# Agent Instructions

**ASCIIDOC SPECIFICATION**: As we are implementing things we should consult [https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-lang](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-lang) for the authoritative spec of ASCIIDOC.
We should also note the TCK is here: [https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck).

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

## Direct-style Scala, sbt, Metals, and Ox

Ascribe uses **Scala 3 in direct style** with **[Ox](https://github.com/softwaremill/ox)** for structured concurrency, flows, and related utilities in applicable modules. When editing or generating Scala:

- Prefer **Metals** (IDE or MCP) for compile, test, import/build, and navigation instead of driving everything through raw sbt.
- From the CLI, prefer **`sbt --client`** for speed; use plain **`sbt`** when running long-lived applications that need reliable Ctrl+C.
- Prefer **braceless** Scala 3 syntax and **functional** structure: immutable data, state passed through pure functions, local mutability only when it clarifies an algorithm—avoid pervasive `var`, mutable collections, and `return`.
- Do **not** default to `cats-effect` `IO`, `Future` stacks, or similar unless you are in a module that already uses them.
- Use Ox **nested scopes** and the **dual error model** (exceptions vs `Either` / `.ok()`); follow the **`ox-*.mdc`** Cursor rules in `.cursor/rules/` and project rule **`085-ascribe-direct-style-scala-tooling.mdc`**.

Background: [VirtusLab on direct-style Scala 3 + agents](https://virtuslab.com/blog/scala/generating-direct-style-scala-3-applications), [Ox docs for AI assistants](https://ox.softwaremill.com/latest/info/ai.html). Add `https://ox.softwaremill.com/latest/` to Cursor **Indexing & Docs** for `@Docs` Ox lookup.

## Branch and worktree workflow

Substantive work (features, refactors, multi-module or build-layout changes, large test or doc updates) must be done on a **feature branch** using a **git worktree** under `.worktrees/` (gitignored), then merged via PR to `main`. Do not push large or risky changes directly to `main`.

**Exceptions** (narrow cases where `main` is acceptable): small deployment or release CI/CD edits, one-line hotfixes explicitly requested for `main`, or other minimal maintenance agreed with maintainers.

From the repository root:

```bash
git fetch origin main
git worktree add .worktrees/<short-slug> -b feat/<topic> origin/main
cd .worktrees/<short-slug>
```

When finished, open a PR from `feat/<topic>`, and remove the worktree after merge: `git worktree remove .worktrees/<short-slug>`.

For directory choice and safety checks, follow the **using-git-worktrees** skill (`.agents/skills/...` or superpowers).

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work atomically
bd close <id>         # Complete work
bd dolt push          # Push beads data to remote
```

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

## Shared Release Skills

For changelog and release tasks, use the repo-local skills and supporting docs
instead of inventing a parallel process:

- `.github/skills/changelog-maintenance/SKILL.md`
- `.github/skills/release-process/SKILL.md`
- `docs/contributing/changelog.md`
- `docs/contributing/releasing.md`

<!-- BEGIN BEADS INTEGRATION -->
## Issue Tracking with bd (beads)

**IMPORTANT**: This project uses **bd (beads)** for ALL issue tracking. Do NOT use markdown TODOs, task lists, or other tracking methods.

### Why bd?

- Dependency-aware: Track blockers and relationships between issues
- Git-friendly: Dolt-powered version control with native sync
- Agent-optimized: JSON output, ready work detection, discovered-from links
- Prevents duplicate tracking systems and confusion

### Quick Start

**Check for ready work:**

```bash
bd ready --json
```

**Create new issues:**

```bash
bd create "Issue title" --description="Detailed context" -t bug|feature|task -p 0-4 --json
bd create "Issue title" --description="What this issue is about" -p 1 --deps discovered-from:bd-123 --json
```

**Claim and update:**

```bash
bd update <id> --claim --json
bd update bd-42 --priority 1 --json
```

**Complete work:**

```bash
bd close bd-42 --reason "Completed" --json
```

### Issue Types

- `bug` - Something broken
- `feature` - New functionality
- `task` - Work item (tests, docs, refactoring)
- `epic` - Large feature with subtasks
- `chore` - Maintenance (dependencies, tooling)

### Priorities

- `0` - Critical (security, data loss, broken builds)
- `1` - High (major features, important bugs)
- `2` - Medium (default, nice-to-have)
- `3` - Low (polish, optimization)
- `4` - Backlog (future ideas)

### Workflow for AI Agents

1. **Check ready work**: `bd ready` shows unblocked issues
2. **Claim your task atomically**: `bd update <id> --claim`
3. **Work on it**: Implement, test, document
4. **Discover new work?** Create linked issue:
   - `bd create "Found bug" --description="Details about what was found" -p 1 --deps discovered-from:<parent-id>`
5. **Complete**: `bd close <id> --reason "Done"`

### Auto-Sync

bd automatically syncs via Dolt:

- Each write auto-commits to Dolt history
- Use `bd dolt push`/`bd dolt pull` for remote sync
- No manual export/import needed!

### Important Rules

- ✅ Use bd for ALL task tracking
- ✅ Always use `--json` flag for programmatic use
- ✅ Link discovered work with `discovered-from` dependencies
- ✅ Check `bd ready` before asking "what should I work on?"
- ❌ Do NOT create markdown TODO lists
- ❌ Do NOT use external issue trackers
- ❌ Do NOT duplicate tracking systems

For more details, see README.md and docs/QUICKSTART.md.

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

<!-- END BEADS INTEGRATION -->
