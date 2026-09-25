# CLAUDE.md

All project guidance for AI agents lives in AGENTS.md (tool-agnostic). Claude Code: follow it exactly.

@AGENTS.md

## Claude Code specifics

- Slash commands `/review-spec`, `/generate-tests`, `/review-code` are symlinked from `commands/` into `.claude/commands/`.
- The documentation skill is symlinked into `.claude/skills/documentation`.
- SpecStory does not capture Claude Code sessions: write the `.specstory/history/` file and the
  `docs/prompt-history.md` entry manually at the start of every turn.
