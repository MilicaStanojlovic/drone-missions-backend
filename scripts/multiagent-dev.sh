#!/usr/bin/env bash
# Headless multiagent-dev pipeline: plan -> implement (one task at a time) -> review -> test.
# Drives the same .claude/agents/{planner,implementor,code-reviewer,tester}.md personas as the
# interactive `multiagent-dev` Workflow, but stage-by-stage via `claude -p`, so it runs
# unattended in a single terminal session. Never commits or pushes -- review the diff yourself
# once it finishes.
#
# Usage: scripts/multiagent-dev.sh "<feature or task description>"

set -euo pipefail

FEATURE="${1:?Usage: scripts/multiagent-dev.sh \"<feature description>\"}"
REVIEWER_AGENT="code-reviewer"
TASKS_FILE=".claude/tasks/TASKS.md"
LOG_DIR=".claude/tasks/logs"
STAMP="$(date +%Y%m%d-%H%M%S)"

COMMON_ARGS=(
  --permission-mode bypassPermissions
  --disallowedTools "Bash(git push*)" "Bash(git commit*)" "Bash(gh pr create*)"
)

mkdir -p "$LOG_DIR"

if [ -n "$(git status --porcelain)" ]; then
  echo "Warning: working tree isn't clean before this run -- the diff afterward may include changes from before this script ran." >&2
fi

echo "== Plan =="
claude -p "Break down this feature into an ordered task checklist and write it to $TASKS_FILE: $FEATURE" \
  --agent planner "${COMMON_ARGS[@]}" | tee "$LOG_DIR/$STAMP-plan.log"

if [ ! -f "$TASKS_FILE" ]; then
  echo "Planner did not produce $TASKS_FILE -- stopping." >&2
  exit 1
fi

TOTAL="$(grep -c '^- \[ \]' "$TASKS_FILE" 2>/dev/null || echo 0)"
echo "Planned $TOTAL task(s)."

echo "== Implement =="
for i in $(seq 1 "$TOTAL"); do
  REMAINING="$(grep -c '^- \[ \]' "$TASKS_FILE" 2>/dev/null || echo 0)"
  if [ "$REMAINING" -eq 0 ]; then
    break
  fi
  echo "-- Task $i/$TOTAL ($REMAINING unchecked remaining) --"
  claude -p "Implement the first unchecked task in $TASKS_FILE and check it off when done." \
    --agent implementor "${COMMON_ARGS[@]}" | tee "$LOG_DIR/$STAMP-implement-$i.log"
  NEW_REMAINING="$(grep -c '^- \[ \]' "$TASKS_FILE" 2>/dev/null || echo 0)"
  if [ "$NEW_REMAINING" -ge "$REMAINING" ]; then
    echo "Task $i did not get checked off -- stopping so you can inspect $TASKS_FILE and $LOG_DIR/$STAMP-implement-$i.log." >&2
    exit 1
  fi
done

echo "== Review =="
claude -p "Review the working diff (git diff / git status) against this repo's conventions in CLAUDE.md." \
  --agent "$REVIEWER_AGENT" --permission-mode bypassPermissions | tee "$LOG_DIR/$STAMP-review.log"

echo "== Test =="
claude -p "Run Checkstyle and the targeted tests for the classes touched by this run, and report pass/fail." \
  --agent tester "${COMMON_ARGS[@]}" | tee "$LOG_DIR/$STAMP-test.log"

echo
echo "Done. Nothing was committed or pushed -- review the diff yourself:"
echo "  git status"
echo "  git diff"
