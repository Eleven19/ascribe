package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import kyo.{<, Sync, Abort, Path, Chunk}

/** A Source that reads `.adoc` files from a directory on the file system.
  *
  * Files are discovered recursively via `Path.walk`. Each `.adoc` file is parsed into a Document and placed in the tree
  * with its path relative to the source directory.
  */
object FileSource:

    private val adocExtensions = Set(".adoc", ".asciidoc", ".ad", ".asc")

    /** Create a Source that reads all `.adoc` files from a directory. */
    def fromDirectory(dir: Path): Source[Sync & Abort[PipelineError]] =
        new Source[Sync & Abort[PipelineError]]:
            def read: DocumentTree < (Sync & Abort[PipelineError]) =
                dir.walk.run.map { paths =>
                    val adocPaths = paths.toList.filter { p =>
                        val name = p.toJava.getFileName.toString
                        adocExtensions.exists(ext => name.endsWith(ext))
                    }
                    readPaths(dir, adocPaths)
                }

    /** Create a Source that reads a single `.adoc` file, resolving include directives. */
    def fromFile(file: Path): Source[Sync & Abort[PipelineError]] =
        new Source[Sync & Abort[PipelineError]]:
            def read: DocumentTree < (Sync & Abort[PipelineError]) =
                val docPath   = DocumentPath.fromString(file.toJava.getFileName.toString)
                val parentDir = Path(file.toJava.getParent.toString)
                file.read.map { rawContent =>
                    IncludeProcessor.process(rawContent, parentDir).map { content =>
                        parseOrAbort(content, docPath).map(doc => DocumentTree.single(docPath, doc))
                    }
                }

    private def parseOrAbort(content: String, docPath: DocumentPath): Document < Abort[PipelineError] =
        Ascribe.parse(content) match
            case parsley.Success(doc) => doc
            case parsley.Failure(msg) =>
                Abort.fail(PipelineError.ParseError(msg.toString, Some(docPath)))

    private def readPaths(
        baseDir: Path,
        paths: List[Path]
    ): DocumentTree < (Sync & Abort[PipelineError]) =
        readPathList(baseDir, paths, Nil).map { docs =>
            DocumentTree.fromDocuments(docs)
        }

    private def readPathList(
        baseDir: Path,
        paths: List[Path],
        acc: List[(DocumentPath, Document)]
    ): List[(DocumentPath, Document)] < (Sync & Abort[PipelineError]) =
        paths match
            case Nil => acc.reverse
            case head :: tail =>
                val relativePath = baseDir.toJava.relativize(head.toJava).toString
                val docPath      = DocumentPath.fromString(relativePath)
                val parentDir    = Path(head.toJava.getParent.toString)
                head.read.map { rawContent =>
                    IncludeProcessor.process(rawContent, parentDir).map { content =>
                        parseOrAbort(content, docPath).map { doc =>
                            readPathList(baseDir, tail, (docPath, doc) :: acc)
                        }
                    }
                }
