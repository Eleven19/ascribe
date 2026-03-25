package io.eleven19.ascribe.pipeline

import kyo.{<, IO, Abort, Path, Result, Sync, Resource, direct, now}
import kyo.test.KyoSpecDefault
import io.eleven19.ascribe.ast.*

object IncludeProcessorSpec extends KyoSpecDefault:

    private def makeTempDir: Path < IO =
        Sync.defer { Path(java.nio.file.Files.createTempDirectory("ascribe-inc-test").toString) }

    private def writeFile(dir: Path, name: String, content: String): Unit < IO =
        Sync.defer { java.nio.file.Files.writeString(dir.toJava.resolve(name), content): Unit }

    private def cleanup(dir: Path): Unit < IO =
        Sync.defer {
            def go(p: java.nio.file.Path): Unit =
                if java.nio.file.Files.isDirectory(p) then
                    java.nio.file.Files.list(p).forEach(go(_)): Unit
                java.nio.file.Files.deleteIfExists(p): Unit
            go(dir.toJava)
        }

    def spec = suite("IncludeProcessor")(
        test("resolves basic include directive") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                writeFile(tmpDir, "partial.adoc", "Included content.\n").now
                val source = "Before.\n\ninclude::partial.adoc[]\n\nAfter.\n"
                val result = Abort.run[PipelineError](IncludeProcessor.process(source, tmpDir)).now
                result match
                    case Result.Success(text) =>
                        zio.test.assertTrue(
                            text.contains("Before."),
                            text.contains("Included content."),
                            text.contains("After.")
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
                val result = Abort.run[PipelineError](IncludeProcessor.process(source, tmpDir)).now
                result match
                    case Result.Success(text) =>
                        zio.test.assertTrue(
                            text.contains("Doc start."),
                            text.contains("Outer start."),
                            text.contains("Inner content."),
                            text.contains("Outer end."),
                            text.contains("Doc end.")
                        )
                    case _ => zio.test.assertTrue(false)
            }
        },
        test("fails on missing include file") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                val source = "include::missing.adoc[]\n"
                val result = Abort.run[PipelineError](
                    IncludeProcessor.process(source, tmpDir)
                ).now
                zio.test.assertTrue(!result.isSuccess)
            }
        },
        test("optional include silently skips missing file") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                val source = "Before.\n\ninclude::missing.adoc[opts=optional]\n\nAfter.\n"
                val result = Abort.run[PipelineError](IncludeProcessor.process(source, tmpDir)).now
                result match
                    case Result.Success(text) =>
                        zio.test.assertTrue(
                            text.contains("Before."),
                            text.contains("After."),
                            !text.contains("missing")
                        )
                    case _ => zio.test.assertTrue(false)
            }
        },
        test("include integrates with FileSource.fromFile") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                writeFile(tmpDir, "snippet.adoc", "Snippet text.\n").now
                writeFile(tmpDir, "main.adoc", "Main doc.\n\ninclude::snippet.adoc[]\n").now
                val result = Abort.run[PipelineError](
                    FileSource.fromFile(Path(tmpDir.toJava.resolve("main.adoc").toString)).read
                ).now
                result match
                    case Result.Success(tree) =>
                        val doc  = tree.allDocuments.head._2
                        val text = doc.blocks.flatMap {
                            case Paragraph(content) => content.collect { case Text(s) => s }
                            case _                  => Nil
                        }
                        zio.test.assertTrue(
                            text.contains("Main doc."),
                            text.contains("Snippet text.")
                        )
                    case _ => zio.test.assertTrue(false)
            }
        },
        test("lines without include directives pass through unchanged") {
            direct {
                val tmpDir = makeTempDir.now
                Resource.ensure(cleanup(tmpDir)).now
                val source = "Line one.\nLine two.\nLine three.\n"
                val result = Abort.run[PipelineError](IncludeProcessor.process(source, tmpDir)).now
                result match
                    case Result.Success(text) => zio.test.assertTrue(text == source.stripLineEnd)
                    case _                    => zio.test.assertTrue(false)
            }
        }
    )
