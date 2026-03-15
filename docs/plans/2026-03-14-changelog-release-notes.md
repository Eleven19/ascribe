# Changelog and Release Notes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a checked-in Keep a Changelog style `CHANGELOG.md`, integrate `git-cliff` into the release workflow, and publish GitHub Releases that prepend curated changelog content to generated release notes.

**Architecture:** Keep changelog curation in git and treat the release workflow as a validator and publisher. Add one `git-cliff` config, one release-notes assembly script, and one checked-in changelog policy, then expose the same process through shared repo skills and thin agent-specific discovery pointers.

**Tech Stack:** GitHub Actions, Bash, `gh`, `git-cliff`, Keep a Changelog, Markdown, repo-local agent skills

---

### Task 1: Add the checked-in changelog skeleton

**Files:**
- Create: `CHANGELOG.md`
- Modify: `docs/contributing/README.md`

**Step 1: Write the failing validation target**

Document the exact first release section that must exist for current history:

```markdown
## [Unreleased]

### Added

### Changed

### Fixed

### Documentation

### CI
```

and a first released section for the current released history:

```markdown
## [0.1.0] - 2026-03-14
```

The failure condition is simple: later release-note assembly must not find `CHANGELOG.md` yet.

**Step 2: Verify the file is absent**

Run: `test ! -f CHANGELOG.md`
Expected: exit code `0`

**Step 3: Write the minimal changelog**

Create `CHANGELOG.md` with:
- the standard Keep a Changelog intro
- `[Unreleased]`
- empty `Added`, `Changed`, `Fixed`, `Documentation`, and `CI` buckets
- an initial released section summarizing the major work already shipped

Update `docs/contributing/README.md` to link a new changelog/release-process article once that doc exists.

**Step 4: Verify the file shape**

Run: `rg -n "^## \\[(Unreleased|0\\.1\\.0)\\]" CHANGELOG.md`
Expected: one `Unreleased` section and one `0.1.0` section are found

**Step 5: Commit**

```bash
git add CHANGELOG.md docs/contributing/README.md
git commit -m "docs: add keep a changelog file"
```

### Task 2: Add `git-cliff` configuration for this repository

**Files:**
- Create: `cliff.toml`
- Test: `CHANGELOG.md`

**Step 1: Write the failing generation command**

Run a generation command before `cliff.toml` exists:

```bash
git-cliff --config cliff.toml --unreleased
```

Expected: FAIL because `cliff.toml` does not exist yet

**Step 2: Write the minimal config**

Create `cliff.toml` with:
- a changelog header/footer compatible with supplemental release notes
- commit parsers that group:
  - `Implement`, `Add`, `feat`, `feature` into `Added`
  - `Fix`, `bug`, `repair` into `Fixed`
  - `docs`, `readme`, `guide` into `Documentation`
  - `ci`, `workflow`, `release`, `build`, `chore` into `CI`
  - everything else relevant into `Changed`
- filters to hide merge-noise or empty commits
- tag handling that works with `v<version>` tags

Keep the config optimized for generated release notes, not full changelog replacement.

**Step 3: Verify local generation works**

Run: `git-cliff --config cliff.toml --latest`
Expected: PASS and output grouped notes using the configured buckets

**Step 4: Verify the config against current history**

Run: `git-cliff --config cliff.toml --unreleased | sed -n '1,80p'`
Expected: PASS and the output is grouped and readable, without raw merge noise

**Step 5: Commit**

```bash
git add cliff.toml
git commit -m "build: add git-cliff configuration"
```

### Task 3: Add release-note assembly and changelog validation scripts

**Files:**
- Create: `scripts/ci/build-release-notes.sh`
- Create: `scripts/ci/test-build-release-notes.sh`
- Modify: `scripts/ci/create-or-update-github-release.sh`

**Step 1: Write the failing script test**

Create `scripts/ci/test-build-release-notes.sh` that:
- creates a temporary changelog fixture with `[Unreleased]` and `## [1.2.3] - 2026-03-14`
- runs `scripts/ci/build-release-notes.sh 1.2.3`
- asserts the output file contains:
  - the exact `1.2.3` changelog section first
  - a separator
  - a generated-notes heading after the curated section

