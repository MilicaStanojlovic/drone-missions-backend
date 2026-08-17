#!/usr/bin/env bash
# Headless multiagent-dev pipeline: planner -> implementor (one task per call) -> code-reviewer -> tester.
# Same stages and agent definitions (.claude/agents/) as the multiagent-dev workflow, driven via `claude -p`.
#
# Usage: bash scripts/multiagent-dev.sh [--replan] [--dry-run] [--feature <slug>] [--repo <path>] [--impl-model <provider/model>] [--max-iterations N] [--add-dir <path>] <feature brief...>
#   --repo is the target repo where the work happens and where plans/ lives (default: this repo).
#   A target repo other than this one is automatically granted to the claude calls via --add-dir.
#   --feature defaults to the target repo's current branch minus its feature/ prefix.
#   --impl-model runs the Implement stage via opencode with that model (e.g. opencode-go/kimi-k2.7-code)
#   instead of the claude implementor agent; the other stages stay on claude.
#   --dry-run prints the resolved repo/slug/plan-file and the resume decision, then exits without invoking anything.
#   State lives in <repo>/plans/PLAN-<slug>.md; a re-run with unchecked tasks resumes without re-planning.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
SCRIPT_REPO=$(git rev-parse --show-toplevel)

MAX_ITER=15
REPLAN=0
DRY_RUN=0
FEATURE=""
REPO="."
IMPL_MODEL=""
BRIEF=""
EXTRA_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --replan) REPLAN=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --feature) FEATURE="${2:?--feature needs a value}"; shift 2 ;;
    --repo) REPO="${2:?--repo needs a value}"; shift 2 ;;
    --impl-model) IMPL_MODEL="${2:?--impl-model needs a value}"; shift 2 ;;
    --max-iterations) MAX_ITER="${2:?--max-iterations needs a value}"; shift 2 ;;
    --add-dir) EXTRA_ARGS+=(--add-dir "${2:?--add-dir needs a value}"); shift 2 ;;
    *) BRIEF="${BRIEF:+$BRIEF }$1"; shift ;;
  esac
done

normalize() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'; }

REPO_ROOT=$(git -C "$REPO" rev-parse --show-toplevel) || { echo "ERROR: --repo is not a git repo: $REPO" >&2; exit 1; }
if [ "$REPO_ROOT" != "$(git rev-parse --show-toplevel)" ]; then
  EXTRA_ARGS+=(--add-dir "$REPO_ROOT")
fi

if [ -z "$FEATURE" ]; then
  branch=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)
  FEATURE="${branch#feature/}"
fi
SLUG=$(normalize "$FEATURE")
[ -n "$SLUG" ] || { echo "ERROR: could not derive a feature slug (use --feature <slug>)." >&2; exit 1; }
PLAN_FILE="$REPO_ROOT/plans/PLAN-${SLUG}.md"

ALLOWED_TOOLS='Read,Grep,Glob,Write,Edit,Task,Bash(git *),Bash(mvnw.cmd *),Bash(./mvnw *),Bash(curl *),Bash(npm *),Bash(npx *),Bash(ng *),mcp__claude-in-chrome__*'

run_stage() { # $1 = stage name, $2 = prompt; fresh session per call, state flows through $PLAN_FILE + git tree
  echo "=== $1 ==="
  claude -p "$2" --permission-mode acceptEdits --allowedTools "$ALLOWED_TOOLS" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
}

run_impl() { # $1 = stage name; one implementor invocation on whichever engine is configured
  if [ -n "$IMPL_MODEL" ]; then
    echo "=== $1 [opencode: $IMPL_MODEL] ==="
    opencode run --dir "$REPO_ROOT" -m "$IMPL_MODEL" --auto \
      "You are the implementor agent of a multi-stage pipeline. First read $SCRIPT_REPO/.claude/agents/implementor.md and follow its procedure and rules exactly (treat it as your role instructions). Then implement the first unchecked task in $PLAN_FILE and check it off in that file. Implement exactly one task, then stop."
  else
    run_stage "$1" "Use the implementor agent to implement the first unchecked task in $PLAN_FILE and check it off in that file."
  fi
}

unchecked() { # prints 0 when the file is missing; `|| true`: grep -c exits 1 on zero matches
  [ -f "$PLAN_FILE" ] || { echo 0; return; }
  grep -c '^- \[ \]' "$PLAN_FILE" 2>/dev/null || true
}

if [ "$DRY_RUN" -eq 1 ]; then
  echo "repo:        $REPO_ROOT"
  echo "slug:        $SLUG"
  echo "plan file:   $PLAN_FILE"
  echo "implementor: ${IMPL_MODEL:-claude (implementor agent, opus)}"
  if [ "$REPLAN" -eq 1 ]; then
    echo "would:       re-plan from scratch (--replan)"
  elif [ -f "$PLAN_FILE" ] && [ "$(unchecked)" -gt 0 ]; then
    echo "would:       resume ($(unchecked) task(s) left)"
  elif [ -f "$PLAN_FILE" ]; then
    echo "would:       refuse — plan fully checked off (needs --replan)"
  else
    echo "would:       plan fresh"
  fi
  exit 0
fi

# Stage 1: Plan (resume-aware)
if [ -f "$PLAN_FILE" ] && [ "$(unchecked)" -gt 0 ] && [ "$REPLAN" -eq 0 ]; then
  echo "Resuming existing plan ($(unchecked) task(s) left): $PLAN_FILE"
elif [ -f "$PLAN_FILE" ] && [ "$REPLAN" -eq 0 ]; then
  echo "ERROR: $PLAN_FILE exists fully checked off (finished feature record). Re-run with --replan to overwrite." >&2
  exit 1
else
  [ -n "$BRIEF" ] || { echo "ERROR: no brief given and no resumable plan file at $PLAN_FILE." >&2; exit 1; }
  mkdir -p "$REPO_ROOT/plans"
  run_stage "Plan" "Use the planner agent to break this feature into an ordered task checklist and write it to $PLAN_FILE, overwriting any existing file there: $BRIEF"
  [ -f "$PLAN_FILE" ] || { echo "ERROR: planner did not create $PLAN_FILE." >&2; exit 1; }
fi

# Stage 2: Implement — one invocation per task, capped, with a no-progress guard
i=0
while [ "$(unchecked)" -gt 0 ]; do
  i=$((i + 1))
  [ "$i" -le "$MAX_ITER" ] || { echo "ERROR: iteration cap ($MAX_ITER) hit with $(unchecked) task(s) left." >&2; exit 1; }
  before=$(unchecked)
  run_impl "Implement $i ($before task(s) left)"
  [ "$(unchecked)" -lt "$before" ] || { echo "ERROR: implementor made no progress on $PLAN_FILE." >&2; exit 1; }
done

# Stage 3: Review
run_stage "Review" "Use the code-reviewer agent to review the working diff (git diff / git status) against the conventions in CLAUDE.md. The plan for this feature is $PLAN_FILE."

# Stage 4: Test
run_stage "Test" "Use the tester agent to run the appropriate checks for the touched classes/files, then attempt best-effort browser verification — skip it gracefully if the app is not running or the Chrome extension is not connected. The plan for this feature is $PLAN_FILE."

echo "=== Done — feature record: $PLAN_FILE ==="
