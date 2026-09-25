# 2026-09-26 05:40 — Fix confirmed defects from acceptance review

**Phase:** Fix

## Prompt

Review the findings from the final acceptance review.

Fix ONLY confirmed defects.

For each fix:

1. Explain the defect.
2. Identify the specification/acceptance criterion affected.
3. Make the smallest appropriate change.
4. Add or update a regression test.
5. Run the relevant tests.
6. Confirm the original defect is resolved.

Do not weaken tests or modify acceptance criteria to make failures disappear.

After all fixes, run the complete backend and frontend test suites.