Also add a second case that requests `9.9.9` and expects a non-zero exit code.

**Step 2: Run the test to verify it fails**

Run: `bash scripts/ci/test-build-release-notes.sh`
Expected: FAIL because `scripts/ci/build-release-notes.sh` does not exist yet

**Step 3: Write the minimal implementation**

Create `scripts/ci/build-release-notes.sh` that:
- requires the normalized version as an argument
- optionally accepts env vars for `CHANGELOG_PATH`, `CLIFF_CONFIG`, and output path for testability
- extracts the exact `## [<version>] - <date>` section from `CHANGELOG.md`
- fails if the section is missing or empty
- runs `git-cliff` with `cliff.toml` to generate supplemental notes
- writes a final markdown file with:
  - curated changelog heading/content first
  - a divider
  - a generated notes heading and the `git-cliff` output
- prints the output path or writes it to `$GITHUB_OUTPUT` when helpful for CI

Modify `scripts/ci/create-or-update-github-release.sh` so it:
- requires `RELEASE_NOTES_FILE`
- uses that file when creating or updating the release
- stops using `generate_release_notes=true`

**Step 4: Run the script test again**

Run: `bash scripts/ci/test-build-release-notes.sh`
Expected: PASS

**Step 5: Smoke-test with repo history**

Run: `scripts/ci/build-release-notes.sh 0.1.0`
Expected: PASS and a markdown file is produced with curated notes first

**Step 6: Commit**

```bash
git add scripts/ci/build-release-notes.sh scripts/ci/test-build-release-notes.sh scripts/ci/create-or-update-github-release.sh
git commit -m "ci: add changelog-backed release notes assembly"
```

### Task 4: Wire `git-cliff` and release-note assembly into the release workflow

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: `scripts/ci/upload-release-assets.sh`
- Test: `scripts/ci/build-release-notes.sh`

**Step 1: Write the failing workflow expectation**

Identify the missing publish-job behaviors:
- no `git-cliff` install
- no release-notes assembly step
- release creation still uses GitHub-generated notes

The failure condition is the current workflow cannot publish the curated+generated hybrid body.

**Step 2: Update the publish job**

Modify `.github/workflows/release.yml` so the publish job:
- installs `git-cliff`
- runs `scripts/ci/build-release-notes.sh "${{ needs.metadata.outputs.version }}"`
- exports the produced notes path as `RELEASE_NOTES_FILE`
- calls the updated `scripts/ci/create-or-update-github-release.sh`

Keep the matrix build job unchanged unless an extra artifact is needed.

If useful, adjust `scripts/ci/upload-release-assets.sh` so it continues uploading only archives plus `SHA256SUMS`, not the temporary notes file.

**Step 3: Verify workflow syntax**

Run: `git diff -- .github/workflows/release.yml scripts/ci/create-or-update-github-release.sh scripts/ci/build-release-notes.sh`
Expected: release generation now uses the explicit notes file and no longer asks GitHub to auto-generate notes

**Step 4: Re-run release-notes smoke test**

Run: `scripts/ci/build-release-notes.sh 0.1.0`
Expected: PASS

**Step 5: Commit**

```bash
git add .github/workflows/release.yml scripts/ci/upload-release-assets.sh
git commit -m "ci: publish hybrid changelog release notes"
```

### Task 5: Document changelog maintenance and release-note flow

**Files:**
- Create: `docs/contributing/changelog.md`
- Modify: `docs/contributing/releasing.md`
- Modify: `docs/contributing/README.md`

**Step 1: Write the missing-docs checklist**

The docs must explain:
- how to maintain `[Unreleased]`
- what each bucket means
- how to cut a versioned release section
- how release publication fails when the version section is missing
- how curated notes and generated notes combine
- ASCII diagrams for changelog maintenance and release-note assembly

**Step 2: Add the docs**

Create `docs/contributing/changelog.md` with:
- Keep a Changelog policy
- bucket definitions
- examples for feature, fix, docs, and CI entries
- release-preparation steps
- ASCII diagrams

Update `docs/contributing/releasing.md` to:
- reference `CHANGELOG.md` and `cliff.toml`
- explain the hybrid release-note body
- include the changelog validation failure mode
- include local verification commands for `scripts/ci/build-release-notes.sh`

