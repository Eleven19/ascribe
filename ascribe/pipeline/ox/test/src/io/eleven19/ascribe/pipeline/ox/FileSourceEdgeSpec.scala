package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import zio.test.*

/** File I/O branches: invalid inputs, empty trees, nested paths, subdirectory discovery. */
object FileSourceEdgeSpec extends ZIOSpecDefault:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    def spec = suite("FileSource & FileSink (Ox) edge cases")(
        test("FileSource.fromDirectory fails when path is a regular file") {
            val d = os.temp.dir()
            val f = d / "only.adoc"
            os.write(f, "", createFolders = true)
            try
                val r = FileSource.fromDirectory(f).read
                assertTrue(r.isLeft)
            finally cleanup(d)
        },
        test("FileSource.fromDirectory with no .adoc files yields empty tree") {
            val d = os.temp.dir()
            try
                os.write(d / "note.txt", "not adoc", createFolders = true)
                FileSource.fromDirectory(d).read match
                    case Right(t) => assertTrue(t.size == 0)
                    case Left(_)  => assertTrue(false)
            finally cleanup(d)
        },
        test("FileSource.fromDirectory discovers .adoc in subdirectories") {
            val d = os.temp.dir()
            try
                os.write(d / "part" / "inner.adoc", "Nested.\n", createFolders = true)
                FileSource.fromDirectory(d).read match
                    case Right(t) =>
                        val path = t.allDocuments.head._1
                        assertTrue(t.size == 1, path.render.contains("inner.adoc"))
                    case Left(_) => assertTrue(false)
            finally cleanup(d)
        },
        test("FileSink.toDirectory creates nested parent directories for deep DocumentPath") {
            val out = os.temp.dir()
            try
                val deep       = DocumentPath.fromString("one/two/out.adoc")
                val rendered   = Map(deep -> "deep content\n")
                FileSink.toDirectory(out).write(rendered) match
                    case Right(()) =>
                        val p        = out / "one" / "two" / "out.adoc"
                        val onDisk   = os.read(p)
                        val expected = "deep content\n"
                        assertTrue(onDisk == expected)
                    case Left(_) => assertTrue(false)
            finally cleanup(out)
        },
        test("FileSink.toFile with empty rendered map succeeds without writing") {
            val d = os.temp.dir()
            try
                val f = d / "missing.txt"
                FileSink.toFile(f).write(Map.empty) match
                    case Right(()) =>
                        val missing = !os.exists(f)
                        assertTrue(missing)
                    case Left(_) => assertTrue(false)
            finally cleanup(d)
        }
    )
