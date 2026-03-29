package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import zio.test.*

object FileSourceSinkSpec extends ZIOSpecDefault:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    def spec = suite("FileSource & FileSink (Ox)")(
        test("FileSource.fromFile reads and parses a single .adoc file") {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "test.adoc", "Hello world.\n", createFolders = true)
                val result = FileSource.fromFile(tmpDir / "test.adoc").read
                assertTrue(result.isRight)
            finally cleanup(tmpDir)
        },
        test("FileSource.fromDirectory reads all .adoc files") {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "a.adoc", "First.\n", createFolders = true)
                os.write(tmpDir / "b.adoc", "Second.\n", createFolders = true)
                os.write(tmpDir / "c.txt", "Ignored.\n", createFolders = true)
                FileSource.fromDirectory(tmpDir).read match
                    case Right(tree) => assertTrue(tree.size == 2)
                    case Left(_)     => assertTrue(false)
            finally cleanup(tmpDir)
        },
        test("FileSink.toFile writes rendered output") {
            val tmpDir = os.temp.dir()
            try
                val tmpFile = tmpDir / "output.adoc"
                val rendered = Map(DocumentPath("doc.adoc") -> "Hello world.\n")
                FileSink.toFile(tmpFile).write(rendered) match
                    case Right(()) =>
                        val content = os.read(tmpFile)
                        assertTrue(content == "Hello world.\n")
                    case Left(_) => assertTrue(false)
            finally cleanup(tmpDir)
        },
        test("FileSink.toDirectory writes multiple files") {
            val tmpDir = os.temp.dir()
            try
                val rendered = Map(
                    DocumentPath("a.adoc") -> "First.\n",
                    DocumentPath("b.adoc") -> "Second.\n"
                )
                FileSink.toDirectory(tmpDir).write(rendered) match
                    case Right(()) =>
                        val content1 = os.read(tmpDir / "a.adoc")
                        val content2 = os.read(tmpDir / "b.adoc")
                        assertTrue(content1 == "First.\n", content2 == "Second.\n")
                    case Left(_) => assertTrue(false)
            finally cleanup(tmpDir)
        },
        test("FileSource fails on parse error") {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "bad.adoc", "[invalid\n", createFolders = true)
                val result = FileSource.fromFile(tmpDir / "bad.adoc").read
                assertTrue(result.isLeft)
            finally cleanup(tmpDir)
        },
        test("end-to-end: read, transform, write") {
            val srcDir = os.temp.dir()
            val outDir = os.temp.dir()
            try
                os.write(srcDir / "doc.adoc", "hello.\n", createFolders = true)
                val rule = RewriteRule.forInlines { case Text(content) =>
                    RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
                }
                val pipeline = Pipeline.from(FileSource.fromDirectory(srcDir)).rewrite(rule)
                pipeline.runToStrings match
                    case Right(rendered) =>
                        FileSink.toDirectory(outDir).write(rendered) match
                            case Right(()) =>
                                val output = os.read(outDir / "doc.adoc")
                                assertTrue(output == "HELLO.\n")
                            case Left(_) => assertTrue(false)
                    case Left(_) => assertTrue(false)
            finally
                cleanup(srcDir)
                cleanup(outDir)
        }
    )
