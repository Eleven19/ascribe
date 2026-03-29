package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
import io.eleven19.ascribe.cst.{CstDocument, CstLowering}
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.supervised
import parsley.{Failure, Success}

/** File-backed [[Source]] using [[https://github.com/com-lihaoyi/os-lib os-lib]] inside an Ox [[supervised]] scope
  * (mirrors Kyo [[io.eleven19.ascribe.pipeline.FileSource]]).
  */
object FileSource:

    private val adocExtensions = Set(".adoc", ".asciidoc", ".ad", ".asc")

    def fromDirectory(dir: os.Path): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                supervised:
                    readDirectory(dir)

    def fromFile(file: os.Path): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                supervised:
                    readSingleFile(file)

    private def readDirectory(baseDir: os.Path): Either[PipelineError, DocumentTree] =
        if !os.isDir(baseDir) then Left(PipelineError.IOError(s"Not a directory: $baseDir", None))
        else
            try
                val adocPaths = os.walk(baseDir).filter { p =>
                    os.isFile(p) && {
                        val name = p.last
                        adocExtensions.exists(ext => name.endsWith(ext))
                    }
                }
                readPathList(baseDir, adocPaths.toList, Nil).map(DocumentTree.fromDocuments)
            catch
                case e: Exception =>
                    Left(PipelineError.IOError(Option(e.getMessage).getOrElse("walk failed"), Some(e)))

    private def readSingleFile(file: os.Path): Either[PipelineError, DocumentTree] =
        val docPath   = DocumentPath.fromString(file.last)
        val parentDir = file / os.up
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
        baseDir: os.Path,
        paths: List[os.Path],
        acc: List[(DocumentPath, Document)]
    ): Either[PipelineError, List[(DocumentPath, Document)]] =
        paths match
            case Nil => Right(acc.reverse)
            case head :: tail =>
                val relativePath = head.relativeTo(baseDir).toString
                val docPath      = DocumentPath.fromString(relativePath)
                val parentDir    = head / os.up
                readString(head).flatMap { rawContent =>
                    parseCstOrAbort(rawContent, docPath).flatMap { cst =>
                        IncludeResolver.resolve(cst, parentDir).flatMap { resolved =>
                            readPathList(baseDir, tail, (docPath, CstLowering.toAst(resolved)) :: acc)
                        }
                    }
                }

    private def readString(path: os.Path): Either[PipelineError, String] =
        try Right(os.read(path))
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("read failed"), Some(e)))
