package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.*

trait Source[S]:
    def read: DocumentTree < S

object Source:

    def fromString(content: String): Source[Abort[PipelineError]] =
        fromString(content, DocumentPath("document.adoc"))

    def fromString(content: String, path: DocumentPath): Source[Abort[PipelineError]] =
        new Source[Abort[PipelineError]]:
            def read: DocumentTree < Abort[PipelineError] =
                Ascribe.parse(content) match
                    case parsley.Success(doc) => DocumentTree.single(path, doc)
                    case parsley.Failure(msg) =>
                        Abort.fail(PipelineError.ParseError(msg.toString, Some(path)))

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

    def fromDocument(document: Document): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = DocumentTree.single(document)

    def fromDocument(document: Document, path: DocumentPath): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = DocumentTree.single(path, document)

    def fromTree(tree: DocumentTree): Source[Any] =
        new Source[Any]:
            def read: DocumentTree < Any = tree
