---
description: Find a ClickUp task, set it to in progress, and branch off develop
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
   feature/SLUG, where SLUG is the task name lowercased, with spaces
   and punctuation replaced by hyphens, trimmed to about 50
   characters. Show me the branch name before creating it.

6. Record which ClickUp task this branch belongs to, so the PR hook
   can find it later:
   git config branch.feature/SLUG.clickup-task <task id>

7. Report the task URL, the new status, and the branch you are on.

Never create the ClickUp task, never change anything other than the
status, and never push the new branch.
