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
