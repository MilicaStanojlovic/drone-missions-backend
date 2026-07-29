\---

description: Find a ClickUp task, set it to in progress, and branch off develop

argument-hint: \[task name or description]

allowed-tools: Bash(git \*), mcp\_\_clickup\_\_\*

\---



Start work on a ClickUp task.



1\. If $ARGUMENTS is empty, ask me for the task name or a short

&#x20;  description of what I want to work on, and wait for my answer.

&#x20;  If it is not empty, use it as the search term.



2\. Search ClickUp for open tasks matching that term. Show the matches

&#x20;  as a numbered list with task name, status, and list/space, and ask

&#x20;  me to pick one. If exactly one task matches, still show it and ask

&#x20;  me to confirm before doing anything.



3\. Once I confirm, set that task's status to "in progress" in ClickUp.

&#x20;  Use the workspace's actual status name. Read the available statuses

&#x20;  rather than assuming the exact wording.



4\. Make sure the working tree is clean. If it is not, stop and tell me

&#x20;  what is uncommitted instead of stashing or discarding anything.



5\. Check out develop, pull the latest, and create a branch named

&#x20;  feature/SLUG, where SLUG is the task name lowercased, with spaces

&#x20;  and punctuation replaced by hyphens, trimmed to about 50

&#x20;  characters. Show me the branch name before creating it.



6\. Report the task URL, the new status, and the branch you are on.



Never create the ClickUp task, never change anything other than the

status, and never push the new branch.

