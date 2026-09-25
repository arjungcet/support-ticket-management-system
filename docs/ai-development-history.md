# AI Development History

| Scope | Sources | Last updated |
|-------|---------|--------------|
| Every AI-assisted step from the first prompt to the current state | [`docs/prompt-history.md`](prompt-history.md) (23 prompts), [`.specstory/history/`](../.specstory/history/) (full text), `docs/reviews/`, [`docs/ai-review.md`](ai-review.md), Git history | 2026-09-26 |

## About the tools

Every prompt recorded here was run in **Claude Code (Anthropic, model Opus 5.5)**, in the Claude desktop app. The
record contains no Cursor or GitHub Copilot usage, so no phase below claims either.

The repository does include Cursor adapters (`.cursor/rules`, `.cursor/commands`), so Cursor *can* use the same
guidance. SpecStory was never configured, so `.specstory/history/` was written by hand as each prompt arrived. If
other tools were used outside this record, add them as new entries rather than editing these.

"Human review" and "Human decision" entries quote or paraphrase what the engineer actually instructed or chose in a
prompt. Where no human decision was recorded, the entry says so. Times are the labels in the prompt log. They're
approximate and in session order.

---

## Phase 1 — Repository Setup
**Tool:** Claude Code

**Prompts:**
- 2026-09-25 22:20, *Project workflow setup*: remember the 14-step SDD order for this project, and save every prompt to `.specstory/history/` and `docs/prompt-history.md`.
- 22:30, *Prompt 0: Initialize the project*: "Do NOT implement the Support Ticket Management System yet." Create reusable AI guidance: `rules/` (Java/Spring Boot, testing, API standards), `skills/documentation/`, `commands/` (review-code, review-spec, generate-tests), and the prompt-history files.
- 22:40, *Use Gradle*.
- 2026-09-26 06:20, *Target repository structure* (a pasted layout).

**Result:**
- `rules/`: five binding rule files. Two more than requested: `security.md` and `ai-assisted-development.md`, which holds the phase order, prompt logging, the AI-code verification checklist and review expectations.
- `skills/documentation/`: `SKILL.md` plus spec and ADR templates.
- `commands/`: three command files.
- Entry points for AI tools: `AGENTS.md` and `CLAUDE.md`.
- Cursor and Claude adapters (symlinks to `commands/` and `skills/`).
- `.gitignore`, and project memory for the workflow and logging rules.
- After the structure prompt: a root `README.md`, frontend tests moved to `frontend/tests/`, and the folder renamed to `support-ticket-management-sdd`, then (per the engineer, 07:20) to `support-ticket-management-service`.

**Human decisions:**
- **Gradle instead of Maven** (22:40). When the pasted layout later showed `pom.xml`, the AI asked, and the engineer chose **"Keep Gradle"**.
- The engineer initialised Git and made the initial commit `0aee402` (author "Arjun", 2026-09-25).
- For the pasted layout, the engineer chose: add the README, create `frontend/tests/`, and rename the root folder. They later settled on the folder name **`support-ticket-management-service`**. The GitHub remote is still `arjungcet/support-ticket-management-system`.

---

## Phase 2 — Requirements and Specification
**Tool:** Claude Code

**Prompts:**
- 22:50: create `spec/architecture.md`, and explain where the state-machine logic belongs.
- 23:05: create `spec/data-model.md`.
- 23:20: "can we use mysql or mongodb".
- 23:35: create `spec/api-contract.md`.
- 23:50: create `spec/state-machine.md`.
- 2026-09-26 00:05: create `spec/test-strategy.md`.
- 00:40: "The specifications are now approved for implementation planning", then create the implementation plan.

**Result:**
- `spec/requirements.md`: REQ-1…12, TC-1…5.
- `architecture.md`: state machine placed in the domain (`TicketStatus` + `Ticket.changeStatus`), with the reasons it must not live only in the controller.
- `data-model.md`: PostgreSQL schema, constraints, indexes, H2 compatibility table.
- `api-contract.md`: 9 endpoints and one Problem Details error format.
- `state-machine.md`: 25-row transition matrix.
- `test-strategy.md`: test pyramid, and explicit A1–A5 / R1–R8 state-machine tests.
- `implementation-plan.md`: 68 steps.
- `docs/adr/0001-use-postgresql.md`.
- Every document lists its assumptions (A-n, DM-n, API-n, SM-n, TS-n) instead of guessing.

**Human decisions:**
- **Stay on PostgreSQL** (23:25), after the AI compared MySQL and MongoDB. Recorded as ADR-0001.
- The engineer approved the specs for implementation planning. They did so even though the functional spec (SDD phase 3) was never written and the spec review had 4 open blockers. The plan therefore starts with a "Phase 0" of spec decisions, which is still open.

---

## Phase 3 — Backend Implementation
**Tool:** Claude Code

**Task:** "Implement ONLY the first backend implementation task" (01:00). That is plan STEP-07, the Gradle and Spring Boot skeleton. **The ticket service is not implemented.** Plan STEP-08…39 (persistence, domain, state machine, services, controllers, validation) haven't been started.

