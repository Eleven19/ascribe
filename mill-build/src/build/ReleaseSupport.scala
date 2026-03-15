package build

import mill.*
import mill.javalib.NativeImageModule

import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

trait ReleaseSupport extends mill.Module { this: NativeImageModule =>
  private val toolName = "mill-interceptor"
  private val supportedReleaseTargets = Seq(
    "x86_64-unknown-linux-gnu",
    "aarch64-unknown-linux-gnu",
    "x86_64-apple-darwin",
    "aarch64-apple-darwin",
    "x86_64-pc-windows-msvc"
  )

  private def isWindowsTarget(target: String): Boolean =
    target.endsWith("windows-msvc")

  private def validatedTarget(target: String): String =
    if supportedReleaseTargets.contains(target) then target
    else throw new IllegalArgumentException(s"Unsupported release target: $target")

  private def executableNameFor(target: String): String =
    if isWindowsTarget(target) then s"$toolName.exe" else toolName

  private def archiveExtensionFor(target: String): String =
    if isWindowsTarget(target) then "zip" else "tar.gz"

  private def assetNameFor(version: String, target: String): String =
    s"$toolName-v$version-$target.${archiveExtensionFor(target)}"

  private def writeZip(source: os.Path, entryName: String, destination: os.Path): Unit =
    val output = new ZipOutputStream(new FileOutputStream(destination.toIO))
    try
      val entry = new ZipEntry(entryName)
      output.putNextEntry(entry)
      output.write(os.read.bytes(source))
      output.closeEntry()
    finally output.close()

  def releaseTargets = Task {
    supportedReleaseTargets
  }

  def releaseExecutableName(target: String) = Task.Command {
    executableNameFor(validatedTarget(target))
  }

  def releaseArchiveExtension(target: String) = Task.Command {
    archiveExtensionFor(validatedTarget(target))
  }

  def releaseAssetName(version: String, target: String) = Task.Command {
    assetNameFor(version, validatedTarget(target))
  }

  def releaseArchive(version: String, target: String) = Task.Command {
    val checkedTarget = validatedTarget(target)
    val stageDir = Task.dest / "stage"
    val executableName = executableNameFor(checkedTarget)
    val archivePath = Task.dest / assetNameFor(version, checkedTarget)
    val stagedExecutable = stageDir / executableName

    os.makeDir.all(stageDir)
    os.copy.over(nativeImage().path, stagedExecutable, createFolders = true)

    if !isWindowsTarget(checkedTarget) then
      os.perms.set(stagedExecutable, "rwxr-xr-x")

    if isWindowsTarget(checkedTarget) then
      writeZip(stagedExecutable, executableName, archivePath)
    else
      os.proc(
        "tar",
        "-C",
        stageDir.toString,
        "-czf",
        archivePath.toString,
        executableName
      ).call(check = true)

    PathRef(archivePath)
  }
}
