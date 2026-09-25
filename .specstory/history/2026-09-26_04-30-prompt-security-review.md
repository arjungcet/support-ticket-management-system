# 2026-09-26 04:30 — Security review

**Phase:** Review (security)

## Prompt

Perform a security review of the complete repository.

Check for:

- Hard-coded passwords
- API keys
- Tokens
- Database credentials
- Secrets in configuration
- Secrets in frontend code
- Sensitive logging
- Unsafe exception responses
- SQL injection risks
- Unsafe search/query construction
- Missing input validation
- CORS issues
- Dependency risks
- Insecure actuator exposure
- Production configuration problems

Check Git-tracked files as well.

Do not modify anything yet.

Report each finding with:
Severity
File
Finding
Risk
Recommended remediation
