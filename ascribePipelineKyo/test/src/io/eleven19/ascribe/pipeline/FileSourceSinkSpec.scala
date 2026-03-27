package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import kyo.{Abort, Path as KPath, Result}
import zio.test.*

object FileSourceSinkSpec extends ZIOSpecDefault:

    private def deleteRecursively(root: java.nio.file.Path): Unit =
        if java.nio.file.Files.isDirectory(root) then
            val stream = java.nio.file.Files.list(root)
            try stream.forEach(deleteRecursively(_))
            finally stream.close()
        java.nio.file.Files.deleteIfExists(root): Unit

    def spec = suite("FileSource & FileSink")(
        test("FileSource.fromFile reads and parses a single .adoc file") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("test.adoc"), "Hello world.\n")
                val result = KyoTestSupport.runSyncAbort(
                    FileSource.fromFile(KPath(tmpDir.resolve("test.adoc").toString)).read
                )
                assertTrue(result.isSuccess)
            finally deleteRecursively(tmpDir)
        },
        test("FileSource.fromDirectory reads all .adoc files") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("a.adoc"), "First.\n")
                java.nio.file.Files.writeString(tmpDir.resolve("b.adoc"), "Second.\n")
                java.nio.file.Files.writeString(tmpDir.resolve("c.txt"), "Ignored.\n")
                val result = KyoTestSupport.runSyncAbort(
                    FileSource.fromDirectory(KPath(tmpDir.toString)).read
                )
                result match
                    case Result.Success(tree) => assertTrue(tree.size == 2)
                    case _                    => assertTrue(false)
            finally deleteRecursively(tmpDir)
        },
        test("FileSink.toFile writes rendered output") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                val tmpFile = tmpDir.resolve("output.adoc")
                val rendered = Map(DocumentPath("doc.adoc") -> "Hello world.\n")
                KyoTestSupport.runSync(FileSink.toFile(KPath(tmpFile.toString)).write(rendered))
                val content = java.nio.file.Files.readString(tmpFile)
                assertTrue(content == "Hello world.\n")
            finally deleteRecursively(tmpDir)
        },
        test("FileSink.toDirectory writes multiple files") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                val rendered = Map(
                    DocumentPath("a.adoc") -> "First.\n",
                    DocumentPath("b.adoc") -> "Second.\n"
                )
                KyoTestSupport.runSync(FileSink.toDirectory(KPath(tmpDir.toString)).write(rendered))
                val content1 = java.nio.file.Files.readString(tmpDir.resolve("a.adoc"))
                val content2 = java.nio.file.Files.readString(tmpDir.resolve("b.adoc"))
                assertTrue(content1 == "First.\n", content2 == "Second.\n")
            finally deleteRecursively(tmpDir)
        },
        test("FileSource fails on parse error") {
            val tmpDir = java.nio.file.Files.createTempDirectory("ascribe-test")
            try
                java.nio.file.Files.writeString(tmpDir.resolve("bad.adoc"), "[invalid\n")
                val result = KyoTestSupport.runSyncAbort(
                    FileSource.fromFile(KPath(tmpDir.resolve("bad.adoc").toString)).read
                )
                assertTrue(!result.isSuccess)
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
                val pipeline = Pipeline.from(FileSource.fromDirectory(KPath(srcDir.toString))).rewrite(rule)
                val result =
                    KyoTestSupport.runSyncAbortResult(Abort.run[PipelineError](pipeline.runToStrings))
                result match
                    case Result.Success(rendered) =>
                        KyoTestSupport.runSync(FileSink.toDirectory(KPath(outDir.toString)).write(rendered))
                        val output = java.nio.file.Files.readString(outDir.resolve("doc.adoc"))
                        assertTrue(output == "HELLO.\n")
                    case _ => assertTrue(false)
            finally
                deleteRecursively(srcDir)
                deleteRecursively(outDir)
        }
    )
