package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.either
import ox.either.fail

import scala.collection.mutable

trait Source:
    def read: Either[PipelineError, DocumentTree]

object Source:

    def fromString(content: String): Source =
        fromString(content, DocumentPath("document.adoc"))

    def fromString(content: String, path: DocumentPath): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                Ascribe.parse(content) match
                    case parsley.Success(doc) => Right(DocumentTree.single(path, doc))
                    case parsley.Failure(msg) =>
                        Left(PipelineError.ParseError(msg.toString, Some(path)))

    def fromStrings(docs: (DocumentPath, String)*): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                either:
                    val buf = mutable.ListBuffer.empty[(DocumentPath, Document)]
                    for (path, content) <- docs do
                        Ascribe.parse(content) match
                            case parsley.Success(doc) => buf += ((path, doc))
                            case parsley.Failure(msg) =>
                                fail(PipelineError.ParseError(msg.toString, Some(path)))()
                    DocumentTree.fromDocuments(buf.toList)

    def fromDocument(document: Document): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                Right(DocumentTree.single(document))

    def fromDocument(document: Document, path: DocumentPath): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] =
                Right(DocumentTree.single(path, document))

    def fromTree(tree: DocumentTree): Source =
        new Source:
            def read: Either[PipelineError, DocumentTree] = Right(tree)
