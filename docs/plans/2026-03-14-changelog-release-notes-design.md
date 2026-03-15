# Changelog and Release Notes Design

## Goal

Add a checked-in Keep a Changelog style `CHANGELOG.md`, integrate `git-cliff` into the GitHub release workflow, and publish GitHub Releases that combine curated changelog content with generated commit-derived notes.

## Scope

This design covers:

- A checked-in `CHANGELOG.md` with a persistent `[Unreleased]` section
- `git-cliff` configuration for generated release-note supplements
- Release workflow changes to validate changelog state and assemble release notes
- Contributor documentation for changelog maintenance and release preparation
- Shared agent skills for changelog maintenance and release-process tasks across Claude, Cursor, Codex, and Copilot

This design does not cover:

- Automatic changelog commits from CI
- Enforcing Conventional Commits across the repository
- Maven Central publication changes

## Constraints

- `main` now requires pull requests and Code Owner review for normal contributors, so the release workflow should not mutate repository contents.
- The repository already has a GitHub Release workflow in `.github/workflows/release.yml`.
- The repository does not currently have a checked-in `CHANGELOG.md` or `git-cliff` configuration.
- Existing commit history is not consistently Conventional Commits, so generated grouping must rely on explicit matching rules rather than stock presets.

## Recommended Approach

Use a hybrid model:

- `CHANGELOG.md` is the curated, checked-in source of truth for notable changes.
- `git-cliff` generates supplemental notes from Git history and tags.
- GitHub Release bodies are assembled as:
  1. exact curated changelog section for the release version
  2. separator
  3. generated `git-cliff` notes for the same release

The release workflow should fail if `CHANGELOG.md` does not already contain an exact section for the target version.

This keeps release quality high, avoids self-mutating release automation, and still captures commit-level detail in the published release.

## Changelog Contract

The repository should add a root-level `CHANGELOG.md` in Keep a Changelog format.

Required structure:

- Introductory header explaining the format
- `## [Unreleased]`
- Keep a Changelog buckets under `[Unreleased]`
- Versioned sections using:
  - `## [1.2.3] - 2026-03-14`
  - `## [1.2.3-rc.1] - 2026-03-14`

Expected buckets:

- `Added`
- `Changed`
- `Fixed`
- `Documentation`
- `CI`

Normal feature or maintenance PRs should update `[Unreleased]` when they introduce user-visible or operator-visible changes. Release preparation should promote the relevant entries from `[Unreleased]` into the exact versioned section before the tag is cut or the manual release workflow is run.

## Generated Release Notes

The repository should add a `cliff.toml` configuration that maps this repository's commit styles into useful release-note groups. Because commit subjects are currently mixed, the config should use practical regex-based grouping instead of assuming strict semantic prefixes.

Generated notes should be used for GitHub Release publication, not for rewriting the curated changelog.

The generated section should prioritize:

- merged feature work
- fixes
- docs changes
- CI/tooling changes

Low-signal internal commits should be filtered or grouped conservatively so the generated section remains useful rather than noisy.

## Release Workflow Changes

The release workflow should stop relying on GitHub's generated release notes and instead publish an explicit release body assembled in CI.

Required publish-job behavior:

1. Install `git-cliff`
2. Validate that `CHANGELOG.md` contains an exact section for the normalized release version
3. Extract that exact version section
4. Generate supplemental notes with `git-cliff`
5. Compose a release body file with curated changelog first and generated notes second
6. Create or update the GitHub Release using the prepared body file

If the exact changelog section is missing, empty, or cannot be extracted, the workflow should fail before creating or updating the GitHub Release.

## Release Flow Diagram

```text
           +-----------------------------+
           | release version normalized  |
           | from tag or dispatch input  |
           +-------------+---------------+
                         |
                         v
           +-----------------------------+
           | exact version section in    |
           | CHANGELOG.md present?       |
           +-------------+---------------+
                         |
               no        | yes
          fail release   v
                   +----------------------+
                   | extract curated      |
                   | changelog section    |
                   +----------+-----------+
                              |
                              v
                   +----------------------+
                   | run git-cliff for    |
                   | generated notes      |
                   +----------+-----------+
                              |
                              v
                   +----------------------+
                   | assemble release     |
                   | body file            |
                   +----------+-----------+
                              |
                              v
                   +----------------------+
                   | create/update        |
                   | GitHub Release       |
                   +----------------------+
```

## Documentation Changes

The release guide should be expanded so contributors understand both ongoing changelog maintenance and release-cutting expectations.

Documentation should cover:

- when to update `[Unreleased]`
- what belongs in each changelog bucket
- how to prepare a versioned release section
- how the workflow validates changelog state
- how `git-cliff` contributes the generated notes
- local verification commands for release-note assembly

The docs should also include ASCII diagrams for:

- changelog maintenance flow
- release-note assembly flow

## Shared Agent Skill Design

The repository should add two separate canonical skills using a shared `SKILL.md` format:

- `.github/skills/changelog-maintenance/SKILL.md`
- `.github/skills/release-process/SKILL.md`

These skills should be minimal wrappers that reference the canonical repo docs and scripts instead of copying the full process text.

Responsibilities:

- `changelog-maintenance`
  - instruct agents to update `[Unreleased]`
  - enforce Keep a Changelog buckets
  - point agents to the changelog policy and examples
- `release-process`
  - instruct agents how to prepare a versioned changelog section
  - require changelog validation before release work is considered complete
  - point agents to the release workflow docs and release-note assembly scripts

Agent-specific integration should stay thin:

- `AGENTS.md` should point release-related tasks at the relevant shared skills
- Cursor rules should direct release and changelog tasks to the shared skills
- Copilot should discover the repo-local skills from `.github/skills`
- Claude support should use the same canonical skill content or references rather than a separate duplicated policy

## Testing Strategy

Testing should focus on deterministic release-note assembly and validation.

Add verification for:

- extracting an exact version section from `CHANGELOG.md`
- failing cleanly when the version section is missing
- generating a release body that contains curated notes first and generated notes second
- keeping `[Unreleased]` intact when generating release notes

Workflow verification should ensure:

- stable versions publish as stable releases
- prerelease versions publish as prereleases
- release publication aborts before release creation when changelog validation fails

## Risks and Mitigations

### Curated changelog drift

Risk:
Contributors may forget to update `[Unreleased]`.

Mitigation:
Document the requirement clearly and add shared agent skills that enforce changelog maintenance during change work.

### Noisy generated notes

Risk:
Mixed commit styles can produce low-signal generated release notes.

Mitigation:
Use explicit `git-cliff` filtering and grouping rules tuned to this repository's history.

### Release failure near cut time

Risk:
Releases can fail late if the versioned changelog section was not prepared.

Mitigation:
Fail fast in the publish job before any release object is created or updated, and document the pre-release checklist clearly.

## References

- [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
- [git-cliff changelog configuration](https://git-cliff.org/docs/configuration/changelog/)
- [git-cliff git configuration](https://git-cliff.org/docs/configuration/git/)
- [git-cliff GitHub Actions usage](https://git-cliff.org/docs/github-actions/git-cliff-action/)
- [GitHub Copilot agent skills](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/create-skills)
- [skills.sh / shared skill patterns](https://github.com/vercel-labs/skills)
