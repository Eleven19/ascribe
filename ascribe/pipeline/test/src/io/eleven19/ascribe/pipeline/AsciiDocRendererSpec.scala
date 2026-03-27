package io.eleven19.ascribe.pipeline

import zio.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import scala.language.implicitConversions
import kyo.<

object AsciiDocRendererSpec extends ZIOSpecDefault:

    /** Evaluate a pure Kyo value (no pending effects). */
    private def runPure(v: String < Any): String = v.asInstanceOf[String]

    private def renderDoc(doc: Document): String = runPure(AsciiDocRenderer.render(doc))

    /** Parse input and render back, returning the rendered string. */
    private def roundtrip(input: String): String =
        Ascribe.parse(input) match
            case parsley.Success(doc) => renderDoc(doc)
            case parsley.Failure(msg) => throw new AssertionError(s"Parse failed: $msg")

    def spec = suite("AsciiDocRenderer")(
        test("renders plain paragraph") {
            assertTrue(renderDoc(document(paragraph("Hello world."))) == "Hello world.\n")
        },
        test("renders heading") {
            assertTrue(renderDoc(document(heading(1, text("Title")))) == "== Title\n")
        },
        test("renders bold inline") {
            val rendered = AsciiDocRenderer.renderInline(Bold(scala.List(Text("bold")(Span.unknown)))(Span.unknown))
            assertTrue(rendered == "**bold**")
        },
        test("renders constrained bold inline") {
            val rendered = AsciiDocRenderer.renderInline(
                ConstrainedBold(scala.List(Text("bold")(Span.unknown)))(Span.unknown)
            )
            assertTrue(rendered == "*bold*")
        },
        test("renders italic inline") {
            val rendered = AsciiDocRenderer.renderInline(Italic(scala.List(Text("em")(Span.unknown)))(Span.unknown))
            assertTrue(rendered == "__em__")
        },
        test("renders monospace inline") {
            val rendered = AsciiDocRenderer.renderInline(Mono(scala.List(Text("code")(Span.unknown)))(Span.unknown))
            assertTrue(rendered == "``code``")
        },
        test("renders unordered list") {
            val rendered = renderDoc(
                document(
                    unorderedList(listItem(text("first")), listItem(text("second")))
                )
            )
            assertTrue(rendered.contains("* first"), rendered.contains("* second"))
        },
        test("renders ordered list") {
            val rendered = renderDoc(
                document(
                    orderedList(listItem(text("one")), listItem(text("two")))
                )
            )
            assertTrue(rendered.contains(". one"), rendered.contains(". two"))
        },
        test("renders listing block with delimiter") {
            val rendered = renderDoc(document(listingBlock("----", "puts 'hello'")))
            assertTrue(rendered.contains("----\nputs 'hello'\n----"))
        },
        test("roundtrip: single paragraph") {
            assertTrue(roundtrip("Hello world.\n") == "Hello world.\n")
        },
        test("roundtrip: heading and paragraph") {
            assertTrue(roundtrip("== Title\n\nA paragraph.\n") == "== Title\n\nA paragraph.\n")
        },
        test("roundtrip: unordered list") {
            assertTrue(roundtrip("* first\n* second\n") == "* first\n* second\n")
        },
        test("roundtrip: ordered list") {
            assertTrue(roundtrip(". one\n. two\n") == ". one\n. two\n")
        },
        test("roundtrip: listing block") {
            assertTrue(roundtrip("----\nputs 'hello'\n----\n") == "----\nputs 'hello'\n----\n")
        },
        test("renders NOTE admonition paragraph") {
            val para     = Paragraph(List(Text("Watch out.")(Span.unknown)))(Span.unknown)
            val adm      = Admonition(AdmonitionKind.Note, List(para))(Span.unknown)
            val doc      = Document(None, List(adm))(Span.unknown)
            val rendered = renderDoc(doc)
            assertTrue(rendered.contains("NOTE: Watch out."))
        },
        test("renders WARNING admonition paragraph") {
            val para     = Paragraph(List(Text("Danger!")(Span.unknown)))(Span.unknown)
            val adm      = Admonition(AdmonitionKind.Warning, List(para))(Span.unknown)
            val doc      = Document(None, List(adm))(Span.unknown)
            val rendered = renderDoc(doc)
            assertTrue(rendered.contains("WARNING: Danger!"))
        }
    )
