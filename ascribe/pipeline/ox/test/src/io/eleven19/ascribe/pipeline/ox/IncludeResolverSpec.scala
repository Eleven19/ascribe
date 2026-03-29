package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import zio.test.*
import parsley.Success

object IncludeResolverSpec extends ZIOSpecDefault:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    def spec = suite("IncludeResolver (Ox)")(
        test("resolves basic include directive") {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "partial.adoc", "Included content.\n", createFolders = true)
                val source = "Before.\n\ninclude::partial.adoc[]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmpDir) match
                    case Right(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assertTrue(
                            texts.exists(_.contains("Before.")),
                            texts.exists(_.contains("Included content.")),
                            texts.exists(_.contains("After."))
                        )
                    case Left(_) => assertTrue(false)
            finally cleanup(tmpDir)
        },
        test("fails on missing include file") {
            val tmpDir = os.temp.dir()
            try
                val source = "include::missing.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val result = IncludeResolver.resolve(cst, tmpDir)
                assertTrue(result.isLeft)
            finally cleanup(tmpDir)
        },
        test("optional include silently skips missing file") {
            val tmpDir = os.temp.dir()
            try
                val source = "Before.\n\ninclude::missing.adoc[opts=optional]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmpDir) match
                    case Right(resolved) =>
                        val hasInclude = resolved.content.exists(_.isInstanceOf[CstInclude])
                        val texts      = resolved.collect { case t: CstText => t.content }
                        assertTrue(
                            !hasInclude,
                            texts.exists(_.contains("Before.")),
                            texts.exists(_.contains("After."))
                        )
                    case Left(_) => assertTrue(false)
            finally cleanup(tmpDir)
        },
        test("resolves nested includes") {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "inner.adoc", "Inner content.\n", createFolders = true)
                os.write(
                    tmpDir / "outer.adoc",
                    "Outer start.\n\ninclude::inner.adoc[]\n\nOuter end.\n",
                    createFolders = true
                )
                val source = "Doc start.\n\ninclude::outer.adoc[]\n\nDoc end.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                IncludeResolver.resolve(cst, tmpDir) match
                    case Right(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assertTrue(
                            texts.exists(_.contains("Doc start.")),
                            texts.exists(_.contains("Outer start.")),
                            texts.exists(_.contains("Inner content.")),
                            texts.exists(_.contains("Doc end."))
                        )
                    case Left(_) => assertTrue(false)
            finally cleanup(tmpDir)
        }
    )
