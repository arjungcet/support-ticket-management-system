# AI-Assisted Development Workflow

Applies to: every AI agent (Claude Code, Cursor, others) and every human using one in this repository.
Status: **binding**.

---

## 1. Specification-Driven Development — phase order

Work proceeds strictly in this order. A phase starts only when the previous phase's artifact exists and has been
reviewed by the user.

| # | Phase | Artifact |
|---|-------|----------|
| 1 | Requirement | `spec/requirements.md` |
| 2 | Repository / AI instructions | `rules/`, `skills/`, `commands/`, `CLAUDE.md`, `AGENTS.md`, `.cursor/` |
| 3 | Specification | `spec/functional-spec.md` |
| 4 | Architecture | `spec/architecture.md` + ADRs in `docs/adr/` |
| 5 | Data model | `spec/data-model.md` |
| 6 | API contract | `spec/api-contract.md` (+ `spec/openapi.yaml`) |
| 7 | State machine | `spec/state-machine.md` |
| 8 | Test strategy | `spec/test-strategy.md` |
| 9 | Implementation plan | `spec/implementation-plan.md` |
| 10 | Backend | `backend/` |
| 11 | Frontend | `frontend/` |
| 12 | Testing | test suites + reports |
| 13 | Review | `docs/reviews/*.md` (via `commands/review-code.md`) |
| 14 | Fix | commits referencing review findings |

Rules for agents:
- Identify which phase a request belongs to. If earlier artifacts are missing, **say so and stop** (or ask) rather
  than jumping ahead.
- Do only the requested phase. Don't "helpfully" start the next one.
- When a later phase reveals a flaw in an earlier artifact, **update the earlier artifact first**, then continue.
- Code must trace to the spec. If the spec is silent, ask — don't invent behaviour.

## 2. Prompt history

Every user prompt is recorded (redact secrets/PII first — see `rules/security.md` §1):

1. `.specstory/history/YYYY-MM-DD_HH-MM-<slug>.md` — full prompt text + phase.
   If the SpecStory extension is installed in Cursor it writes here automatically; agents without SpecStory
   (e.g. Claude Code) write the file manually.
2. `docs/prompt-history.md` — append a dated entry: title, phase, short summary, link to the full `.specstory` file.

## 3. Verifying AI-generated code

AI output is a **draft from an untrusted contributor**. Before it is accepted:

- [ ] **Compiles and all tests pass** (`./gradlew build`, frontend `lint`/`typecheck`/`test`). Run them — don't assume.
- [ ] **Traceability**: each change maps to a spec item / plan step; nothing unrequested was added.
- [ ] **No hallucinated APIs**: every class, method, annotation, config property and dependency actually exists in the
      versions we use (check the Spring Boot / library docs, not memory).
- [ ] **New tests were seen failing** before the fix/feature made them pass.
- [ ] **Tests assert behaviour**, not just that code ran (no assertion-free tests, no over-mocking, no tests
      tweaked to match buggy output).
- [ ] **Rules compliance**: `rules/java-springboot.md`, `rules/api-standards.md`, `rules/testing.md`, `rules/security.md`.
- [ ] **Security**: no secrets, no string-built SQL, no internals in errors, no `dangerouslySetInnerHTML`.
- [ ] **Diff is minimal and reviewable**: no drive-by refactors, reformatting, or dead code.
- [ ] **Docs updated** when behaviour or contract changed.
- [ ] Agent reports honestly: what was run, what passed, what was skipped, what is uncertain.

## 4. Code review expectations

- Every change is reviewed (human, optionally assisted by `commands/review-code.md`).
- Reviews prioritise: correctness vs spec → security → data integrity/transactions → error handling → tests →
  maintainability → style. Style nits are labelled `nit:` and never block.
- Findings state: location (`file:line`), problem, concrete failure scenario, suggested fix, severity
  (`blocker` / `major` / `minor` / `nit`).
- `blocker`/`major` findings must be resolved or explicitly accepted by the user before merge.
- Reviewers verify claims by reading the code (and running tests where relevant), not by trusting the PR description.

## 5. Change hygiene

- Small, focused commits. Conventional Commit messages (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`).
- One phase / one plan step per PR where practical.
- Never commit generated build output, IDE files, `.env`, or local DB files.
