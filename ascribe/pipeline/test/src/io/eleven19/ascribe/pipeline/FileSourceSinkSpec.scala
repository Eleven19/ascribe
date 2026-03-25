package io.eleven19.ascribe.pipeline

import kyo.{<, IO, Abort, Path, Result, Sync, direct, now}
import kyo.test.KyoSpecDefault
import io.eleven19.ascribe.ast.*

object FileSourceSinkSpec extends KyoSpecDefault:

    /** Create a temp directory using Kyo IO. */
    private def makeTempDir: Path < IO =
        Sync.defer {
            Path(java.nio.file.Files.createTempDirectory("ascribe-test").toString)
        }

    /** Write a string to a file using Kyo IO. */
    private def writeFile(dir: Path, name: String, content: String): Unit < IO =
        Sync.defer {
            java.nio.file.Files.writeString(dir.toJava.resolve(name), content): Unit
        }

    /** Read a file using Kyo IO. */
    private def readFile(dir: Path, name: String): String < IO =
        Sync.defer {
            java.nio.file.Files.readString(dir.toJava.resolve(name))
        }

    /** Recursively delete a directory using Kyo IO. */
    private def cleanup(dir: Path): Unit < IO =
        Sync.defer {
            def go(p: java.nio.file.Path): Unit =
                if java.nio.file.Files.isDirectory(p) then
                    java.nio.file.Files.list(p).forEach(go(_)): Unit
                java.nio.file.Files.deleteIfExists(p): Unit
            go(dir.toJava)
        }

    def spec = suite("FileSource & FileSink")(
        test("FileSource.fromFile reads and parses a single .adoc file") {
            direct {
                val tmpDir = makeTempDir.now
                val tmpFile = tmpDir.toJava.resolve("test.adoc")
                writeFile(tmpDir, "test.adoc", "Hello world.\n").now
                val result = Abort.run[PipelineError](
                    FileSource.fromFile(Path(tmpFile.toString)).read
                ).now
                cleanup(tmpDir).now
                zio.test.assertTrue(result.isSuccess)
            }
        },
        test("FileSource.fromDirectory reads all .adoc files") {
            direct {
                val tmpDir = makeTempDir.now
                writeFile(tmpDir, "a.adoc", "First.\n").now
                writeFile(tmpDir, "b.adoc", "Second.\n").now
                writeFile(tmpDir, "c.txt", "Ignored.\n").now
                val result = Abort.run[PipelineError](
                    FileSource.fromDirectory(tmpDir).read
                ).now
                cleanup(tmpDir).now
                result match
                    case Result.Success(tree) => zio.test.assertTrue(tree.size == 2)
                    case _                    => zio.test.assertTrue(false)
            }
        },
        test("FileSink.toFile writes rendered output") {
            direct {
                val tmpDir  = makeTempDir.now
                val tmpFile = tmpDir.toJava.resolve("output.adoc")
                val rendered = Map(DocumentPath("doc.adoc") -> "Hello world.\n")
                FileSink.toFile(Path(tmpFile.toString)).write(rendered).now
                val content = readFile(tmpDir, "output.adoc").now
                cleanup(tmpDir).now
                zio.test.assertTrue(content == "Hello world.\n")
            }
        },
        test("FileSink.toDirectory writes multiple files") {
            direct {
                val tmpDir   = makeTempDir.now
                val rendered = Map(
                    DocumentPath("a.adoc") -> "First.\n",
                    DocumentPath("b.adoc") -> "Second.\n"
                )
                FileSink.toDirectory(tmpDir).write(rendered).now
                val content1 = readFile(tmpDir, "a.adoc").now
                val content2 = readFile(tmpDir, "b.adoc").now
                cleanup(tmpDir).now
                zio.test.assertTrue(content1 == "First.\n", content2 == "Second.\n")
            }
        },
        test("FileSource fails on parse error") {
            direct {
                val tmpDir = makeTempDir.now
                writeFile(tmpDir, "bad.adoc", "[invalid\n").now
                val result = Abort.run[PipelineError](
                    FileSource.fromFile(Path(tmpDir.toJava.resolve("bad.adoc").toString)).read
                ).now
                cleanup(tmpDir).now
                zio.test.assertTrue(!result.isSuccess)
            }
        },
        test("end-to-end: read, transform, write") {
            direct {
                val srcDir = makeTempDir.now
                val outDir = makeTempDir.now
                writeFile(srcDir, "doc.adoc", "hello.\n").now
                val rule = RewriteRule.forInlines {
                    case Text(content) =>
                        RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
                }
                val pipeline = Pipeline.from(FileSource.fromDirectory(srcDir)).rewrite(rule)
                val result   = Abort.run[PipelineError](pipeline.runToStrings).now
                val testResult = result match
                    case Result.Success(rendered) =>
                        FileSink.toDirectory(outDir).write(rendered).now
                        val output = readFile(outDir, "doc.adoc").now
                        zio.test.assertTrue(output == "HELLO.\n")
                    case _ => zio.test.assertTrue(false)
                cleanup(srcDir).now
                cleanup(outDir).now
                testResult
            }
        }
    )
