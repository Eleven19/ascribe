package io.eleven19.ascribe

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind, Paragraph, Text}

object AscribeSpec extends ZIOSpecDefault:

    def spec = suite("Ascribe public API")(
        test("parses a simple heading document") {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assertTrue(doc == document(documentHeader(text("Hello World"))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("parses a paragraph") {
            Ascribe.parse("Hello world.\n") match
                case Success(doc) =>
                    assertTrue(doc == document(paragraph(text("Hello world."))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("empty input produces an empty document") {
            Ascribe.parse("") match
                case Success(doc) => assertTrue(doc == document())
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        suite("attribute references")(
            test("resolves {attr} from header via full pipeline") {
                val src = "= Doc\n:version: 2.0\n\nRelease {version}.\n"
                Ascribe.parse(src) match
                    case Success(doc) =>
                        val texts = doc.blocks.flatMap {
                            case Paragraph(content) => content.collect { case Text(c) => c }
                            case _                  => Nil
                        }
                        assertTrue(texts.contains("2.0"))
                    case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
            },
            test("resolves {attr} from body entry") {
                val src = ":greeting: Hello\n\n{greeting} world.\n"
                Ascribe.parse(src) match
                    case Success(doc) =>
                        val texts = doc.blocks.flatMap {
                            case Paragraph(content) => content.collect { case Text(c) => c }
                            case _                  => Nil
                        }
                        assertTrue(texts.contains("Hello"))
                    case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
            }
        ),
        suite("admonition paragraphs")(
            test("NOTE: text parses to ast.Admonition") {
                Ascribe.parse("NOTE: Watch out.\n") match
                    case Success(doc) =>
                        doc.blocks match
                            case List(Admonition(AdmonitionKind.Note, _)) => assertTrue(true)
                            case other => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
            },
            test("all five admonition kinds are parsed") {
                val kinds = List(
                    "NOTE: text\n"      -> AdmonitionKind.Note,
                    "TIP: text\n"       -> AdmonitionKind.Tip,
                    "IMPORTANT: text\n" -> AdmonitionKind.Important,
                    "CAUTION: text\n"   -> AdmonitionKind.Caution,
                    "WARNING: text\n"   -> AdmonitionKind.Warning
                )
                val results = kinds.map { case (src, expected) =>
                    Ascribe.parse(src) match
                        case Success(doc) =>
                            doc.blocks match
                                case List(Admonition(kind, _)) => kind == expected
                                case _ => false
                        case _ => false
                }
                assertTrue(results.forall(identity))
            }
        )
    )
