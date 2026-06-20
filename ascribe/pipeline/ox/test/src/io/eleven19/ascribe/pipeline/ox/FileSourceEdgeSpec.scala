package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import kyo.test.*

/** File I/O branches: invalid inputs, empty trees, nested paths, subdirectory discovery. */
class FileSourceEdgeSpec extends Test[Any]:

    private def cleanup(root: os.Path): Unit =
        if os.exists(root) then os.remove.all(root)

    "FileSource & FileSink (Ox) edge cases" - {
        "FileSource.fromDirectory fails when path is a regular file" in {
            val d = os.temp.dir()
            val f = d / "only.adoc"
            os.write(f, "", createFolders = true)
            try
                val r = FileSource.fromDirectory(f).read
                assert(r.isLeft)
            finally cleanup(d)
        }
        "FileSource.fromDirectory with no .adoc files yields empty tree" in {
            val d = os.temp.dir()
            try
                os.write(d / "note.txt", "not adoc", createFolders = true)
                FileSource.fromDirectory(d).read match
                    case Right(t) => assert(t.size == 0)
                    case Left(_)  => assert(false)
            finally cleanup(d)
        }
        "FileSource.fromDirectory discovers .adoc in subdirectories" in {
            val d = os.temp.dir()
            try
                os.write(d / "part" / "inner.adoc", "Nested.\n", createFolders = true)
                FileSource.fromDirectory(d).read match
                    case Right(t) =>
                        val path = t.allDocuments.head._1
                        assert(t.size == 1)
                        assert(path.render.contains("inner.adoc"))
                    case Left(_) => assert(false)
            finally cleanup(d)
        }
        "FileSink.toDirectory creates nested parent directories for deep DocumentPath" in {
            val out = os.temp.dir()
            try
                val deep     = DocumentPath.fromString("one/two/out.adoc")
                val rendered = Map(deep -> "deep content\n")
                FileSink.toDirectory(out).write(rendered) match
                    case Right(()) =>
                        val p        = out / "one" / "two" / "out.adoc"
                        val onDisk   = os.read(p)
                        val expected = "deep content\n"
                        assert(onDisk == expected)
                    case Left(_) => assert(false)
            finally cleanup(out)
        }
        "FileSink.toFile with empty rendered map succeeds without writing" in {
            val d = os.temp.dir()
            try
                val f = d / "missing.txt"
                FileSink.toFile(f).write(Map.empty) match
                    case Right(()) =>
                        val missing = !os.exists(f)
                        assert(missing)
                    case Left(_) => assert(false)
            finally cleanup(d)
        }
    }
