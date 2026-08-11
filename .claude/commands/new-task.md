---
description: Find a ClickUp task, set it to in progress, branch off develop, then plan and implement it
argument-hint: [task name or description]
allowed-tools: Bash(git *), mcp__clickup__*
---

Start work on a ClickUp task.

1. If $ARGUMENTS is empty, ask me for the task name or a short
   description of what I want to work on, and wait for my answer.
   If it is not empty, use it as the search term.

2. Search ClickUp for open tasks matching that term. Show the matches
   as a numbered list with task name, status, and list/space, and ask
   me to pick one. If exactly one task matches, still show it and ask
   me to confirm before doing anything.

3. Once I confirm, set that task's status to "in progress" in ClickUp.
   Use the workspace's actual status name. Read the available statuses
   rather than assuming the exact wording.

4. Make sure the working tree is clean. If it is not, stop and tell me
   what is uncommitted instead of stashing or discarding anything.

5. Check out develop, pull the latest, and create a branch named
   feature/SLUG. Derive SLUG from what the task is about — the
   domain concept, taken from the title and description together
   (e.g. an admin-visibility task -> feature/admin-visibility) —
   not from the title word for word. Never include team or layer
   tags: drop things like [Backend]/[Frontend] and the words
   backend/frontend themselves. Keep it lowercase kebab-case,
   2-4 words, at most about 50 characters. Show me the proposed
   name and wait for my approval before creating the branch.

6. Record which ClickUp task this branch belongs to, so the PR hook
   can find it later:
   git config branch.feature/SLUG.clickup-task <task id>

7. Report the task URL, the new status, and the branch you are on.

8. Fetch the task's full details, most importantly its description
   (and any comments that add requirements). If the branch somehow
   has no recorded task id, ask me which task this is and search
   for it.

9. Read the description as the requirements. Before any planning,
   ask me clarifying questions about anything ambiguous, missing, or
   contradictory — scope, edge cases, API shape, data model. If the
   description is empty or too thin to work from, say so and get the
   requirements from me instead of guessing.

10. Once the requirements are clear (task description plus my answers
    from step 9), hand off to the multiagent-dev pipeline instead of
    planning it yourself: call
    Workflow({ name: 'multiagent-dev', args: '<task description +
    clarifications, written out as one self-contained brief>' }).
    That workflow plans the task into .claude/tasks/TASKS.md, implements
    it one task at a time, reviews the diff against CLAUDE.md, and runs
    Checkstyle plus targeted tests — all before you see it.

11. When the workflow finishes, summarize what it returned: the task
    list it planned, the reviewer's findings, and the tester's pass/fail
    verdict. Point out anything the reviewer or tester flagged so I can
    decide whether it needs another pass before committing. Once I'm
    happy with the result and it's committed, remind me that /pr will
    open the pull request and the hook will move this task to review.

Never create the ClickUp task, never change anything other than the
status, and never push the new branch.
