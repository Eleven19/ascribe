# Main Branch Governance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add repository governance for `main` by committing a catch-all CODEOWNERS file and applying a GitHub ruleset that blocks deletion and requires pull requests with one approving Code Owner review while preserving admin bypass.

**Architecture:** Make the repository-owned piece explicit in git via `.github/CODEOWNERS`, and manage the enforcement policy through a GitHub repository ruleset targeted at `refs/heads/main`. Because the user explicitly requested a direct commit on `main`, the implementation must keep the worktree synchronized with `origin/main` and commit only the intended governance file before applying the ruleset with `gh api`.

**Tech Stack:** Git, GitHub CLI (`gh api`), GitHub repository rulesets, CODEOWNERS

---

### Task 1: Verify `main` Is Current and Clean for a Direct Governance Commit

**Files:**
- Modify: none
- Test: local `main` branch state

**Step 1: Run the branch-state check**

Run:

```bash
git status --short --branch
git log --oneline --decorate -n 5
```

Expected: `main` is at the current `origin/main` tip and only the planning docs are untracked.

**Step 2: Fetch the latest remote state**

Run:

```bash
git fetch origin main
```

Expected: fetch completes without changing tracked files in the worktree.

**Step 3: Verify no divergence remains**

Run:

```bash
git rev-list --left-right --count origin/main...HEAD
```

Expected: `0	0`

**Step 4: Commit**

No commit in this task.

### Task 2: Add the Repository-Wide CODEOWNERS File

**Files:**
- Create: `.github/CODEOWNERS`
- Test: `.github/CODEOWNERS`

**Step 1: Write the failing ownership check**

Run:

```bash
test -f .github/CODEOWNERS
```

Expected: FAIL because the file does not exist yet.

**Step 2: Create the minimal CODEOWNERS file**

Add `.github/CODEOWNERS` with:

```text
* @DamianReeves
```

**Step 3: Verify the file contents**

Run:

```bash
sed -n '1,20p' .github/CODEOWNERS
```

Expected: exactly one catch-all entry for `@DamianReeves`.

**Step 4: Commit only the governance file**

```bash
git add .github/CODEOWNERS
git commit -m "chore: add repository codeowners"
```

### Task 3: Create the `main` Branch Ruleset with `gh api`

**Files:**
- Modify: GitHub repository settings for `Eleven19/mill-interceptor`
- Test: ruleset API response

**Step 1: Write the failing discovery check**

Run:

```bash
gh api repos/Eleven19/mill-interceptor/rulesets
```

Expected: either `[]` or no ruleset matching `main`, which confirms the governance rule still needs to be created.

**Step 2: Create the branch-targeting ruleset**

Run a `gh api` POST with a payload shaped like:

```json
{
  "name": "Protect main",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/main"],
      "exclude": []
    }
  },
  "bypass_actors": [
    {
      "actor_type": "RepositoryRole",
      "actor_id": 5,
      "bypass_mode": "always"
    }
  ],
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "pull_request",
      "parameters": {
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": true,
        "require_last_push_approval": false,
        "required_approving_review_count": 1,
        "required_review_thread_resolution": false
      }
    }
  ]
}
```

Notes:

- The `RepositoryRole` bypass actor should target admins so maintainer/admin bypass remains available.
- If GitHub rejects the role id or payload shape, inspect the API error and adjust to the accepted repository-role identifier for admins/maintainers.

**Step 3: Read the created ruleset back**

Run:

```bash
gh api repos/Eleven19/mill-interceptor/rulesets
```

Expected: a ruleset exists for `refs/heads/main`.

**Step 4: Verify the rule details**

Run:

```bash
gh api repos/Eleven19/mill-interceptor/rulesets/<ruleset-id>
```

Expected:

- target is `branch`
- include ref is `refs/heads/main`
- deletion rule is present
- pull request rule is present
- `required_approving_review_count` is `1`
- `require_code_owner_review` is `true`
- bypass actor is present for admins/maintainers

**Step 5: Commit**

No git commit in this task because the change is repository configuration, not a tracked file.

### Task 4: Final Verification and Push

**Files:**
- Test: `.github/CODEOWNERS`
- Test: GitHub ruleset configuration

**Step 1: Verify the local branch is ready to push**

Run:

```bash
git status --short --branch
```

Expected: only the intended CODEOWNERS commit is on `main`; no unrelated tracked changes.

**Step 2: Verify CODEOWNERS is present in git history**

Run:

```bash
git show --stat --oneline HEAD
```

Expected: the most recent commit is the CODEOWNERS addition.

**Step 3: Push `main`**

Run:

```bash
git push origin main
```

Expected: the CODEOWNERS commit is published.

**Step 4: Re-read the ruleset once more**

Run:

```bash
gh api repos/Eleven19/mill-interceptor/rulesets
```

Expected: the `main` protection ruleset is still active after the push.
