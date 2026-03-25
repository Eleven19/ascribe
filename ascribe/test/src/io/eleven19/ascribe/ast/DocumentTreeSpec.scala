package io.eleven19.ascribe.ast

import zio.test.*
import io.eleven19.ascribe.ast.dsl.{*, given}

object DocumentTreeSpec extends ZIOSpecDefault:

    def spec = suite("DocumentTree")(
        test("single creates a one-document tree") {
            val doc  = document(paragraph("hello"))
            val tree = DocumentTree.single(doc)
            assertTrue(tree.size == 1)
        },
        test("single with path creates a named document") {
            val doc  = document(paragraph("hello"))
            val path = DocumentPath("intro.adoc")
            val tree = DocumentTree.single(path, doc)
            assertTrue(
                tree.size == 1,
                tree.get(path).isDefined
            )
        },
        test("fromDocuments creates a flat tree") {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(List(
                (DocumentPath("a.adoc"), doc1),
                (DocumentPath("b.adoc"), doc2)
            ))
            assertTrue(tree.size == 2)
        },
        test("empty tree has no documents") {
            assertTrue(DocumentTree.empty.size == 0)
        },
        test("allDocuments returns all leaf documents") {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(List(
                (DocumentPath("a.adoc"), doc1),
                (DocumentPath("b.adoc"), doc2)
            ))
            val docs = tree.allDocuments
            assertTrue(docs.size == 2)
        },
        test("mapDocuments transforms every document") {
            val doc = document(paragraph("hello"))
            val tree = DocumentTree.single(doc)
            val mapped = tree.mapDocuments(d =>
                Document(d.header, d.blocks ++ scala.List(paragraph("added")))(d.span)
            )
            val blocks = mapped.allDocuments.head._2.blocks
            assertTrue(blocks.size == 2)
        },
        test("filter removes non-matching documents") {
            val doc1 = document(paragraph("keep"))
            val doc2 = document(paragraph("drop"))
            val tree = DocumentTree.fromDocuments(List(
                (DocumentPath("keep.adoc"), doc1),
                (DocumentPath("drop.adoc"), doc2)
            ))
            val filtered = tree.filter(_.name == "keep.adoc")
            assertTrue(filtered.size == 1)
        },
        test("filter on empty tree returns empty") {
            val filtered = DocumentTree.empty.filter(_ => true)
            assertTrue(filtered.size == 0)
        },
        test("get finds document by path") {
            val doc = document(paragraph("found"))
            val path = DocumentPath("chapters", "intro.adoc")
            val tree = DocumentTree.single(path, doc)
            assertTrue(tree.get(path).isDefined)
        },
        test("get returns None for missing path") {
            val tree = DocumentTree.single(document(paragraph("x")))
            assertTrue(tree.get(DocumentPath("missing.adoc")).isEmpty)
        },
        test("collect extracts values from matching documents") {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(List(
                (DocumentPath("a.adoc"), doc1),
                (DocumentPath("b.adoc"), doc2)
            ))
            val names = tree.collect { case (p, _) => p.name }
            assertTrue(names == scala.List("a.adoc", "b.adoc"))
        },
        test("DocumentPath segments and operations") {
            val path = DocumentPath("chapters", "intro.adoc")
            assertTrue(
                path.segments == scala.List("chapters", "intro.adoc"),
                path.name == "intro.adoc",
                path.parent == DocumentPath("chapters"),
                path.depth == 2,
                path.render == "chapters/intro.adoc",
                !path.isRoot,
                DocumentPath.root.isRoot
            )
        },
        test("DocumentPath / operator appends segment") {
            val path = DocumentPath("docs") / "guides" / "intro.adoc"
            assertTrue(path.render == "docs/guides/intro.adoc")
        },
        test("DocumentPath.fromString parses slash-separated path") {
            val path = DocumentPath.fromString("chapters/intro.adoc")
            assertTrue(path == DocumentPath("chapters", "intro.adoc"))
        }
    )
