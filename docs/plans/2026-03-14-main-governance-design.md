# Main Branch Governance Design

## Goal

Protect `main` with GitHub repository rulesets instead of classic branch protection, require pull requests with one approving Code Owner review, prevent branch deletion, and preserve admin bypass. Add a repository-wide CODEOWNERS file that assigns all paths to `@DamianReeves`.

## Scope

This design covers:

- syncing the local `main` worktree to the latest `origin/main`
- adding `.github/CODEOWNERS`
- creating a branch-targeting repository ruleset for `main` through the GitHub CLI

This design does not cover:

- extending protection to other branches
- adding required status checks beyond the review policy
- force-push restrictions or signed-commit requirements

## Constraints

- The repo default branch is `main`.
- The local `main` worktree is currently ahead of and behind `origin/main`, with local modifications present, so syncing must be done carefully before a direct commit on `main`.
- The GitHub CLI is authenticated and can manage repo settings.
- Admin bypass must remain available, which implies the final policy cannot fully enforce restrictions on admins.

## Recommended Approach

Use a GitHub repository ruleset that targets `refs/heads/main`, rather than classic branch protection.

This gives one place to manage the branch governance policy and maps better to future repository policy growth.

The concrete changes are:

1. reconcile the local `main` branch with `origin/main`
2. add `.github/CODEOWNERS` with `* @DamianReeves`
3. commit that change directly to `main`
4. create or update a ruleset for `main` with:
   - pull requests required
   - one approving review required
   - Code Owner review required
   - branch deletion blocked
   - admin bypass preserved

## Ruleset Shape

The ruleset should target the branch reference:

- `refs/heads/main`

Required behavior:

- direct non-admin pushes should be blocked by PR requirements
- one approval is required before merge
- that approval must satisfy Code Owner review when touched paths match CODEOWNERS
- deleting `main` is blocked
- admins can bypass the restrictions

This is the intended functional result even if the exact GitHub ruleset payload is more verbose.

## CODEOWNERS Shape

Place the file at:

- `.github/CODEOWNERS`

Contents:

```text
* @DamianReeves
```

Using `.github/CODEOWNERS` keeps ownership configuration alongside other repository governance files.

## Sync Strategy

Because the local `main` branch is not a clean, up-to-date mirror of `origin/main`, the first step must be to inspect the divergence and local modifications.

Preferred order:

1. inspect local commits and unstaged changes
2. fetch `origin/main`
3. reconcile the local branch onto the remote tip without overwriting user changes
4. only then add the CODEOWNERS commit

If the local modifications are unrelated user work, they should not be discarded. The safest path is to preserve them while still getting `main` to the current remote baseline before the governance commit.

## Verification

After applying the change:

- confirm `.github/CODEOWNERS` is on `main`
- confirm the ruleset exists and targets `main`
- confirm the ruleset requires pull requests and one Code Owner approval
- confirm branch deletion is disallowed

## Risks and Mitigations

### Local `main` worktree is dirty

Risk:
The direct-to-main commit could accidentally mix in unrelated local changes.

Mitigation:
Inspect branch divergence and working tree state before committing. Commit only the CODEOWNERS file.

### Ruleset payload mismatch

Risk:
GitHub rulesets are more verbose than classic branch protection and the first payload may omit or misname a required rule.

Mitigation:
Read back the created ruleset with `gh api` after creation and verify the stored configuration matches the intended policy.

### Over-restricting admins

Risk:
The ruleset could accidentally block the intended maintainer bypass path.

Mitigation:
Use the ruleset bypass actor configuration for admins instead of enabling stricter admin enforcement.
