package build

import mill.*

trait TckSubmoduleSupport extends Module {

  /** Path to the TCK git submodule. Defaults to submodules/tck relative to workspace root. */
  def tckSubmodulePath: T[os.Path] = Task {
    Task.ctx().workspace / "submodules" / "tck"
  }

  /** Initialize/update the TCK submodule. Always runs when invoked (Command, not cached). */
  def tckSync(): Command[PathRef] = Task.Command {
    val submodulePath = tckSubmodulePath()
    val workspace = Task.ctx().workspace
    Task.log.info(s"Syncing TCK submodule at $submodulePath")
    os.proc("git", "submodule", "update", "--init", submodulePath.relativeTo(workspace).toString)
      .call(cwd = workspace)
    Task.log.info("TCK submodule sync complete")
    PathRef(submodulePath)
  }
}
