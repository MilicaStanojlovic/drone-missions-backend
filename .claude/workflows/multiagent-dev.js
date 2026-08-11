export const meta = {
  name: 'multiagent-dev',
  description: 'Plan a feature into a task checklist, implement it task-by-task, review, and test',
  whenToUse: 'Run against a feature/task description (pass it as args) for the drone-missions backend. Builds .claude/tasks/TASKS.md, then implements each task in order with a dedicated subagent per stage: planner (fable) -> implementor (opus, one task per call) -> code-reviewer -> tester.',
  phases: [
    { title: 'Plan' },
    { title: 'Implement' },
    { title: 'Review' },
    { title: 'Test' },
  ],
}

const TASKS_SCHEMA = {
  type: 'object',
  properties: {
    tasks: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
        },
        required: ['title'],
      },
    },
  },
  required: ['tasks'],
}

phase('Plan')
const plan = await agent(
  `Break down this feature into an ordered task checklist and write it to .claude/tasks/TASKS.md, then return the same list as structured output: ${args}`,
  { agentType: 'planner', schema: TASKS_SCHEMA }
)
log(`Planned ${plan.tasks.length} task(s).`)

phase('Implement')
for (const task of plan.tasks) {
  await agent(
    `Implement the next unchecked task in .claude/tasks/TASKS.md (expected to be: "${task.title}"). Check it off in the file when done.`,
    { agentType: 'implementor', phase: 'Implement', label: task.title }
  )
}

phase('Review')
const review = await agent(
  'Review the working diff produced by this workflow run (git diff / git status) against the repo conventions in CLAUDE.md.',
  { agentType: 'code-reviewer', phase: 'Review' }
)

phase('Test')
const test = await agent(
  'Run Checkstyle and the targeted tests for the classes touched by this workflow run, and report pass/fail.',
  { agentType: 'tester', phase: 'Test' }
)

return { plan, review, test }
