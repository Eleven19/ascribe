package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.DocumentPath
import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.{<, Abort, Sync, Path}
import kyo.toJava

/** A Sink that writes rendered output to files on the file system.
  *
  * Each document is written to a file at `outputDir / documentPath`. Parent directories are created as needed.
  */
object FileSink:

    /** Create a Sink that writes rendered documents to a directory. */
    def toDirectory(outputDir: Path): Sink[Sync & Abort[PipelineError]] =
        new Sink[Sync & Abort[PipelineError]]:
            def write(rendered: Map[DocumentPath, String]): Unit < (Sync & Abort[PipelineError]) =
                writeEntries(outputDir, rendered.toList)

    /** Create a Sink that writes a single rendered document to a file. */
    def toFile(outputFile: Path): Sink[Sync & Abort[PipelineError]] =
        new Sink[Sync & Abort[PipelineError]]:
            def write(rendered: Map[DocumentPath, String]): Unit < (Sync & Abort[PipelineError]) =
                rendered.headOption match
                    case Some((_, content)) =>
                        val parentDir = Path(outputFile.toJava.getParent.toString)
                        ensureDir(parentDir).flatMap(_ => KyoFileError.write(outputFile.write(content)))
                    case None => ()

    private def writeEntries(
        outputDir: Path,
        entries: List[(DocumentPath, String)]
    ): Unit < (Sync & Abort[PipelineError]) =
        entries match
            case Nil => ()
            case (docPath, content) :: rest =>
                val targetPath = Path(outputDir.toJava.resolve(docPath.render).toString)
                val parentDir  = Path(targetPath.toJava.getParent.toString)
                ensureDir(parentDir).flatMap { _ =>
                    KyoFileError.write(targetPath.write(content)).flatMap { _ =>
                        writeEntries(outputDir, rest)
                    }
                }

    /** Create directory if it doesn't exist. */
    private def ensureDir(dir: Path): Unit < (Sync & Abort[PipelineError]) =
        dir.exists.map { exists =>
            if !exists then KyoFileError.fs(dir.mkDir)
            else ()
        }
