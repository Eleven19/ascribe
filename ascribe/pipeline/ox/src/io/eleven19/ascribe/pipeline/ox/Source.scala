package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import io.eleven19.ascribe.pipeline.core.PipelineError

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
                val parsed = docs.toList.map { (path, content) =>
                    Ascribe.parse(content) match
                        case parsley.Success(doc) => Right((path, doc))
                        case parsley.Failure(msg) =>
                            Left(PipelineError.ParseError(msg.toString, Some(path)))
                }
                parsed.collectFirst { case Left(err) => err } match
                    case Some(err) => Left(err)
                    case None =>
                        Right(DocumentTree.fromDocuments(parsed.collect { case Right(pair) => pair }))

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
