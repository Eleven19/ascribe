package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import kyo.test.*

class FileSourceSinkSpec extends Test[Any]:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    "FileSource & FileSink (Ox)" - {
        "FileSource.fromFile reads and parses a single .adoc file" in {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "test.adoc", "Hello world.\n", createFolders = true)
                val result = FileSource.fromFile(tmpDir / "test.adoc").read
                assert(result.isRight)
            finally cleanup(tmpDir)
        }
        "FileSource.fromDirectory reads all .adoc files" in {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "a.adoc", "First.\n", createFolders = true)
                os.write(tmpDir / "b.adoc", "Second.\n", createFolders = true)
                os.write(tmpDir / "c.txt", "Ignored.\n", createFolders = true)
                FileSource.fromDirectory(tmpDir).read match
                    case Right(tree) => assert(tree.size == 2)
                    case Left(_)     => assert(false)
            finally cleanup(tmpDir)
        }
        "FileSink.toFile writes rendered output" in {
            val tmpDir = os.temp.dir()
            try
                val tmpFile  = tmpDir / "output.adoc"
                val rendered = Map(DocumentPath("doc.adoc") -> "Hello world.\n")
                FileSink.toFile(tmpFile).write(rendered) match
                    case Right(()) =>
                        val content = os.read(tmpFile)
                        assert(content == "Hello world.\n")
                    case Left(_) => assert(false)
            finally cleanup(tmpDir)
        }
        "FileSink.toDirectory writes multiple files" in {
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
                        assert(content1 == "First.\n")
                        assert(content2 == "Second.\n")
                    case Left(_) => assert(false)
            finally cleanup(tmpDir)
        }
        "FileSource fails on parse error" in {
            val tmpDir = os.temp.dir()
            try
                os.write(tmpDir / "bad.adoc", "[invalid\n", createFolders = true)
                val result = FileSource.fromFile(tmpDir / "bad.adoc").read
                assert(result.isLeft)
            finally cleanup(tmpDir)
        }
        "end-to-end: read, transform, write" in {
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
                                assert(output == "HELLO.\n")
                            case Left(_) => assert(false)
                    case Left(_) => assert(false)
            finally
                cleanup(srcDir)
                cleanup(outDir)
        }
    }
