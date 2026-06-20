package io.eleven19.ascribe.pipeline

import kyo.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import kyo.{Path as KPath, Result}
import parsley.Success

class IncludeResolverSpec extends Test[Any]:

    private def deleteRecursively(root: java.nio.file.Path): Unit =
        if java.nio.file.Files.isDirectory(root) then
            val stream = java.nio.file.Files.list(root)
            try stream.forEach(deleteRecursively(_))
            finally stream.close()
        java.nio.file.Files.deleteIfExists(root): Unit

    "IncludeResolver" - {
        "resolves basic include directive" in {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-inc-cst-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("partial.adoc"), "Included content.\n")
                val source = "Before.\n\ninclude::partial.adoc[]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = KyoTestSupport.runSyncAbort(
                    IncludeResolver.resolve(cst, KPath(tmpDir.toString))
                )
                result match
                    case Result.Success(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assert(texts.exists(_.contains("Before.")))
                        assert(texts.exists(_.contains("Included content.")))
                        assert(texts.exists(_.contains("After.")))
                    case _ => assert(false)
            finally deleteRecursively(tmpDir)
        }
        "fails on missing include file" in {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-inc-cst-test")
            try
                val source = "include::missing.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = KyoTestSupport.runSyncAbort(
                    IncludeResolver.resolve(cst, KPath(tmpDir.toString))
                )
                assert(!result.isSuccess)
            finally deleteRecursively(tmpDir)
        }
        "optional include silently skips missing file" in {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-inc-cst-test")
            try
                val source = "Before.\n\ninclude::missing.adoc[opts=optional]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = KyoTestSupport.runSyncAbort(
                    IncludeResolver.resolve(cst, KPath(tmpDir.toString))
                )
                result match
                    case Result.Success(resolved) =>
                        val hasInclude = resolved.content.exists(_.isInstanceOf[CstInclude])
                        val texts      = resolved.collect { case t: CstText => t.content }
                        assert(!hasInclude)
                        assert(texts.exists(_.contains("Before.")))
                        assert(texts.exists(_.contains("After.")))
                    case _ => assert(false)
            finally deleteRecursively(tmpDir)
        }
        "resolves nested includes" in {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-inc-cst-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("inner.adoc"), "Inner content.\n")
                java.nio.file.Files.writeString(
                    tmpDir.resolve("outer.adoc"),
                    "Outer start.\n\ninclude::inner.adoc[]\n\nOuter end.\n"
                )
                val source = "Doc start.\n\ninclude::outer.adoc[]\n\nDoc end.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = KyoTestSupport.runSyncAbort(
                    IncludeResolver.resolve(cst, KPath(tmpDir.toString))
                )
                result match
                    case Result.Success(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        assert(texts.exists(_.contains("Doc start.")))
                        assert(texts.exists(_.contains("Outer start.")))
                        assert(texts.exists(_.contains("Inner content.")))
                        assert(texts.exists(_.contains("Doc end.")))
                    case _ => assert(false)
            finally deleteRecursively(tmpDir)
        }
    }
