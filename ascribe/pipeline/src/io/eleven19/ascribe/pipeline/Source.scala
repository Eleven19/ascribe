package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import kyo.*

/** A source of AsciiDoc documents.
  *
  * Sources read documents from some backing store (string, file system, etc.) and produce a DocumentTree. The effect
  * type `S` captures what effects are needed to read.
  */
trait Source[S]:
    /** Read documents from this source into a DocumentTree. */
    def read: DocumentTree < S

object Source:

    /** Create a source from a single AsciiDoc string. */
    def fromString(content: String): Source[Abort[PipelineError]] =
        fromString(content, DocumentPath("document.adoc"))

    /** Create a source from a single AsciiDoc string with a given path. */
    def fromString(content: String, path: DocumentPath): Source[Abort[PipelineError]] =
        new Source[Abort[PipelineError]]:
            def read: DocumentTree < Abort[PipelineError] =
                Ascribe.parse(content) match
                    case parsley.Success(doc) => DocumentTree.single(path, doc)
                    case parsley.Failure(msg) =>
                        Abort.fail(PipelineError.ParseError(msg.toString, Some(path)))

    /** Create a source from multiple named AsciiDoc strings. */
    def fromStrings(docs: (DocumentPath, String)*): Source[Abort[PipelineError]] =
        new Source[Abort[PipelineError]]:
            def read: DocumentTree < Abort[PipelineError] =
                val parsed = docs.toList.map { (path, content) =>
                    Ascribe.parse(content) match
                        case parsley.Success(doc) => Right((path, doc))
                        case parsley.Failure(msg) =>
                            Left(PipelineError.ParseError(msg.toString, Some(path)))
                }
                parsed.collectFirst { case Left(err) => err } match
                    case Some(err) => Abort.fail(err)
                    case None =>
                        DocumentTree.fromDocuments(parsed.collect { case Right(pair) => pair })

    /** Create a source from an already-parsed Document. */
    def fromDocument(document: Document): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = DocumentTree.single(document)

    /** Create a source from an already-parsed Document with a path. */
    def fromDocument(document: Document, path: DocumentPath): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = DocumentTree.single(path, document)

    /** Create a source from an existing DocumentTree. */
    def fromTree(tree: DocumentTree): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = tree