Update `docs/contributing/README.md` so both articles are discoverable.

**Step 3: Verify docs coverage**

Run: `rg -n "Unreleased|git-cliff|release notes|ASCII|build-release-notes" docs/contributing CHANGELOG.md`
Expected: all topics are covered in the new docs

**Step 4: Commit**

```bash
git add docs/contributing/changelog.md docs/contributing/releasing.md docs/contributing/README.md
git commit -m "docs: describe changelog and release notes flow"
```

### Task 6: Add shared repo skills and thin agent-specific discovery

**Files:**
- Create: `.github/skills/changelog-maintenance/SKILL.md`
- Create: `.github/skills/release-process/SKILL.md`
- Modify: `AGENTS.md`
- Create: `.cursor/rules/release-process.mdc`
- Create: `.cursor/rules/changelog-maintenance.mdc`
- Create: `CLAUDE.md`
- Create: `.github/copilot-instructions.md`

**Step 1: Write the missing-skill expectations**

The repo currently lacks:
- repo-local shared release skills
- Cursor rules for release/changelog work
- repo-local Claude instructions
- repo-local Copilot instructions

**Step 2: Add the shared skills**

Create `.github/skills/changelog-maintenance/SKILL.md` that:
- tells agents to update `[Unreleased]`
- enforces the Keep a Changelog buckets
- points to `CHANGELOG.md` and `docs/contributing/changelog.md`

Create `.github/skills/release-process/SKILL.md` that:
- tells agents to require an exact version section before release
- points to `docs/contributing/releasing.md`, `cliff.toml`, and `scripts/ci/build-release-notes.sh`
- requires verification commands before claiming release work complete

Keep both skills short and reference-oriented so the process lives in normal repo docs.

**Step 3: Add thin discovery glue**

Update `AGENTS.md` with a short release-process section that points Codex users at the two repo skills and their supporting docs.

Create `.cursor/rules/release-process.mdc` and `.cursor/rules/changelog-maintenance.mdc` that direct Cursor to the same shared skills.

Create `CLAUDE.md` and `.github/copilot-instructions.md` with minimal instructions that point release and changelog tasks at the shared repo skills instead of duplicating the process.

**Step 4: Verify file presence**

Run: `find .github/skills .cursor/rules -maxdepth 2 -type f | sort`
Expected: the two skills and the two new Cursor rules exist

Run: `test -f CLAUDE.md && test -f .github/copilot-instructions.md`
Expected: exit code `0`

**Step 5: Commit**

```bash
git add .github/skills AGENTS.md .cursor/rules/release-process.mdc .cursor/rules/changelog-maintenance.mdc CLAUDE.md .github/copilot-instructions.md
git commit -m "docs: add shared release process agent skills"
```

### Task 7: Run full local verification

**Files:**
- Test: `CHANGELOG.md`
- Test: `cliff.toml`
- Test: `scripts/ci/build-release-notes.sh`
- Test: `.github/workflows/release.yml`
- Test: `docs/contributing/changelog.md`
- Test: `docs/contributing/releasing.md`

**Step 1: Run release workflow regressions**

Run:

```bash
COURSIER_CACHE=/tmp/coursier ./mill test.testLocal
scripts/ci/build-release-notes.sh 0.1.0
bash scripts/ci/test-build-release-notes.sh
git-cliff --config cliff.toml --latest >/tmp/latest-release-notes.md
```

Expected:
- Mill tests pass
- release-notes assembly passes for the known version
- shell test passes
- `git-cliff` command exits `0`

**Step 2: Verify missing-version failure**

Run:

```bash
if scripts/ci/build-release-notes.sh 9.9.9; then
  echo "unexpected success"
  exit 1
fi
```

Expected: command fails and prints a clear missing-version message

**Step 3: Review the generated output**

Run:

```bash
sed -n '1,120p' /tmp/latest-release-notes.md
```

Expected: grouped generated notes are readable and consistent with the configured buckets

**Step 4: Commit**

```bash
git add CHANGELOG.md cliff.toml scripts/ci .github/workflows/release.yml docs/contributing .github/skills AGENTS.md .cursor/rules CLAUDE.md .github/copilot-instructions.md
git commit -m "test: verify changelog-backed release workflow"
```

