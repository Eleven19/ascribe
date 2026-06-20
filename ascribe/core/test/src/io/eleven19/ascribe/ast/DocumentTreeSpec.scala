package io.eleven19.ascribe.ast

import scala.language.implicitConversions
import kyo.test.*
import io.eleven19.ascribe.ast.dsl.{*, given}

class DocumentTreeSpec extends Test[Any]:

    "DocumentTree" - {
        "single creates a one-document tree" in {
            val doc  = document(paragraph("hello"))
            val tree = DocumentTree.single(doc)
            assert(tree.size == 1)
        }
        "single with path creates a named document" in {
            val doc  = document(paragraph("hello"))
            val path = DocumentPath("intro.adoc")
            val tree = DocumentTree.single(path, doc)
            assert(tree.size == 1)
            assert(tree.get(path).isDefined)
        }
        "fromDocuments creates a flat tree" in {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(
                List(
                    (DocumentPath("a.adoc"), doc1),
                    (DocumentPath("b.adoc"), doc2)
                )
            )
            assert(tree.size == 2)
        }
        "fromDocuments preserves full paths" in {
            val doc1  = document(paragraph("one"))
            val doc2  = document(paragraph("two"))
            val path1 = DocumentPath("chapters", "intro.adoc")
            val path2 = DocumentPath("chapters", "conclusion.adoc")
            val tree  = DocumentTree.fromDocuments(List((path1, doc1), (path2, doc2)))
            assert(tree.get(path1).isDefined)
            assert(tree.get(path2).isDefined)
        }
        "empty tree has no documents" in
            assert(DocumentTree.empty.size == 0)
        "allDocuments returns all leaf documents" in {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(
                List(
                    (DocumentPath("a.adoc"), doc1),
                    (DocumentPath("b.adoc"), doc2)
                )
            )
            val docs = tree.allDocuments
            assert(docs.size == 2)
        }
        "mapDocuments transforms every document" in {
            val doc    = document(paragraph("hello"))
            val tree   = DocumentTree.single(doc)
            val mapped = tree.mapDocuments(d => Document(d.header, d.blocks ++ scala.List(paragraph("added")))(d.span))
            val blocks = mapped.allDocuments.head._2.blocks
            assert(blocks.size == 2)
        }
        "mapDocuments on empty tree returns empty" in {
            val mapped = DocumentTree.empty.mapDocuments(d => Document(d.header, Nil)(d.span))
            assert(mapped.size == 0)
        }
        "filter removes non-matching documents" in {
            val doc1 = document(paragraph("keep"))
            val doc2 = document(paragraph("drop"))
            val tree = DocumentTree.fromDocuments(
                List(
                    (DocumentPath("keep.adoc"), doc1),
                    (DocumentPath("drop.adoc"), doc2)
                )
            )
            val filtered = tree.filter(_.name == "keep.adoc")
            assert(filtered.size == 1)
        }
        "filter on empty tree returns empty" in {
            val filtered = DocumentTree.empty.filter(_ => true)
            assert(filtered.size == 0)
        }
        "filter removing root leaf returns empty tree" in {
            val tree     = DocumentTree.single(document(paragraph("x")))
            val filtered = tree.filter(_ => false)
            assert(filtered.size == 0)
        }
        "get finds document by path" in {
            val doc  = document(paragraph("found"))
            val path = DocumentPath("chapters", "intro.adoc")
            val tree = DocumentTree.single(path, doc)
            assert(tree.get(path).isDefined)
        }
        "get returns None for missing path" in {
            val tree = DocumentTree.single(document(paragraph("x")))
            assert(tree.get(DocumentPath("missing.adoc")).isEmpty)
        }
        "collect extracts values from matching documents" in {
            val doc1 = document(paragraph("one"))
            val doc2 = document(paragraph("two"))
            val tree = DocumentTree.fromDocuments(
                List(
                    (DocumentPath("a.adoc"), doc1),
                    (DocumentPath("b.adoc"), doc2)
                )
            )
            val names = tree.collect { case (p, _) => p.name }
            assert(names == scala.List("a.adoc", "b.adoc"))
        }
        "TreeNode.nodePath accessor works for both variants" in {
            val leaf   = TreeNode.DocLeaf(DocumentPath("a.adoc"), document())
            val branch = TreeNode.TreeBranch(DocumentPath("dir"), Nil)
            assert(leaf.nodePath == DocumentPath("a.adoc"))
            assert(branch.nodePath == DocumentPath("dir"))
        }
        "DocumentPath" - {
            "segments and operations" in {
                val path = DocumentPath("chapters", "intro.adoc")
                assert(path.segments == scala.List("chapters", "intro.adoc"))
                assert(path.name == "intro.adoc")
                assert(path.parent == DocumentPath("chapters"))
                assert(path.depth == 2)
                assert(path.render == "chapters/intro.adoc")
                assert(!path.isRoot)
                assert(DocumentPath.root.isRoot)
            }
            "/ operator appends segment" in {
                val path = DocumentPath("docs") / "guides" / "intro.adoc"
                assert(path.render == "docs/guides/intro.adoc")
            }
            "/ ignores empty segments" in {
                val path = DocumentPath("docs") / "" / "guide.adoc"
                assert(path.render == "docs/guide.adoc")
            }
            "fromString parses slash-separated path" in {
                val path = DocumentPath.fromString("chapters/intro.adoc")
                assert(path == DocumentPath("chapters", "intro.adoc"))
            }
            "fromString normalizes double slashes" in {
                val path = DocumentPath.fromString("chapters//intro.adoc")
                assert(path == DocumentPath("chapters", "intro.adoc"))
            }
            "apply filters empty segments" in {
                val path = DocumentPath("a", "", "b")
                assert(path == DocumentPath("a", "b"))
            }
            "no-arg apply creates root" in
                assert(DocumentPath() == DocumentPath.root)
            "root.parent is root" in
                assert(DocumentPath.root.parent == DocumentPath.root)
            "root.name is empty string" in
                assert(DocumentPath.root.name == "")
            "parentOption returns None for root" in
                assert(DocumentPath.root.parentOption.isEmpty)
            "parentOption returns Some for non-root" in {
                val path = DocumentPath("a", "b")
                assert(path.parentOption == Some(DocumentPath("a")))
            }
            "contains returns true when other is nested within" in {
                val dir  = DocumentPath("chapters")
                val file = DocumentPath("chapters", "intro.adoc")
                assert(dir.contains(file))
            }
            "contains returns false for same path" in {
                val path = DocumentPath("chapters")
                assert(!path.contains(path))
            }
            "contains returns false for unrelated path" in {
                val dir  = DocumentPath("chapters")
                val file = DocumentPath("appendix", "a.adoc")
                assert(!dir.contains(file))
            }
            "root contains all non-root paths" in
                assert(DocumentPath.root.contains(DocumentPath("a.adoc")))
            "startsWith checks prefix" in {
                val path = DocumentPath("chapters", "intro.adoc")
                assert(path.startsWith(DocumentPath("chapters")))
                assert(path.startsWith(DocumentPath.root))
                assert(!path.startsWith(DocumentPath("appendix")))
            }
        }
    }
