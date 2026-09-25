# Security Review — 2026-09-26

| Scope | Method | Changes made |
|-------|--------|--------------|
| All 141 Git-tracked files, the full Git history (1 commit), untracked working-tree files, and the running backend jar and frontend build | Pattern scans (`git grep`, `git log -p`), `npm audit`, an OSV.dev query over all 38 backend runtime artifacts, the Gradle wrapper checksum against gradle.org, live HTTP probes of the backend (`:18181`) and frontend (`:13131`) | None |

**Overall:** no secrets are exposed. The main risks are a **vulnerable Tomcat version**, **no authentication by
design**, and **missing hardening and production configuration**. Most application-level checks (validation, SQL,
error handling) can't be exercised yet because the backend has no endpoints (code review B-1). They are listed as
design-level findings where the spec itself is weak.

Severity: **High**, **Medium**, **Low**, **Info** (checked, no issue).

---

## High

### H-1 — Embedded Tomcat 11.0.24 has three critical advisories
- **File:** `backend/build.gradle.kts` / `backend/gradle/libs.versions.toml` (Tomcat comes in transitively through Spring Boot 4.1.1's dependency management)
- **Finding:** OSV reports `org.apache.tomcat.embed:tomcat-embed-core:11.0.24` as affected by:
  - `GHSA-9xv2-5v5q-p794` / CVE-2026-65905: DIGEST authenticator, auth bypass by capture-replay
  - `GHSA-gcx9-497g-6cp6` / CVE-2026-65182: improper access control / incorrect authorisation
  - `GHSA-h3x4-894j-xpx5` / CVE-2026-68525: FORM authentication, incorrect authorisation

  All are rated CRITICAL and fixed in **11.0.25**. The latest is 11.0.26. Spring Boot 4.1.1 is the newest 4.1.x
  release, so upgrading Boot doesn't fix this yet.
- **Risk:** the DIGEST and FORM advisories target Tomcat container authentication, which this app doesn't configure,
  so exploitability today is probably low. The access-control advisory (gcx9) hasn't been analysed against our usage.
  Shipping a server with known critical CVEs fails most compliance gates regardless.
- **Remediation:** pin `tomcat.version` to `11.0.26` through the dependency-management property (e.g.
  `extra["tomcat.version"]`), recorded in the version catalog with a comment linking the advisories. Remove the
  override when a Spring Boot patch release includes it. Verify with `./gradlew dependencies` and an OSV re-scan.

### H-2 — No authentication or authorisation, and no stated deployment boundary
- **File:** `spec/architecture.md` §2, §20 (A-4). `backend/` (no Spring Security). `frontend/` (no login).
- **Finding:**
  - By design, every API operation will be anonymous: create, edit, assign, comment, and irreversible `CLOSED`/`CANCELLED` transitions.
  - Comment `author` is free text (A-14), so anyone can post as anyone.
  - Ticket ids are sequential and enumerable.
  - No ADR accepts this risk or restricts where the system may run (spec review SR-12 is open).
  - Both the backend and `next start` listen on **all interfaces** (`TCP *:18181`, `TCP *:13131` observed).
- **Risk:** anyone who can reach the host can read every ticket (customer data in descriptions and comments), change
  or permanently close tickets, and forge comment authors.
- **Remediation:**
  - Write the deployment-boundary ADR (plan STEP-01): internal network only, or behind an SSO reverse proxy.
  - Bind to `127.0.0.1` outside production (`server.address`, `next start -H`).
  - Label the author field as unverified.
  - Keep the Spring Security extension point (`architecture.md` §19) for v2.

---

## Medium

### M-1 — No automated dependency or secret scanning
- **File:** `backend/build.gradle.kts` (no OWASP Dependency-Check / OSV plugin), no `.github/workflows/*`, no pre-commit hooks
- **Finding:** `rules/security.md` §5 requires dependency checks in CI, and plan STEP-06 requires a gitleaks scan. H-1 was found only by this manual review.
- **Risk:** new CVEs in Tomcat, Spring, Jackson, Next.js or transitive npm packages go unnoticed. A pasted secret (e.g. in `.specstory/` prompt logs, see L-5) could be committed undetected.
- **Remediation:** add an OSV-Scanner or OWASP Dependency-Check job for Gradle, `npm audit --audit-level=high` for `frontend/` and `e2e/`, and gitleaks as both a pre-commit hook and a CI job covering `.specstory/` and `docs/`. Fail the build on high/critical findings unless triaged.

### M-2 — Frontend sends no security headers and advertises its framework
- **File:** `frontend/next.config.ts`
- **Finding:** a live `HEAD /tickets` returned `X-Powered-By: Next.js` and **none** of `Content-Security-Policy`, `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options`, `Referrer-Policy`, `Strict-Transport-Security`.
- **Risk:**
  - **Clickjacking:** the UI can be framed by another site, and one click performs irreversible actions ("Close", "Cancel ticket").
  - No CSP to limit an injection if one is ever introduced.
  - Framework fingerprinting.
- **Remediation:** `poweredByHeader: false`, and an `async headers()` block with a baseline policy: `frame-ancestors 'none'`, a CSP restricted to `'self'`, `nosniff`, `strict-origin-when-cross-origin`, and HSTS behind TLS. Add an E2E check of the headers.

### M-3 — Production configuration is missing; the frontend silently falls back to localhost
- **Files:** `backend/src/main/resources/application.yml` (only `spring.application.name`), `frontend/next.config.ts` (`BACKEND_URL ?? "http://localhost:8080"`, evaluated at build time)
- **Finding:**
  - There is no `prod` profile, no datasource placeholders with required secrets, no explicit `server.error.*` settings, and no actuator exposure configuration.
  - The frontend bakes the backend URL at build time and defaults to `localhost:8080` when it's unset (code review M-3).
- **Risk:**
  - A production frontend built without `BACKEND_URL` proxies to whatever listens on localhost:8080 on that host, with no error.
  - When persistence and actuator are added, the defaults will be implicit rather than reviewed. Adding the actuator starter later would expose `/actuator/health` details only if left unconfigured, but that behaviour is not pinned.
- **Remediation:** implement plan STEP-08:
  - `prod` profile, `${SPRING_DATASOURCE_PASSWORD}` with no default, fail-fast validation
  - `server.error.include-stacktrace=never`, `include-message=never` stated explicitly
  - `management.endpoints.web.exposure.include=health,info`

  On the frontend, use a runtime proxy that fails if `BACKEND_URL` is unset.

### M-4 — Specified validation has gaps that will cause 500s or unbounded requests
- **Files:** `spec/api-contract.md` §2.2–§2.3, `spec/data-model.md` §7 (no backend code yet)
- **Finding (design level):**
  - The contract doesn't reject NUL or control characters. PostgreSQL rejects `\u0000` in `varchar`, so a single JSON string can produce a `500` (spec review SR-06).
  - No maximum request body size is specified (SR-12/SR-23), although `rules/security.md` §2 requires one.
- **Risk:** trivially triggered server errors (noise in logs and alerts, possible DoS amplification). Large bodies are parsed fully before validation.
- **Remediation:** plan STEP-02/03/34:
  - reject C0 control characters (except `\t\n\r` in multi-line fields) with `400 INVALID_VALUE`
  - set a body limit (e.g. 64 KB → `413 PAYLOAD_TOO_LARGE`)
  - add tests for both

---

## Low

| ID | File | Finding | Risk | Remediation |
|----|------|---------|------|-------------|
| L-1 | `e2e/stub/contract-stub.mjs:355`, `e2e/scripts/servers.ts` | The contract stub and the servers the harness starts listen on all interfaces. The stub has no auth and writes all data to `e2e/.state/stub-data.json` | While tests run, anyone on the same network can read or write test data on the developer's machine | `listen(PORT, "127.0.0.1")`. Start `next start -H 127.0.0.1` and the backend with `--server.address=127.0.0.1` in the harness |
| L-2 | Backend (Spring Boot default error handling) | Error bodies echo the request path, e.g. `"path":"/api/v1/tickets/%27%20OR%201=1--"` | Reflected input. Low, because the content type is `application/json`, not HTML | Replace with the contract's Problem Details handler (STEP-31). Keep `instance` as the matched path |
| L-3 | `e2e/scripts/servers.ts` (`kill(readPids().backend)`) | Stops processes by PIDs stored in a file from an earlier run | If a PID was reused by an unrelated process, the harness sends it SIGTERM | Verify the process command line (or use process groups the harness owns) before killing |
| L-4 | Repository root, `backend/` | `.env.example` exists only in `frontend/`. `rules/security.md` §1 requires a committed example documenting every variable (datasource, CORS origins) | Developers invent variable names or commit real `.env` values by copying a colleague's file | Add root/backend `.env.example` in STEP-06/STEP-08 |
| L-5 | `.specstory/history/*`, `docs/prompt-history.md` (tracked) | Full prompt text is committed by design. The current content contains no secrets or personal data (checked), but redaction is manual | A future prompt containing a token or customer data would be committed and pushed | Cover these paths with the gitleaks hook (M-1). Keep the redaction rule in `rules/security.md` §1 |

---

## Info — checked, no issue

| Check | Result |
|-------|--------|
| Hard-coded passwords, API keys, tokens, private keys, JWTs, DB credentials in tracked files | **None.** A pattern scan of all 141 tracked files found only fixture prose ("…after password reset") |
| Secrets in Git history | **None** (`git log -p --all`, 1 commit) |
| `.env` handling | `.env`, `.env.*`, `*.pem`, `*.key` are ignored (verified with `git check-ignore` for root, `frontend/`, `backend/`). Only `frontend/.env.example` is tracked, with a non-secret URL |
| Secrets in frontend code | None. No `NEXT_PUBLIC_*` variables. `BACKEND_URL` is server-side only |
| Personal data in tracked files | No email addresses or names of real people found |
| Sensitive logging | No application logging of request bodies or ticket text (no backend code yet, no `console.*` in `frontend/src`). The stub logs only 500 errors |
| Unsafe exception responses (current backend) | Spring's default error JSON contains no stack trace or exception message (verified live for a `404` and a malformed-JSON POST) |
| SQL injection / query construction | No SQL exists. Frontend list queries are built with `URLSearchParams` (encoded, covered by `api.test.ts`). The spec requires bound parameters and LIKE-escaping (`data-model.md` §13) |
| XSS | No `dangerouslySetInnerHTML`, and ESLint `react/no-danger: error` is set. `TicketDetailsView.test.tsx` checks that HTML in ticket text renders inert |
| CORS | No CORS configuration. A preflight from `Origin: https://evil.example` got **no** `Access-Control-Allow-Origin`, so browsers block cross-origin calls. The frontend uses same-origin proxying |
| Actuator exposure | The actuator isn't on the classpath. `/actuator` and `/actuator/env` return `404` |
| npm dependencies | `npm audit`: 0 vulnerabilities in `frontend/` and `e2e/`. All versions pinned exactly, lockfiles committed |
| Other backend runtime dependencies | 37 of 38 artifacts clean in OSV (Spring 7.0.9, Jackson 3.1.5, Logback 1.5.38, SnakeYAML 2.6, …). Only Tomcat is affected (H-1) |
| Build supply chain | The committed `gradle-wrapper.jar` SHA-256 `238e777f…21abd5` **matches** the official Gradle 9.8.0 wrapper checksum. `distributionSha256Sum` is pinned and `validateDistributionUrl=true` |

## Recommended order

1. H-1: pin Tomcat 11.0.26 (one-line change, then re-scan).
2. H-2: deployment-boundary ADR, and bind to localhost outside production.
3. M-1: dependency and secret scanning in CI and pre-commit.
4. M-2, M-3: security headers, production profile, runtime backend URL.
5. M-4 and the Low items as part of plan STEP-02/03/08/31/34.
