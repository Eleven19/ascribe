package io.eleven19.ascribe.pipeline.markdown

import io.eleven19.ascribe.ast.Document
import zio.blocks.docs.{Doc, Parser}

/** GitHub-flavored Markdown via zio-blocks-docs (best-effort; lossy vs full AsciiDoc). */
object GfmMarkdown:

    def fromString(markdown: String): Either[String, Doc] =
        Parser.parse(markdown) match
            case Left(err) => Left(err.toString)
            case Right(d)  => Right(d)

    /** Placeholder: map AST → Markdown string; expand with real Doc emission. */
    def toMarkdownString(document: Document): String =
        s"<!-- ascribe-pipeline-markdown: stub; document blocks=${document.blocks.size} -->\n"
