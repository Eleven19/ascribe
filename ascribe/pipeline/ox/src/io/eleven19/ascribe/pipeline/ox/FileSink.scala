package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.supervised

/** File-backed [[Sink]] using [[https://github.com/com-lihaoyi/os-lib os-lib]] inside an Ox [[supervised]] scope
  * (mirrors Kyo [[io.eleven19.ascribe.pipeline.FileSink]]).
  */
object FileSink:

    def toDirectory(outputDir: os.Path): Sink =
        new Sink:
            def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                supervised:
                    writeEntries(outputDir, rendered.toList)

    def toFile(outputFile: os.Path): Sink =
        new Sink:
            def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                supervised:
                    rendered.headOption match
                        case Some((_, content)) =>
                            writeString(outputFile, content, createFolders = true)
                        case None => Right(())

    private def writeEntries(
        outputDir: os.Path,
        entries: List[(DocumentPath, String)]
    ): Either[PipelineError, Unit] =
        entries match
            case Nil => Right(())
            case (docPath, content) :: rest =>
                val targetPath = outputDir / os.SubPath(docPath.render)
                writeString(targetPath, content, createFolders = true).flatMap { _ =>
                    writeEntries(outputDir, rest)
                }

    private def writeString(path: os.Path, content: String, createFolders: Boolean): Either[PipelineError, Unit] =
        try
            os.write(path, content, createFolders = createFolders)
            Right(())
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("write failed"), Some(e)))
