package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
import io.eleven19.ascribe.cst.{CstDocument, CstLowering}
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.either
import ox.either.ok
import ox.supervised
import parsley.{Failure, Success}

import scala.collection.mutable

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
                either:
                    val adocPaths = os.walk(baseDir).filter { p =>
                        os.isFile(p) && {
                            val name = p.last
                            adocExtensions.exists(ext => name.endsWith(ext))
                        }
                    }
                    val pairs = readPathList(baseDir, adocPaths.toList).ok()
                    DocumentTree.fromDocuments(pairs)
            catch
                case e: Exception =>
                    Left(PipelineError.IOError(Option(e.getMessage).getOrElse("walk failed"), Some(e)))

    private def readSingleFile(file: os.Path): Either[PipelineError, DocumentTree] =
        either:
            val docPath   = DocumentPath.fromString(file.last)
            val parentDir = file / os.up
            val rawContent = readString(file).ok()
            val cst        = parseCstOrAbort(rawContent, docPath).ok()
            val resolved   = IncludeResolver.resolve(cst, parentDir).ok()
            DocumentTree.single(docPath, CstLowering.toAst(resolved))

    private def parseCstOrAbort(content: String, docPath: DocumentPath): Either[PipelineError, CstDocument] =
        Ascribe.parseCst(content) match
            case Success(cst) => Right(cst)
            case Failure(msg) =>
                Left(PipelineError.ParseError(msg.toString, Some(docPath)))

    private def readPathList(
        baseDir: os.Path,
        paths: List[os.Path]
    ): Either[PipelineError, List[(DocumentPath, Document)]] =
        either:
            val buf = mutable.ListBuffer.empty[(DocumentPath, Document)]
            for head <- paths do
                val relativePath = head.relativeTo(baseDir).toString
                val docPath      = DocumentPath.fromString(relativePath)
                val parentDir    = head / os.up
                val rawContent   = readString(head).ok()
                val cst          = parseCstOrAbort(rawContent, docPath).ok()
                val resolved     = IncludeResolver.resolve(cst, parentDir).ok()
                buf += ((docPath, CstLowering.toAst(resolved)))
            buf.toList

    private def readString(path: os.Path): Either[PipelineError, String] =
        try Right(os.read(path))
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("read failed"), Some(e)))
