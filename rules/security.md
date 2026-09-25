# Security & Secret Handling

Applies to: all code, config, docs, prompts and AI tool usage in this repository.
Status: **binding**.

---

## 1. Secrets

- **No secrets in the repository** — ever. This includes DB passwords, API keys, tokens, private keys, and
  real connection strings. Also not in tests, docs, prompt history, or `.specstory/` transcripts.
- Configuration reads secrets from **environment variables** (e.g. `SPRING_DATASOURCE_PASSWORD`) or a secret manager.
  `application.yml` contains only placeholders: `${DB_PASSWORD}` with **no default** for production profiles.
- Local development: `.env` (git-ignored) + committed `.env.example` with dummy values and comments.
- Testcontainers generate throwaway credentials; tests must not require real ones.
- If a secret is committed by mistake: treat it as leaked — rotate it first, then remove it from history.
- Before saving prompts to `docs/prompt-history.md` / `.specstory/history/`, **redact** any secret or personal data.

## 2. Input handling

- Validate all input server-side (`rules/java-springboot.md` §3). Allow-list, don't deny-list.
- SQL: bound parameters only; no string-concatenated queries. Escape `%`/`_` in `LIKE` search terms.
- No mass assignment: request DTOs are explicit; never bind directly to entities.
- Limit request body size and list page sizes.
- Output: React escapes by default — **never** use `dangerouslySetInnerHTML` with user content (ticket
  descriptions/comments). If rich text is ever required, sanitise with a vetted library and document it.

## 3. Errors & logging

- Error responses never contain stack traces, SQL, internal class names, or config values.
- Logs never contain secrets, tokens, passwords, or full request bodies. Mask PII where practical.
- Correlation id on every request for traceability.

## 4. HTTP

- CORS: explicit allow-list of frontend origins per environment; never `*` with credentials.
- Security headers via Spring Security defaults when auth is added (out of current scope — see assumptions in the spec).
- Actuator: only `health` and `info` exposed publicly; everything else disabled or protected.

## 5. Dependencies

- Use maintained, widely-adopted libraries; versions via Spring Boot BOM / lockfile.
- Run dependency vulnerability checks (e.g. OWASP Dependency-Check / `npm audit`) in CI; high/critical findings
  block merge unless triaged.
- AI-suggested dependencies must be verified to exist on Maven Central / npm under the exact coordinates
  (guard against hallucinated or typo-squatted packages).
