package io.eleven19.ascribe.cst

import parsley.{Failure, Success}
import zio.test.*
import io.eleven19.ascribe.Ascribe

object CstRendererSpec extends ZIOSpecDefault:

    /** Parse, render, re-parse and compare the re-parsed CST structure. */
    private def roundtrip(source: String): zio.test.TestResult =
        Ascribe.parseCst(source) match
            case Success(cst) =>
                val rendered = CstRenderer.render(cst)
                Ascribe.parseCst(rendered) match
                    case Success(cst2) =>
                        assertTrue(cst.content.length == cst2.content.length)
                    case Failure(msg) =>
                        assertTrue(s"Re-parse failed: $msg, rendered was: $rendered" == "")
            case Failure(msg) => assertTrue(s"Initial parse failed: $msg" == "")

    def spec = suite("CstRenderer")(
        test("empty document roundtrips") {
            roundtrip("")
        },
        test("paragraph roundtrips") {
            roundtrip("Hello world.\n")
        },
        test("heading roundtrips") {
            roundtrip("== Section Title\n")
        },
        test("blank lines preserved in roundtrip") {
            roundtrip("Para one.\n\nPara two.\n")
        },
        test("line comment roundtrips") {
            roundtrip("// This is a comment\nPara.\n")
        },
        test("include directive roundtrips") {
            roundtrip("include::file.adoc[]\n")
        },
        test("attribute entry roundtrips") {
            roundtrip(":my-attr: value\nPara.\n")
        },
        test("render produces correct text for heading") {
            Ascribe.parseCst("== My Section\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("== My Section"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces correct text for line comment") {
            Ascribe.parseCst("// my comment\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("// my comment"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces correct text for include") {
            Ascribe.parseCst("include::file.adoc[]\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("include::file.adoc"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces :!name: for unset entry") {
            Ascribe.parseCst(":!my-attr:\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains(":!my-attr:"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("attribute ref roundtrips") {
            roundtrip("{version} text\n")
        },
        test("render produces correct text for attribute ref") {
            Ascribe.parseCst("Release {version}.\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("{version}"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        }
    )
