package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import kyo.test.*
import scala.language.implicitConversions

class AsciiDocRendererSpec extends Test[Any]:

    private def renderDoc(doc: Document): String =
        AsciiDocRenderer.render(doc)

    private def roundtrip(input: String): String =
        Ascribe.parse(input) match
            case parsley.Success(doc) => renderDoc(doc)
            case parsley.Failure(msg) => throw new AssertionError(s"Parse failed: $msg")

    "AsciiDocRenderer (Ox)" - {
        "renders plain paragraph" in
            assert(renderDoc(document(paragraph("Hello world."))) == "Hello world.\n")
        "renders heading" in
            assert(renderDoc(document(heading(1, text("Title")))) == "== Title\n")
        "renders bold inline" in {
            val rendered = AsciiDocRenderer.renderInline(Bold(scala.List(Text("bold")(Span.unknown)))(Span.unknown))
            assert(rendered == "**bold**")
        }
        "renders constrained bold inline" in {
            val rendered = AsciiDocRenderer.renderInline(
                ConstrainedBold(scala.List(Text("bold")(Span.unknown)))(Span.unknown)
            )
            assert(rendered == "*bold*")
        }
        "renders italic inline" in {
            val rendered = AsciiDocRenderer.renderInline(Italic(scala.List(Text("em")(Span.unknown)))(Span.unknown))
            assert(rendered == "__em__")
        }
        "renders monospace inline" in {
            val rendered = AsciiDocRenderer.renderInline(Mono(scala.List(Text("code")(Span.unknown)))(Span.unknown))
            assert(rendered == "``code``")
        }
        "renders unordered list" in {
            val rendered = renderDoc(
                document(
                    unorderedList(listItem(text("first")), listItem(text("second")))
                )
            )
            assert(rendered.contains("* first"))
            assert(rendered.contains("* second"))
        }
        "renders ordered list" in {
            val rendered = renderDoc(
                document(
                    orderedList(listItem(text("one")), listItem(text("two")))
                )
            )
            assert(rendered.contains(". one"))
            assert(rendered.contains(". two"))
        }
        "renders listing block with delimiter" in {
            val rendered = renderDoc(document(listingBlock("----", "puts 'hello'")))
            assert(rendered.contains("----\nputs 'hello'\n----"))
        }
        "roundtrip: single paragraph" in
            assert(roundtrip("Hello world.\n") == "Hello world.\n")
        "roundtrip: heading and paragraph" in
            assert(roundtrip("== Title\n\nA paragraph.\n") == "== Title\n\nA paragraph.\n")
        "roundtrip: unordered list" in
            assert(roundtrip("* first\n* second\n") == "* first\n* second\n")
        "roundtrip: ordered list" in
            assert(roundtrip(". one\n. two\n") == ". one\n. two\n")
        "roundtrip: listing block" in
            assert(roundtrip("----\nputs 'hello'\n----\n") == "----\nputs 'hello'\n----\n")
        "renders NOTE admonition paragraph" in {
            val para     = Paragraph(List(Text("Watch out.")(Span.unknown)))(Span.unknown)
            val adm      = Admonition(AdmonitionKind.Note, List(para))(Span.unknown)
            val doc      = Document(None, List(adm))(Span.unknown)
            val rendered = renderDoc(doc)
            assert(rendered.contains("NOTE: Watch out."))
        }
        "renders WARNING admonition paragraph" in {
            val para     = Paragraph(List(Text("Danger!")(Span.unknown)))(Span.unknown)
            val adm      = Admonition(AdmonitionKind.Warning, List(para))(Span.unknown)
            val doc      = Document(None, List(adm))(Span.unknown)
            val rendered = renderDoc(doc)
            assert(rendered.contains("WARNING: Danger!"))
        }
        "link rendering" - {
            "renders autolink as bare URL" in {
                val rendered = AsciiDocRenderer.renderInline(autoLink("https://example.com"))
                assert(rendered == "https://example.com")
            }
            "renders link macro as link:target[text]" in {
                val rendered = AsciiDocRenderer.renderInline(link("report.pdf", text("Get Report")))
                assert(rendered == "link:report.pdf[Get Report]")
            }
            "renders mailto macro as mailto:addr[text]" in {
                val rendered = AsciiDocRenderer.renderInline(mailtoLink("user@example.com", text("Email me")))
                assert(rendered == "mailto:user@example.com[Email me]")
            }
            "renders URL macro as url[text]" in {
                val rendered = AsciiDocRenderer.renderInline(urlLink("https", "https://example.com", text("click")))
                assert(rendered == "https://example.com[click]")
            }
            "renders link macro with empty text as link:target[]" in {
                val rendered = AsciiDocRenderer.renderInline(link("report.pdf"))
                assert(rendered == "link:report.pdf[]")
            }
            "roundtrip: bare autolink" in
                assert(roundtrip("Visit https://example.com today.\n") == "Visit https://example.com today.\n")
            "roundtrip: link macro" in
                assert(roundtrip("See link:report.pdf[Get Report].\n") == "See link:report.pdf[Get Report].\n")
            "roundtrip: mailto macro" in
                assert(
                    roundtrip("Contact mailto:user@example.com[Email me].\n") ==
                        "Contact mailto:user@example.com[Email me].\n"
                )
            "roundtrip: URL macro" in
                assert(
                    roundtrip("Go to https://example.com[the site].\n") == "Go to https://example.com[the site].\n"
                )
        }
    }
