package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.supervised

import java.nio.file.{Files, Path}

/** File-backed [[Sink]] using NIO inside an Ox [[supervised]] scope (mirrors Kyo
  * [[io.eleven19.ascribe.pipeline.FileSink]]).
  */
object FileSink:

    def toDirectory(outputDir: Path): Sink =
        new Sink:
            def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                supervised:
                    writeEntries(outputDir, rendered.toList)

    def toFile(outputFile: Path): Sink =
        new Sink:
            def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                supervised:
                    rendered.headOption match
                        case Some((_, content)) =>
                            val parentDir = outputFile.getParent
                            if parentDir == null then Left(PipelineError.IOError(s"No parent for $outputFile", None))
                            else
                                ensureDir(parentDir).flatMap { _ =>
                                    writeString(outputFile, content)
                                }
                        case None => Right(())

    private def writeEntries(
        outputDir: Path,
        entries: List[(DocumentPath, String)]
    ): Either[PipelineError, Unit] =
        entries match
            case Nil => Right(())
            case (docPath, content) :: rest =>
                val targetPath = outputDir.resolve(docPath.render).normalize()
                val parentDir  = targetPath.getParent
                if parentDir == null then Left(PipelineError.IOError(s"No parent for $targetPath", None))
                else
                    ensureDir(parentDir).flatMap { _ =>
                        writeString(targetPath, content).flatMap { _ =>
                            writeEntries(outputDir, rest)
                        }
                    }

    private def ensureDir(dir: Path): Either[PipelineError, Unit] =
        try
            if !Files.exists(dir) then Files.createDirectories(dir): Unit
            Right(())
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("mkdir failed"), Some(e)))

    private def writeString(path: Path, content: String): Either[PipelineError, Unit] =
        try
            Files.writeString(path, content)
            Right(())
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("write failed"), Some(e)))
