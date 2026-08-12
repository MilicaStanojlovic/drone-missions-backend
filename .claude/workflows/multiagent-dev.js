export const meta = {
  name: 'multiagent-dev',
  description: 'Plan a feature into a task checklist, implement it task-by-task, review, and test',
  whenToUse: 'Run for the drone-missions backend with args as either a plain feature/task description string or {feature, brief, repo} (feature = kebab-case slug, e.g. the branch slug; repo = absolute path of the repo the work happens in — omit for this repo). Builds plans/PLAN-<feature>.md in that repo, then implements each task in order with a dedicated subagent per stage: planner (fable) -> implementor (opus, one task per call) -> code-reviewer -> tester.',
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

let parsedArgs = args
if (typeof parsedArgs === 'string') {
  try { parsedArgs = JSON.parse(parsedArgs) } catch { /* plain-string brief */ }
}
const isObj = parsedArgs !== null && typeof parsedArgs === 'object' && !Array.isArray(parsedArgs)
const brief = isObj ? String(parsedArgs.brief ?? '') : String(args ?? '')
if (!brief.trim()) throw new Error('multiagent-dev needs a non-empty brief (string args, or {feature, brief})')
const normalize = (s) => String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
const slug = (isObj && parsedArgs.feature)
  ? normalize(parsedArgs.feature)
  : normalize(brief).split('-').slice(0, 5).join('-')
if (!slug) throw new Error('could not derive a feature slug from args')
const repoRoot = isObj && parsedArgs.repo ? String(parsedArgs.repo).replace(/[\\/]+$/, '') : ''
const planFile = repoRoot ? `${repoRoot}/plans/PLAN-${slug}.md` : `plans/PLAN-${slug}.md`
log(`Plan file: ${planFile}`)

phase('Plan')
const plan = await agent(
  `Break down this feature into an ordered task checklist and write it to ${planFile} (overwrite the file if it already exists), then return the same list as structured output: ${brief}`,
  { agentType: 'planner', schema: TASKS_SCHEMA }
)
log(`Planned ${plan.tasks.length} task(s).`)

phase('Implement')
for (const task of plan.tasks) {
  await agent(
    `Implement the next unchecked task in ${planFile} (expected to be: "${task.title}"). Check it off in that file when done.`,
    { agentType: 'implementor', phase: 'Implement', label: task.title }
  )
}

phase('Review')
const review = await agent(
  `Review the working diff produced by this workflow run (git diff / git status) against the repo conventions in CLAUDE.md. The plan for this feature is ${planFile}.`,
  { agentType: 'code-reviewer', phase: 'Review' }
)

phase('Test')
const test = await agent(
  'Run Checkstyle and the targeted tests for the classes touched by this workflow run, then attempt best-effort browser verification of Swagger UI at http://localhost:8085/swagger-ui.html — skip it gracefully if the app is not running or the Chrome extension is not connected. Report pass/fail.',
  { agentType: 'tester', phase: 'Test' }
)

return { planFile, plan, review, test }
