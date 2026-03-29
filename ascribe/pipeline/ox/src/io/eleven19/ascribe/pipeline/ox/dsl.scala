package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}

/** DSL for constructing pipeline components with minimal boilerplate. */
object dsl:
    export RewriteAction.{Replace, Remove, Retain}

    def replaceBlock(pf: PartialFunction[Block, RewriteAction[Block]]): RewriteRule =
        RewriteRule.forBlocks(pf)

    def replaceInline(pf: PartialFunction[Inline, RewriteAction[Inline]]): RewriteRule =
        RewriteRule.forInlines(pf)

    val removeComments: RewriteRule =
        RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }

    val stripFormatting: RewriteRule =
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
            case Link(_, target, text, _)   => if text.nonEmpty then flattenInlines(text) else target
        }.mkString
