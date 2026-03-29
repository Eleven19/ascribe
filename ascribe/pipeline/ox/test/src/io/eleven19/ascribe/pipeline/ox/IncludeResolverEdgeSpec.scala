package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.pipeline.core.PipelineError
import zio.test.*
import parsley.Success

import java.nio.file.Files

/** Include depth limits, bad included content, and attribute forms. */
object IncludeResolverEdgeSpec extends ZIOSpecDefault:

    private def deleteRecursively(root: java.nio.file.Path): Unit =
        if Files.isDirectory(root) then
            val stream = Files.list(root)
            try stream.forEach(deleteRecursively(_))
            finally stream.close()
        Files.deleteIfExists(root): Unit

    def spec = suite("IncludeResolver (Ox) edge cases")(
        test("maxDepth 0 fails on any include") {
            val tmp = Files.createTempDirectory("ascribe-inc-depth")
            try
                Files.writeString(tmp.resolve("part.adoc"), "X.\n")
                val source = "include::part.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp, maxDepth = 0)
                assertTrue(r.isLeft)
            finally deleteRecursively(tmp)
        },
        test("fails when included file does not parse") {
            val tmp = Files.createTempDirectory("ascribe-inc-bad")
            try
                Files.writeString(tmp.resolve("bad.adoc"), "[this is not valid adoc\n")
                val source = "include::bad.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp)
                assertTrue(r.isLeft)
            finally deleteRecursively(tmp)
        },
        test("optional include via named opts attribute skips missing file") {
            val tmp = Files.createTempDirectory("ascribe-inc-named")
            try
                val source = "Start.\n\ninclude::nope.adoc[opts=optional]\n\nEnd.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmp) match
                    case Right(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assertTrue(
                            texts.exists(_.contains("Start.")),
                            texts.exists(_.contains("End.")),
                            !resolved.content.exists(_.isInstanceOf[CstInclude])
                        )
                    case Left(_) => assertTrue(false)
            finally deleteRecursively(tmp)
        },
        test("depth limit error mentions include target (nested chain)") {
            val tmp = Files.createTempDirectory("ascribe-inc-msg")
            try
                Files.writeString(tmp.resolve("c.adoc"), "Deep.\n")
                Files.writeString(tmp.resolve("b.adoc"), "include::c.adoc[]\n")
                Files.writeString(tmp.resolve("a.adoc"), "include::b.adoc[]\n")
                val source = "include::a.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmp, maxDepth = 2) match
                    case Left(PipelineError.ParseError(msg, _)) =>
                        assertTrue(msg.contains("c.adoc"), msg.contains("limit"))
                    case _ => assertTrue(false)
            finally deleteRecursively(tmp)
        }
    )