**Result:**
- Spring Boot 4.1.1 / Java 21 skeleton, Gradle 9.8.0 wrapper with a pinned checksum, version catalog.
- Separate `integrationTest` suite with a JaCoCo report over both suites.
- Later, from the defect-fix prompt (05:40): Tomcat pinned to 11.0.26 for three critical CVEs, with a regression test.

**Human review:**
- The AI stopped and reported that STEP-07 depended on unfinished steps (STEP-04 pinned versions, STEP-06 git init). The engineer answered: **"implement whatever asked to implement"**. So versions were chosen without the planned version-pinning decision record. That deviation is recorded in the step report and in `docs/ai-review.md` (AI-3).
- Two AI mistakes in this phase were caught and fixed:
  - AI-1: mixed JUnit 5/6 versions, and integration tests missing the app's dependencies
  - AI-2: redirect HTML written into the wrapper checksum

---

## Phase 4 — Testing
**Tool:** Claude Code

**Prompts:**
- 01:30: "Generate missing automated tests … Do not modify production behavior just to make tests pass … report failures separately from implementation defects."
- 02:50: "Act as an integration-test engineer … For every failure classify it as test defect / implementation defect / specification defect / environment issue. Do not hide failures."

**Result:**
- **241 spec-derived black-box API tests** for the backend (`backend/src/integrationTest/.../ticket/api/*`). They are tagged `spec-acceptance` and run with `./gradlew specAcceptanceTest`, and are written ahead of the implementation. **240 fail**, all with `404` because the endpoints don't exist.
- **21 Playwright E2E tests** covering the 13 user journeys, plus a contract stub for validating the tests themselves. Against the real backend, 19 fail (`404`). Against the stub, 21 of 21 now pass.
- **Frontend tests** (added with the frontend, prompt 02:10): 83 tests with Vitest, Testing Library and MSW, all passing.
- Classification found:
  - three test defects, all fixed (AI-8)
  - one frontend defect (stale conflict message)
  - one configuration defect: the backend URL was fixed at build time (AI-6)
  - three spec gaps
  - Docker unavailable, an environment issue

**Human review:** none recorded beyond these instructions, which set the classification rules and forbade hiding failures.

---

## Phase 5 — Review
**Tool:** Claude Code

| Prompt | Main finding | Human decision |
|--------|--------------|----------------|
| 00:20 skeptical spec review | 32 issues, 4 blockers: functional spec missing, contract contradictions around `null` handling and error aggregation, two sources of truth for the API | Engineer approved the specs for planning anyway (00:40). Blockers carried into plan Phase 0 |
| 03:30 strict code review | Backend implements no requirements (B-1). Frontend lost-update bug (M-1). Search box fighting URL navigation (M-2). Build-time backend URL (M-3) | None at the time: "Do not modify code yet" |
| 04:00 AI-output audit (`docs/ai-review.md`) | 9 real AI mistakes, each with evidence. An executable check proved that Jackson 3 accepts coercion and unknown fields, contrary to the contract (AI-3) | Sign-off table in `ai-review.md` §12 is **still blank**. No human sign-off recorded yet |
| 04:30 security review | No secrets committed. Tomcat 11.0.24 with three critical CVEs. No authentication by design, and no recorded deployment boundary | None at the time |
| 05:00 final acceptance review | **Not accepted:** 1 PASS (no secrets), 1 PARTIAL (UI errors), 13 FAIL, all because the backend isn't implemented | Engineer ordered "Fix ONLY confirmed defects … Do not weaken tests or modify acceptance criteria" (05:40) |

**Fix result (05:40):** five confirmed defects were fixed. Each started with a regression test that failed first and passes after the fix:
- M-1: lost update in the edit form
- M-2: search box vs URL navigation
- I-2: stale conflict message
- M-3: backend URL now read at runtime by a proxy route
- H-1: Tomcat pinned to 11.0.26

The missing backend was deliberately **not** treated as a "fix". It's unimplemented scope that depends on Phase 0 decisions. Details are in [`docs/reviews/2026-09-26-fixes.md`](reviews/2026-09-26-fixes.md).

---

## Additional phase — Frontend Implementation
**Tool:** Claude Code

**Prompt:** 02:10: "Implement the next approved frontend task … Do not invent API fields or endpoints. Use the API contract as the source of truth."

**Result:** A Next.js 16 / React 19 / TypeScript app covering create, list, details, edit, assignee, comments, search, status filter, status transitions and error display. Built with TanStack Query and React Hook Form. The request listed the whole feature set, so this covered plan STEP-46…58, not a single step, and the report said so.

**Human review:** none recorded at the time. The strict code review later found defects M-1 and M-2 here, which were fixed at 05:40.

---

## Current state and open human decisions

| Item | Status |
|------|--------|
| Acceptance | **Not accepted:** the backend ticket API isn't implemented |
| Phase 0 spec decisions (functional spec, contract semantics, `openapi.yaml`, pinned versions) | Open. Needed before backend STEP-08…39 |
| Deployment boundary / no-auth decision record (security H-2) | Open |
| JUnit Jupiter 6 instead of the JUnit 5 in TC-5 | Open, unreviewed deviation |
| Engineer sign-off of the AI-output audit (`ai-review.md` §12) | Open |
| Docker for PostgreSQL / Testcontainers | Not running in this environment |
