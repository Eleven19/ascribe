# Claude Instructions

**ASCIIDOC SPECIFICATION**: As we are implementing things we should consult [https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-lang](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-lang) for the authoritative spec of ASCIIDOC.
We should also note the TCK is here: [https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck).

For changelog or release-related work, consult these repo-local skills first:

- `.github/skills/changelog-maintenance/SKILL.md`
- `.github/skills/release-process/SKILL.md`

Use the supporting repo docs instead of duplicating process text:

- `docs/contributing/changelog.md`
- `docs/contributing/releasing.md`

## Direct-style Scala, Mill, and Ox

This codebase targets **Scala 3 direct style** with **[Ox](https://github.com/softwaremill/ox)** where concurrency or structured scopes matter. Builds use **[Mill](https://com-lihaoyi.github.io/mill/)** (`./mill`). Keep agent and implementation habits aligned with:

- [VirtusLab: Generating direct-style Scala 3 applications](https://virtuslab.com/blog/scala/generating-direct-style-scala-3-applications) — Metals-first workflow; translate “fast incremental CLI” to **Mill’s daemon-backed `mill` tasks** (not sbt); translate “plain sbt for long-lived runs” to **foreground runs where Ctrl+C is reliable**; braceless syntax; minimize mutable/imperative structure.
- [Ox: Using Ox with AI coding assistants](https://ox.softwaremill.com/latest/info/ai.html) — index `https://ox.softwaremill.com/latest/` in Cursor Docs; optional Context7 (`use context7`) for current Ox APIs; `.cursor/rules/ox-*.mdc` ships upstream Ox Cursor rules plus `085-ascribe-direct-style-scala-tooling.mdc` for project-specific norms.

See `AGENTS.md` for the same summary so Claude Code and other agents stay consistent.


<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:6cd5cc61 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->
