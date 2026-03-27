package io.eleven19.ascribe.pipeline

import kyo.{<, Abort, Path, Result, Sync, Resource, direct, now}
import kyo.test.KyoSpecDefault
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import parsley.Success

object IncludeResolverSpec extends KyoSpecDefault:

    private def makeTempDir: Path < Sync =
        Sync.defer(Path(java.nio.file.Files.createTempDirectory("ascribe-inc-cst-test").toString))

    private def writeFile(dir: Path, name: String, content: String): Unit < Sync =
        Sync.defer(java.nio.file.Files.writeString(dir.toJava.resolve(name), content): Unit)

    private def cleanup(dir: Path): Unit < Sync =
        Sync.defer {
            def go(p: java.nio.file.Path): Unit =
                if java.nio.file.Files.isDirectory(p) then java.nio.file.Files.list(p).forEach(go(_)): Unit
                java.nio.file.Files.deleteIfExists(p): Unit
            go(dir.toJava)
        }

    def spec = suite("IncludeResolver")(
        test("resolves basic include directive") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                writeFile(tmpDir, "partial.adoc", "Included content.\n").now
                val source = "Before.\n\ninclude::partial.adoc[]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = Abort.run[PipelineError](IncludeResolver.resolve(cst, tmpDir)).now
                result match
                    case Result.Success(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        zio.test.assertTrue(
                            texts.exists(_.contains("Before.")),
                            texts.exists(_.contains("Included content.")),
                            texts.exists(_.contains("After."))
                        )
                    case _ => zio.test.assertTrue(false)
            }
        },
        test("fails on missing include file") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                val source = "include::missing.adoc[]\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = Abort.run[PipelineError](IncludeResolver.resolve(cst, tmpDir)).now
                zio.test.assertTrue(!result.isSuccess)
            }
        },
        test("optional include silently skips missing file") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                val source = "Before.\n\ninclude::missing.adoc[opts=optional]\n\nAfter.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = Abort.run[PipelineError](IncludeResolver.resolve(cst, tmpDir)).now
                result match
                    case Result.Success(resolved) =>
                        val hasInclude = resolved.content.exists(_.isInstanceOf[CstInclude])
                        val texts      = resolved.collect { case t: CstText => t.content }
                        zio.test.assertTrue(
                            !hasInclude,
                            texts.exists(_.contains("Before.")),
                            texts.exists(_.contains("After."))
                        )
                    case _ => zio.test.assertTrue(false)
            }
        },
        test("resolves nested includes") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                writeFile(tmpDir, "inner.adoc", "Inner content.\n").now
                writeFile(tmpDir, "outer.adoc", "Outer start.\n\ninclude::inner.adoc[]\n\nOuter end.\n").now
                val source = "Doc start.\n\ninclude::outer.adoc[]\n\nDoc end.\n"
                val cst = Ascribe.parseCst(source) match
                    case Success(c) => c
                    case _          => sys.error("parse failed")
                val result = Abort.run[PipelineError](IncludeResolver.resolve(cst, tmpDir)).now
                result match
                    case Result.Success(resolved) =>
                        val texts = resolved.collect { case t: CstText => t.content }
                        zio.test.assertTrue(
                            texts.exists(_.contains("Doc start.")),
                            texts.exists(_.contains("Outer start.")),
                            texts.exists(_.contains("Inner content.")),
                            texts.exists(_.contains("Doc end."))
                        )
                    case _ => zio.test.assertTrue(false)
            }
        }
    )
