# ADR-0002: No authentication in v1; run only on local or trusted internal networks

| Status | Date | Supersedes | Superseded by |
|--------|------|------------|---------------|
| Accepted | 2026-09-26 | — | — |

## Context

The requirements (REQ-1…12) name no users, roles or login, and v1 was built without authentication (assumption A-4,
`spec/requirements.md` §15). The security review (H-2) found that this leaves every operation anonymous: anyone who
can reach the host can read every ticket, edit or close it, and post comments under any name (free-text author,
A-14). No decision limited where the system may run (spec review SR-12, decision D-1 in `spec/requirements.md` §17).

The product owner signed off the as-built behaviour on 2026-09-26 and chose this deployment boundary.

## Options considered

1. **Local or trusted internal network only, no login in v1**
   - Pros: matches the requirements and the implemented, fully tested system; no new scope.
   - Cons: relies on the network for protection; no audit of who did what; comment authors can be impersonated.
2. **Behind a company SSO / authenticating reverse proxy, no login in the app**
   - Pros: real identities at the edge with no application change.
   - Cons: needs infrastructure that doesn't exist for this project; the app still can't use the identity (authors
     stay free text).
3. **Build authentication and users into v1**
   - Pros: real identities for assignees and comment authors (open question Q-9).
   - Cons: a new requirement touching spec, backend, frontend and every test; far beyond the assessment scope.

## Decision

v1 has **no authentication** and may run **only on a developer machine or a trusted internal network**, never
exposed to the public internet. Authentication becomes a future requirement, revisited together with Q-9 (real users
for assignee and comment author).

## Consequences

- Anyone with network access to the frontend or backend has full access. Operators must restrict access (host
  firewall, private network, or binding: `SERVER_ADDRESS=127.0.0.1` for the backend, `next start -H 127.0.0.1` for
  the frontend) when the machine is on a shared network.
- The local database container already binds to `127.0.0.1` only (`docker-compose.yml`).
- The frontend sends a nonce-based Content-Security-Policy and anti-framing headers (security review M-2), so
  clickjacking and injected scripts are limited even without login. `upgrade-insecure-requests` is deliberately not
  set because internal deployments may use plain HTTP; HSTS takes effect only when served over TLS.
- Any deployment beyond this boundary (internet-facing, multi-team, customer data at scale) requires a new ADR that
  supersedes this one and adds authentication first.
