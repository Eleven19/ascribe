package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.*

/** DSL for constructing pipeline components with minimal boilerplate.
  *
  * {{{
  * import io.eleven19.ascribe.pipeline.dsl.*
  *
  * val pipeline = Pipeline.fromString("= Hello\n\nWorld.\n")
  *     .rewrite(removeComments)
  *     .rewrite(replaceBlock {
  *         case p: Paragraph => RewriteAction.Replace(p)
  *     })
  * }}}
  */
object dsl:
    export RewriteAction.{Replace, Remove, Retain}

    /** Create a block rewrite rule from a partial function (pure). */
    def replaceBlock(pf: PartialFunction[Block, RewriteAction[Block]]): RewriteRule[Any] =
        RewriteRule.forBlocks(pf)

    /** Create an inline rewrite rule from a partial function (pure). */
    def replaceInline(pf: PartialFunction[Inline, RewriteAction[Inline]]): RewriteRule[Any] =
        RewriteRule.forInlines(pf)

    /** A built-in rule that removes all Comment blocks. */
    val removeComments: RewriteRule[Any] =
        RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }

    /** A built-in rule that strips all inline formatting, leaving plain text. */
    val stripFormatting: RewriteRule[Any] =
        RewriteRule.forInlines {
            case Bold(content)              => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case ConstrainedBold(content)   => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case Italic(content)            => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case Mono(content)              => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case ConstrainedItalic(content) => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
            case ConstrainedMono(content)   => RewriteAction.Replace(Text(flattenInlines(content))(Span.unknown))
        }

    private def flattenInlines(inlines: List[Inline]): String =
        inlines.map {
            case Text(content)              => content
            case Bold(content)              => flattenInlines(content)
            case ConstrainedBold(content)   => flattenInlines(content)
            case Italic(content)            => flattenInlines(content)
            case Mono(content)              => flattenInlines(content)
            case ConstrainedItalic(content) => flattenInlines(content)
            case ConstrainedMono(content)   => flattenInlines(content)
            case Link(_, target, text)      => if text.nonEmpty then flattenInlines(text) else target
        }.mkString
