package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import kyo.test.*
import parsley.Success

class IncludeResolverSpec extends Test[Any]:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    "IncludeResolver (Ox)" - {
        "resolves basic include directive" in {
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
                        assert(texts.exists(_.contains("Before.")))
                        assert(texts.exists(_.contains("Included content.")))
                        assert(texts.exists(_.contains("After.")))
                    case Left(_) => assert(false)
            finally cleanup(tmpDir)
        }
        "fails on missing include file" in {
            val tmpDir = os.temp.dir()
            try
                val source = "include::missing.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => throw new AssertionError("parse failed")
                val result = IncludeResolver.resolve(cst, tmpDir)
                assert(result.isLeft)
            finally cleanup(tmpDir)
        }
        "optional include silently skips missing file" in {
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
                        assert(!hasInclude)
                        assert(texts.exists(_.contains("Before.")))
                        assert(texts.exists(_.contains("After.")))
                    case Left(_) => assert(false)
            finally cleanup(tmpDir)
        }
        "resolves nested includes" in {
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
                        assert(texts.exists(_.contains("Doc start.")))
                        assert(texts.exists(_.contains("Outer start.")))
                        assert(texts.exists(_.contains("Inner content.")))
                        assert(texts.exists(_.contains("Doc end.")))
                    case Left(_) => assert(false)
            finally cleanup(tmpDir)
        }
    }
