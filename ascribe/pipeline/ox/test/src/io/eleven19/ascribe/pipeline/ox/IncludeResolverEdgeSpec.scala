package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.test.*
import parsley.Success

/** Include depth limits, bad included content, and attribute forms. */
class IncludeResolverEdgeSpec extends Test[Any]:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    "IncludeResolver (Ox) edge cases" - {
        "maxDepth 0 fails on any include" in {
            val tmp = os.temp.dir()
            try
                os.write(tmp / "part.adoc", "X.\n", createFolders = true)
                val source = "include::part.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp, maxDepth = 0)
                assert(r.isLeft)
            finally cleanup(tmp)
        }
        "fails when included file does not parse" in {
            val tmp = os.temp.dir()
            try
                os.write(tmp / "bad.adoc", "[this is not valid adoc\n", createFolders = true)
                val source = "include::bad.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val r = IncludeResolver.resolve(cst, tmp)
                assert(r.isLeft)
            finally cleanup(tmp)
        }
        "optional include via named opts attribute skips missing file" in {
            val tmp = os.temp.dir()
            try
                val source = "Start.\n\ninclude::nope.adoc[opts=optional]\n\nEnd.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmp) match
                    case Right(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assert(texts.exists(_.contains("Start.")))
                        assert(texts.exists(_.contains("End.")))
                        assert(!resolved.content.exists(_.isInstanceOf[CstInclude]))
                    case Left(_) => assert(false)
            finally cleanup(tmp)
        }
        "depth limit error mentions include target (nested chain)" in {
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
                        assert(msg.contains("c.adoc"))
                        assert(msg.contains("limit"))
                    case _ => assert(false)
            finally cleanup(tmp)
        }
    }
