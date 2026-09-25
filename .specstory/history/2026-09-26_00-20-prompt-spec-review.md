# 2026-09-26 00:20 — Skeptical specification review

**Phase:** Review (of specification phases 1–8)

## Prompt

Act as a skeptical senior engineer performing a specification review.

Review every file under spec/.

Do not implement anything.

Look specifically for:

- Missing requirements
- Contradictions
- Ambiguous behavior
- Incorrect assumptions
- API inconsistencies
- Database problems
- State-machine gaps
- Validation gaps
- Testing gaps
- Security issues
- Frontend/backend contract mismatches
- PostgreSQL/H2 incompatibilities
- Requirements that cannot be tested objectively

Do not simply say the specification looks good.

Identify concrete problems.

For every issue provide:

Issue
Why it matters
Recommended correction
Affected specification file

Do not modify the specifications yet.
