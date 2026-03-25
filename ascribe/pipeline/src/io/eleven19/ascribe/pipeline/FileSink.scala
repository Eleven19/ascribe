package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.DocumentPath
import kyo.{<, IO, Path}

/** A Sink that writes rendered output to files on the file system.
  *
  * Each document is written to a file at `outputDir / documentPath`. Parent directories are created as needed.
  */
object FileSink:

    /** Create a Sink that writes rendered documents to a directory. */
    def toDirectory(outputDir: Path): Sink[IO] =
        new Sink[IO]:
            def write(rendered: Map[DocumentPath, String]): Unit < IO =
                writeEntries(outputDir, rendered.toList)

    /** Create a Sink that writes a single rendered document to a file. */
    def toFile(outputFile: Path): Sink[IO] =
        new Sink[IO]:
            def write(rendered: Map[DocumentPath, String]): Unit < IO =
                rendered.headOption match
                    case Some((_, content)) =>
                        val parentDir = Path(outputFile.toJava.getParent.toString)
                        ensureDir(parentDir).map(_ => outputFile.write(content))
                    case None => ()

    private def writeEntries(
        outputDir: Path,
        entries: List[(DocumentPath, String)]
    ): Unit < IO =
        entries match
            case Nil => ()
            case (docPath, content) :: rest =>
                val targetPath = Path(outputDir.toJava.resolve(docPath.render).toString)
                val parentDir  = Path(targetPath.toJava.getParent.toString)
                ensureDir(parentDir).map { _ =>
                    targetPath.write(content).map { _ =>
                        writeEntries(outputDir, rest)
                    }
                }

    /** Create directory if it doesn't exist. */
    private def ensureDir(dir: Path): Unit < IO =
        dir.exists.map { exists =>
            if !exists then dir.mkDir
            else ()
        }
