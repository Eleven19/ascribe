# GitHub Copilot coding agent (repository)

This repo configures the [Copilot coding agent](https://docs.github.com/en/copilot/concepts/agents/coding-agent/about-coding-agent) so it can build and review work the same way CI does.

## Setup steps workflow (preferred)

The workflow [`.github/workflows/copilot-setup-steps.yml`](../../.github/workflows/copilot-setup-steps.yml) defines a job named **`copilot-setup-steps`** (required). It:

- Checks out the repo **with Git submodules** (needed for `submodules/tck`).
- Installs **JDK 21 (Temurin)** to match [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).
- Runs **`./mill __.compile`** to bootstrap Mill and warm dependency caches.

Those steps run **before** the agent session and are **not** limited by the [Copilot agent firewall](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/customize-the-agent-firewall) for Bash-started processes in the same way as ad-hoc commands.

**Important:** GitHub only applies this file from the **default branch** (`main`). Merge it there for Copilot to use it.

Official reference: [Customizing the development environment for GitHub Copilot coding agent](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/customize-the-agent-environment).

## Firewall custom allowlist (admins)

If the agent still hits blocked hosts when running tools in Bash (warnings on PRs/comments), add domains under:

**Repository → Settings → Code & automation → Copilot → Coding agent → Custom allowlist**

Suggested entries for this project (only if you see blocks; the [recommended allowlist](https://docs.github.com/en/copilot/reference/copilot-allowlist-reference) already covers many Maven/Java hosts):

| Host / URL | Why |
|------------|-----|
| `gitlab.eclipse.org` | Git operations or HTTPS access for the [asciidoc-tck](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck) submodule |
| `repo1.maven.org` | Maven Central artifacts (if not already allowed via Java registry rules) |

There is **no `gh` subcommand** for editing this allowlist today. Organization owners can use the [Copilot coding agent management REST API](https://docs.github.com/en/rest/copilot/copilot-coding-agent-management) for **which repos** may use the agent, not for per-repo firewall rules.

## Validate the workflow

After merging to `main`, run **Actions → Copilot Setup Steps → Run workflow** to confirm the job stays green.
