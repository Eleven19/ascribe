package io.github.eleven19.ascribe

import io.cucumber.scala.{EN, ScalaDsl}
import parsley.{Failure, Success}

import io.github.eleven19.ascribe.ast.*

/** Cucumber step definitions for the AsciiDoc parser integration tests. */
class AsciiDocParserSteps extends ScalaDsl with EN:

    private var source: String           = ""
    private var result: Option[Document] = None

    // -----------------------------------------------------------------------
    // Given
    // -----------------------------------------------------------------------

    Given("""^an AsciiDoc source of "(.+)"$""") { (raw: String) =>
        source = raw + "\n"
        result = None
    }

    Given("""^the following AsciiDoc source:$""") { (docString: String) =>
        // Trim leading/trailing blank lines introduced by the triple-quote block,
        // then ensure a trailing newline.
        source = docString.trim + "\n"
        result = None
    }

    // -----------------------------------------------------------------------
    // When
    // -----------------------------------------------------------------------

    When("""^the source is parsed$""") { () =>
        Ascribe.parse(source) match
            case Success(doc) => result = Some(doc)
            case Failure(message) =>
                throw new AssertionError(s"Parse failed for input:\n$source\nError: $message")
    }

    // -----------------------------------------------------------------------
    // Then – document-level assertions
    // -----------------------------------------------------------------------

    Then("""^the document contains (\d+) blocks?$""") { (n: Int) =>
        val doc = result.getOrElse(throw new AssertionError("No parsed document available"))
        assert(
            doc.blocks.length == n,
            s"Expected $n block(s) but got ${doc.blocks.length}: ${doc.blocks}"
        )
    }

    // -----------------------------------------------------------------------
    // Then – heading assertions
    // -----------------------------------------------------------------------

    Then("""^block (\d+) is a level (\d+) heading with title "(.+)"$""") { (idx: Int, level: Int, title: String) =>
        val doc   = result.getOrElse(throw new AssertionError("No parsed document available"))
        val block = doc.blocks(idx - 1)
        block match
            case Heading(l, inlines) =>
                assert(l == level, s"Expected heading level $level but got $l")
                val text = inlinesToText(inlines)
                assert(text == title, s"Expected title '$title' but got '$text'")
            case other =>
                throw new AssertionError(s"Expected Heading at block $idx but got: $other")
    }

    // -----------------------------------------------------------------------
    // Then – paragraph assertions
    // -----------------------------------------------------------------------

    Then("""^block (\d+) is a paragraph containing the text "(.+)"$""") { (idx: Int, expected: String) =>
        val doc   = result.getOrElse(throw new AssertionError("No parsed document available"))
        val block = doc.blocks(idx - 1)
        block match
            case Paragraph(inlines) =>
                val text = inlinesToText(inlines)
                assert(
                    text.contains(expected),
                    s"Paragraph text '$text' does not contain '$expected'"
                )
            case other =>
                throw new AssertionError(s"Expected Paragraph at block $idx but got: $other")
    }

    Then("""^block (\d+) is a paragraph containing bold text "(.+)"$""") { (idx: Int, expected: String) =>
        assertContainsInlineSpan(idx, "bold", expected) { case Bold(cs) =>
            inlinesToText(cs)
        }
    }

    Then("""^block (\d+) is a paragraph containing italic text "(.+)"$""") { (idx: Int, expected: String) =>
        assertContainsInlineSpan(idx, "italic", expected) { case Italic(cs) =>
            inlinesToText(cs)
        }
    }

    Then("""^block (\d+) is a paragraph containing monospace text "(.+)"$""") { (idx: Int, expected: String) =>
        assertContainsInlineSpan(idx, "monospace", expected) { case Mono(cs) =>
            inlinesToText(cs)
        }
    }

    // -----------------------------------------------------------------------
    // Then – list assertions
    // -----------------------------------------------------------------------

    Then("""^block (\d+) is an unordered list with (\d+) items?$""") { (idx: Int, n: Int) =>
        val doc   = result.getOrElse(throw new AssertionError("No parsed document available"))
        val block = doc.blocks(idx - 1)
        block match
            case UnorderedList(items) =>
                assert(
                    items.length == n,
                    s"Expected $n item(s) but got ${items.length}"
                )
            case other =>
                throw new AssertionError(s"Expected UnorderedList at block $idx but got: $other")
    }

    Then("""^block (\d+) is an ordered list with (\d+) items?$""") { (idx: Int, n: Int) =>
        val doc   = result.getOrElse(throw new AssertionError("No parsed document available"))
        val block = doc.blocks(idx - 1)
        block match
            case OrderedList(items) =>
                assert(
                    items.length == n,
                    s"Expected $n item(s) but got ${items.length}"
                )
            case other =>
                throw new AssertionError(s"Expected OrderedList at block $idx but got: $other")
    }

    Then("""^unordered list item (\d+) contains the text "(.+)"$""") { (itemIdx: Int, expected: String) =>
        val doc = result.getOrElse(throw new AssertionError("No parsed document available"))
        // find the first UnorderedList block
        val listBlock = doc.blocks
            .collectFirst { case b: UnorderedList => b }
            .getOrElse(throw new AssertionError("No UnorderedList found in document"))
        val item = listBlock.items(itemIdx - 1)
        val text = inlinesToText(item.content)
        assert(text == expected, s"Expected item text '$expected' but got '$text'")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Flattens an [[InlineContent]] list into a plain string for assertion comparison. */
    private def inlinesToText(inlines: List[Inline]): String =
        inlines.map {
            case Text(s)    => s
            case Bold(cs)   => inlinesToText(cs)
            case Italic(cs) => inlinesToText(cs)
            case Mono(cs)   => inlinesToText(cs)
        }.mkString

    /** Generic helper to assert that block `idx` is a Paragraph containing a span whose inner text equals `expected`.
      * The partial function `extract` extracts the inner text from the matching [[Inline]] variant.
      */
    private def assertContainsInlineSpan(idx: Int, kind: String, expected: String)(
        extract: PartialFunction[Inline, String]
    ): Unit =
        val doc   = result.getOrElse(throw new AssertionError("No parsed document available"))
        val block = doc.blocks(idx - 1)
        block match
            case Paragraph(inlines) =>
                val found = inlines.collectFirst(extract)
                val text = found.getOrElse(
                    throw new AssertionError(
                        s"No $kind span found in paragraph. Inlines: $inlines"
                    )
                )
                assert(text == expected, s"Expected $kind text '$expected' but got '$text'")
            case other =>
                throw new AssertionError(s"Expected Paragraph at block $idx but got: $other")
