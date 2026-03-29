package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.pipeline.core.PipelineError
import zio.test.*
import parsley.Success

/** Include depth limits, bad included content, and attribute forms. */
object IncludeResolverEdgeSpec extends ZIOSpecDefault:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    def spec = suite("IncludeResolver (Ox) edge cases")(
        test("maxDepth 0 fails on any include") {
            val tmp = os.temp.dir()
            try
                os.write(tmp / "part.adoc", "X.\n", createFolders = true)
                val source = "include::part.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp, maxDepth = 0)
                assertTrue(r.isLeft)
            finally cleanup(tmp)
        },
        test("fails when included file does not parse") {
            val tmp = os.temp.dir()
            try
                os.write(tmp / "bad.adoc", "[this is not valid adoc\n", createFolders = true)
                val source = "include::bad.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp)
                assertTrue(r.isLeft)
            finally cleanup(tmp)
        },
        test("optional include via named opts attribute skips missing file") {
            val tmp = os.temp.dir()
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
            finally cleanup(tmp)
        },
        test("depth limit error mentions include target (nested chain)") {
            val tmp = os.temp.dir()
            try
                os.write(tmp / "c.adoc", "Deep.\n", createFolders = true)
                os.write(tmp / "b.adoc", "include::c.adoc[]\n", createFolders = true)
                os.write(tmp / "a.adoc", "include::b.adoc[]\n", createFolders = true)
                val source = "include::a.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmp, maxDepth = 2) match
                    case Left(PipelineError.ParseError(msg, _)) =>
                        assertTrue(msg.contains("c.adoc"), msg.contains("limit"))
                    case _ => assertTrue(false)
            finally cleanup(tmp)
        }
    )
