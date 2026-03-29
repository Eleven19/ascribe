package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import zio.test.*

import java.nio.file.{Files as JFiles}

/** File I/O branches: invalid inputs, empty trees, nested paths, subdirectory discovery.
  *
  * Uses `JFiles` because `zio.test.*` brings a conflicting `Files` symbol; keep `JFiles.*` calls outside
  * `assertTrue(...)` when the macro would otherwise trip over Java interop.
  */
object FileSourceEdgeSpec extends ZIOSpecDefault:

    private def deleteRecursively(root: java.nio.file.Path): Unit =
        if JFiles.isDirectory(root) then
            val stream = JFiles.list(root)
            try stream.forEach(deleteRecursively(_))
            finally stream.close()
        JFiles.deleteIfExists(root): Unit

    def spec = suite("FileSource & FileSink (Ox) edge cases")(
        test("FileSource.fromDirectory fails when path is a regular file") {
            val f = JFiles.createTempFile("ascribe-fs", ".adoc")
            try
                val r = FileSource.fromDirectory(f).read
                assertTrue(r.isLeft)
            finally JFiles.deleteIfExists(f): Unit
        },
        test("FileSource.fromDirectory with no .adoc files yields empty tree") {
            val d = JFiles.createTempDirectory("ascribe-empty")
            try
                JFiles.writeString(d.resolve("note.txt"), "not adoc")
                FileSource.fromDirectory(d).read match
                    case Right(t) => assertTrue(t.size == 0)
                    case Left(_)  => assertTrue(false)
            finally deleteRecursively(d)
        },
        test("FileSource.fromDirectory discovers .adoc in subdirectories") {
            val d = JFiles.createTempDirectory("ascribe-nested")
            try
                val sub = JFiles.createDirectories(d.resolve("part"))
                JFiles.writeString(sub.resolve("inner.adoc"), "Nested.\n")
                FileSource.fromDirectory(d).read match
                    case Right(t) =>
                        val path = t.allDocuments.head._1
                        assertTrue(t.size == 1, path.render.contains("inner.adoc"))
                    case Left(_) => assertTrue(false)
            finally deleteRecursively(d)
        },
        test("FileSink.toDirectory creates nested parent directories for deep DocumentPath") {
            val out = JFiles.createTempDirectory("ascribe-deep-out")
            try
                val deep = DocumentPath.fromString("one/two/out.adoc")
                val rendered = Map(deep -> "deep content\n")
                FileSink.toDirectory(out).write(rendered) match
                    case Right(()) =>
                        val p       = out.resolve("one").resolve("two").resolve("out.adoc")
                        val onDisk  = JFiles.readString(p)
                        val expected = "deep content\n"
                        assertTrue(onDisk == expected)
                    case Left(_) => assertTrue(false)
            finally deleteRecursively(out)
        },
        test("FileSink.toFile with empty rendered map succeeds without writing") {
            val d = JFiles.createTempDirectory("ascribe-fs")
            try
                val f = d.resolve("missing.txt")
                FileSink.toFile(f).write(Map.empty) match
                    case Right(()) =>
                        val missing = !JFiles.exists(f)
                        assertTrue(missing)
                    case Left(_)   => assertTrue(false)
            finally deleteRecursively(d)
        }
    )
