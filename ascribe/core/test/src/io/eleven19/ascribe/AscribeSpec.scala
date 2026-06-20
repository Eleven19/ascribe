package io.eleven19.ascribe

import parsley.{Failure, Success}
import kyo.test.*

import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind, Paragraph, Text}

class AscribeSpec extends Test[Any]:

    "Ascribe public API" - {
        "parses a simple heading document" in {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assert(doc == document(documentHeader(text("Hello World"))))
                case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
        }
        "parses a paragraph" in {
            Ascribe.parse("Hello world.\n") match
                case Success(doc) =>
                    assert(doc == document(paragraph(text("Hello world."))))
                case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
        }
        "empty input produces an empty document" in {
            Ascribe.parse("") match
                case Success(doc) => assert(doc == document())
                case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
        }
        "attribute references" - {
            "resolves {attr} from header via full pipeline" in {
                val src = "= Doc\n:version: 2.0\n\nRelease {version}.\n"
                Ascribe.parse(src) match
                    case Success(doc) =>
                        val texts = doc.blocks.flatMap {
                            case Paragraph(content, _, _) => content.collect { case Text(c) => c }
                            case _                        => Nil
                        }
                        assert(texts.contains("2.0"))
                    case Failure(msg) => assert(s"Parse failed: $msg" == "")
            }
            "resolves {attr} from body entry" in {
                val src = ":greeting: Hello\n\n{greeting} world.\n"
                Ascribe.parse(src) match
                    case Success(doc) =>
                        val texts = doc.blocks.flatMap {
                            case Paragraph(content, _, _) => content.collect { case Text(c) => c }
                            case _                        => Nil
                        }
                        assert(texts.contains("Hello"))
                    case Failure(msg) => assert(s"Parse failed: $msg" == "")
            }
        }
        "admonition paragraphs" - {
            "NOTE: text parses to ast.Admonition" in {
                Ascribe.parse("NOTE: Watch out.\n") match
                    case Success(doc) =>
                        doc.blocks match
                            case List(Admonition(AdmonitionKind.Note, _)) => assert(true)
                            case other                                    => assert(s"unexpected: $other" == "")
                    case Failure(msg) => assert(s"Parse failed: $msg" == "")
            }
            "all five admonition kinds are parsed" in {
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
                                case _                         => false
                        case _ => false
                }
                assert(results.forall(identity))
            }
        }
    }
