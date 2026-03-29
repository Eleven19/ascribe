package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import zio.test.*

import java.nio.file.Path as JPath

object FileSourceSinkSpec extends ZIOSpecDefault:

    private def deleteRecursively(root: JPath): Unit =
        if java.nio.file.Files.isDirectory(root) then
            val stream = java.nio.file.Files.list(root)
            try stream.forEach(deleteRecursively(_))
            finally stream.close()
        java.nio.file.Files.deleteIfExists(root): Unit

    def spec = suite("FileSource & FileSink (Ox)")(
        test("FileSource.fromFile reads and parses a single .adoc file") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("test.adoc"), "Hello world.\n")
                val result = FileSource.fromFile(tmpDir.resolve("test.adoc")).read
                assertTrue(result.isRight)
            finally deleteRecursively(tmpDir)
        },
        test("FileSource.fromDirectory reads all .adoc files") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("a.adoc"), "First.\n")
                java.nio.file.Files.writeString(tmpDir.resolve("b.adoc"), "Second.\n")
                java.nio.file.Files.writeString(tmpDir.resolve("c.txt"), "Ignored.\n")
                FileSource.fromDirectory(tmpDir).read match
                    case Right(tree) => assertTrue(tree.size == 2)
                    case Left(_)     => assertTrue(false)
            finally deleteRecursively(tmpDir)
        },
        test("FileSink.toFile writes rendered output") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                val tmpFile = tmpDir.resolve("output.adoc")
                val rendered = Map(DocumentPath("doc.adoc") -> "Hello world.\n")
                FileSink.toFile(tmpFile).write(rendered) match
                    case Right(()) =>
                        val content = java.nio.file.Files.readString(tmpFile)
                        assertTrue(content == "Hello world.\n")
                    case Left(_) => assertTrue(false)
            finally deleteRecursively(tmpDir)
        },
        test("FileSink.toDirectory writes multiple files") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                val rendered = Map(
                    DocumentPath("a.adoc") -> "First.\n",
                    DocumentPath("b.adoc") -> "Second.\n"
                )
                FileSink.toDirectory(tmpDir).write(rendered) match
                    case Right(()) =>
                        val content1 = java.nio.file.Files.readString(tmpDir.resolve("a.adoc"))
                        val content2 = java.nio.file.Files.readString(tmpDir.resolve("b.adoc"))
                        assertTrue(content1 == "First.\n", content2 == "Second.\n")
                    case Left(_) => assertTrue(false)
            finally deleteRecursively(tmpDir)
        },
        test("FileSource fails on parse error") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("bad.adoc"), "[invalid\n")
                val result = FileSource.fromFile(tmpDir.resolve("bad.adoc")).read
                assertTrue(result.isLeft)
            finally deleteRecursively(tmpDir)
        },
        test("end-to-end: read, transform, write") {
            val srcDir = java.nio.file.Files.createTempDirectory("ascribe-src")
            val outDir = java.nio.file.Files.createTempDirectory("ascribe-out")
            try
                java.nio.file.Files.writeString(srcDir.resolve("doc.adoc"), "hello.\n")
                val rule = RewriteRule.forInlines { case Text(content) =>
                    RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
                }
                val pipeline = Pipeline.from(FileSource.fromDirectory(srcDir)).rewrite(rule)
                pipeline.runToStrings match
                    case Right(rendered) =>
                        FileSink.toDirectory(outDir).write(rendered) match
                            case Right(()) =>
                                val output = java.nio.file.Files.readString(outDir.resolve("doc.adoc"))
                                assertTrue(output == "HELLO.\n")
                            case Left(_) => assertTrue(false)
                    case Left(_) => assertTrue(false)
            finally
                deleteRecursively(srcDir)
                deleteRecursively(outDir)
        }
    )
