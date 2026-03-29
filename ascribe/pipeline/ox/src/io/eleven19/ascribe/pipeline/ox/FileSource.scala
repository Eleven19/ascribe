package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
import io.eleven19.ascribe.cst.{CstDocument, CstLowering}
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.supervised
import parsley.{Failure, Success}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** File-backed [[Source]] using NIO inside an Ox [[supervised]] scope (mirrors Kyo
  * [[io.eleven19.ascribe.pipeline.FileSource]]).
  */
object FileSource:

    private val adocExtensions = Set(".adoc", ".asciidoc", ".ad", ".asc")

    def fromDirectory(dir: Path): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                supervised:
                    readDirectory(dir)

    def fromFile(file: Path): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                supervised:
                    readSingleFile(file)

    private def readDirectory(baseDir: Path): Either[PipelineError, DocumentTree] =
        if !Files.isDirectory(baseDir) then Left(PipelineError.IOError(s"Not a directory: $baseDir", None))
        else
            val stream = Files.walk(baseDir)
            try
                val adocPaths = stream
                    .iterator()
                    .asScala
                    .filter { p =>
                        Files.isRegularFile(p) && {
                            val name = p.getFileName.toString
                            adocExtensions.exists(ext => name.endsWith(ext))
                        }
                    }
                    .toList
                readPathList(baseDir, adocPaths, Nil).map(DocumentTree.fromDocuments)
            catch
                case e: Exception =>
                    Left(PipelineError.IOError(Option(e.getMessage).getOrElse("walk failed"), Some(e)))
            finally stream.close()

    private def readSingleFile(file: Path): Either[PipelineError, DocumentTree] =
        val docPath   = DocumentPath.fromString(file.getFileName.toString)
        val parentDir = file.getParent
        if parentDir == null then Left(PipelineError.IOError(s"File has no parent: $file", None))
        else
            readString(file).flatMap { rawContent =>
                parseCstOrAbort(rawContent, docPath).flatMap { cst =>
                    IncludeResolver.resolve(cst, parentDir).map { resolved =>
                        DocumentTree.single(docPath, CstLowering.toAst(resolved))
                    }
                }
            }

    private def parseCstOrAbort(content: String, docPath: DocumentPath): Either[PipelineError, CstDocument] =
        Ascribe.parseCst(content) match
            case Success(cst) => Right(cst)
            case Failure(msg) =>
                Left(PipelineError.ParseError(msg.toString, Some(docPath)))

    private def readPathList(
        baseDir: Path,
        paths: List[Path],
        acc: List[(DocumentPath, Document)]
    ): Either[PipelineError, List[(DocumentPath, Document)]] =
        paths match
            case Nil => Right(acc.reverse)
            case head :: tail =>
                val relativePath = baseDir.relativize(head).toString
                val docPath      = DocumentPath.fromString(relativePath)
                val parentDir    = head.getParent
                if parentDir == null then Left(PipelineError.IOError(s"Path has no parent: $head", None))
                else
                    readString(head).flatMap { rawContent =>
                        parseCstOrAbort(rawContent, docPath).flatMap { cst =>
                            IncludeResolver.resolve(cst, parentDir).flatMap { resolved =>
                                readPathList(baseDir, tail, (docPath, CstLowering.toAst(resolved)) :: acc)
                            }
                        }
                    }

    private def readString(path: Path): Either[PipelineError, String] =
        try Right(Files.readString(path))
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("read failed"), Some(e)))
