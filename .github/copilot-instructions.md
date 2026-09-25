# GitHub Copilot instructions

Thin adapter: the project guidance lives in [`AGENTS.md`](../AGENTS.md) and [`rules/`](../rules/). Read `AGENTS.md`
first and follow it exactly. Edit guidance only in `rules/`, `skills/` and `commands/`, never here.

Summary of the non-negotiables (details in `AGENTS.md` and [`rules/ai-assisted-development.md`](../rules/ai-assisted-development.md)):

- Work in Specification-Driven Development phase order; do only the phase requested. The specs are in `spec/`
  (`requirements.md`, `api-contract.md`, `openapi.yaml`, `state-machine.md`, `data-model.md`, `architecture.md`).
- Never invent behaviour the spec doesn't define: add it to the document's open questions and ask.
- Log every prompt: a full-text file in `.specstory/history/YYYY-MM-DD_HH-MM-<slug>.md` and an entry in
  `docs/prompt-history.md` that names the tool (GitHub Copilot). Redact secrets.
- Verify generated code before calling it done (checklist in `rules/ai-assisted-development.md` §3): backend
  `./gradlew build`, frontend `npm test && npm run lint && npm run typecheck`, E2E `npm run e2e`. Report what you ran.
- No secrets in the repository ([`rules/security.md`](../rules/security.md)).

Stack rules: [`rules/java-springboot.md`](../rules/java-springboot.md), [`rules/api-standards.md`](../rules/api-standards.md),
[`rules/testing.md`](../rules/testing.md). Documentation templates: [`skills/documentation/`](../skills/documentation/).
