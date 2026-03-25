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
        test("fromDocuments preserves full paths") {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val path1 = DocumentPath("chapters", "intro.adoc")
            val path2 = DocumentPath("chapters", "conclusion.adoc")
            val tree = DocumentTree.fromDocuments(List((path1, doc1), (path2, doc2)))
            assertTrue(
                tree.get(path1).isDefined,
                tree.get(path2).isDefined
            )
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
        test("mapDocuments on empty tree returns empty") {
            val mapped = DocumentTree.empty.mapDocuments(d =>
                Document(d.header, Nil)(d.span)
            )
            assertTrue(mapped.size == 0)
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
        test("filter removing root leaf returns empty tree") {
            val tree = DocumentTree.single(document(paragraph("x")))
            val filtered = tree.filter(_ => false)
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
        test("TreeNode.nodePath accessor works for both variants") {
            val leaf = TreeNode.DocLeaf(DocumentPath("a.adoc"), document())
            val branch = TreeNode.TreeBranch(DocumentPath("dir"), Nil)
            assertTrue(
                leaf.nodePath == DocumentPath("a.adoc"),
                branch.nodePath == DocumentPath("dir")
            )
        },
        suite("DocumentPath")(
            test("segments and operations") {
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
            test("/ operator appends segment") {
                val path = DocumentPath("docs") / "guides" / "intro.adoc"
                assertTrue(path.render == "docs/guides/intro.adoc")
            },
            test("/ ignores empty segments") {
                val path = DocumentPath("docs") / "" / "guide.adoc"
                assertTrue(path.render == "docs/guide.adoc")
            },
            test("fromString parses slash-separated path") {
                val path = DocumentPath.fromString("chapters/intro.adoc")
                assertTrue(path == DocumentPath("chapters", "intro.adoc"))
            },
            test("fromString normalizes double slashes") {
                val path = DocumentPath.fromString("chapters//intro.adoc")
                assertTrue(path == DocumentPath("chapters", "intro.adoc"))
            },
            test("apply filters empty segments") {
                val path = DocumentPath("a", "", "b")
                assertTrue(path == DocumentPath("a", "b"))
            },
            test("no-arg apply creates root") {
                assertTrue(DocumentPath() == DocumentPath.root)
            },
            test("root.parent is root") {
                assertTrue(DocumentPath.root.parent == DocumentPath.root)
            },
            test("root.name is empty string") {
                assertTrue(DocumentPath.root.name == "")
            },
            test("parentOption returns None for root") {
                assertTrue(DocumentPath.root.parentOption.isEmpty)
            },
            test("parentOption returns Some for non-root") {
                val path = DocumentPath("a", "b")
                assertTrue(path.parentOption == Some(DocumentPath("a")))
            },
            test("contains returns true when other is nested within") {
                val dir  = DocumentPath("chapters")
                val file = DocumentPath("chapters", "intro.adoc")
                assertTrue(dir.contains(file))
            },
            test("contains returns false for same path") {
                val path = DocumentPath("chapters")
                assertTrue(!path.contains(path))
            },
            test("contains returns false for unrelated path") {
                val dir  = DocumentPath("chapters")
                val file = DocumentPath("appendix", "a.adoc")
                assertTrue(!dir.contains(file))
            },
            test("root contains all non-root paths") {
                assertTrue(DocumentPath.root.contains(DocumentPath("a.adoc")))
            },
            test("startsWith checks prefix") {
                val path = DocumentPath("chapters", "intro.adoc")
                assertTrue(
                    path.startsWith(DocumentPath("chapters")),
                    path.startsWith(DocumentPath.root),
                    !path.startsWith(DocumentPath("appendix"))
                )
            }
        )
    )
