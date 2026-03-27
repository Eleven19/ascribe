package io.eleven19.ascribe.cst

import scala.collection.mutable.ArrayBuffer
import scala.util.control.TailCalls.*

/** Visitor trait for CST nodes. Each method has a default that delegates up the type hierarchy, so you only need to
  * override the methods you care about.
  *
  * Hierarchy: CstNode → CstTopLevel → CstBlock / CstBlankLine; CstInline. Override `visitNode` to handle all nodes,
  * `visitBlock` for all blocks, etc.
  */
trait CstVisitor[A]:
    def visitNode(node: CstNode): A

    def visitTopLevel(node: CstTopLevel): A = visitNode(node)
    def visitBlock(node: CstBlock): A       = visitTopLevel(node)
    def visitInline(node: CstInline): A     = visitNode(node)

    def visitDocument(node: CstDocument): A             = visitNode(node)
    def visitDocumentHeader(node: CstDocumentHeader): A = visitNode(node)

    def visitHeading(node: CstHeading): A                         = visitBlock(node)
    def visitParagraph(node: CstParagraph): A                     = visitBlock(node)
    def visitDelimitedBlock(node: CstDelimitedBlock): A           = visitBlock(node)
    def visitList(node: CstList): A                               = visitBlock(node)
    def visitTable(node: CstTable): A                             = visitBlock(node)
    def visitInclude(node: CstInclude): A                         = visitBlock(node)
    def visitLineComment(node: CstLineComment): A                 = visitBlock(node)
    def visitAttributeEntry(node: CstAttributeEntry): A           = visitBlock(node)
    def visitAdmonitionParagraph(node: CstAdmonitionParagraph): A = visitBlock(node)
    def visitBlankLine(node: CstBlankLine): A                     = visitTopLevel(node)

    def visitParagraphLine(node: CstParagraphLine): A = visitNode(node)
    def visitListItem(node: CstListItem): A           = visitNode(node)
    def visitBlockTitle(node: CstBlockTitle): A       = visitNode(node)
    def visitAttributeList(node: CstAttributeList): A = visitNode(node)
    def visitTableRow(node: CstTableRow): A           = visitNode(node)
    def visitTableCell(node: CstTableCell): A         = visitNode(node)

    def visitText(node: CstText): A                     = visitInline(node)
    def visitBold(node: CstBold): A                     = visitInline(node)
    def visitItalic(node: CstItalic): A                 = visitInline(node)
    def visitMono(node: CstMono): A                     = visitInline(node)
    def visitAttributeRef(node: CstAttributeRef): A     = visitInline(node)
    def visitAutolink(node: CstAutolink): A             = visitInline(node)
    def visitUrlMacro(node: CstUrlMacro): A             = visitInline(node)
    def visitLinkMacro(node: CstLinkMacro): A           = visitInline(node)
    def visitMailtoMacro(node: CstMailtoMacro): A       = visitInline(node)

    def visitVerbatimContent(node: CstVerbatimContent): A = visitNode(node)
    def visitNestedContent(node: CstNestedContent): A     = visitNode(node)
    def visitCellInlines(node: CstCellInlines): A         = visitNode(node)
    def visitCellBlocks(node: CstCellBlocks): A           = visitNode(node)

/** Utilities for visiting and folding over CST trees.
  *
  * Traversal uses trampolining (`scala.util.control.TailCalls`) for stack safety, so arbitrarily deep CST trees can be
  * processed without risk of stack overflow.
  */
object CstVisitor:

    /** Dispatch a node to the appropriate visitor method. */
    def visit[A](node: CstNode, visitor: CstVisitor[A]): A = node match
        case n: CstDocument            => visitor.visitDocument(n)
        case n: CstDocumentHeader      => visitor.visitDocumentHeader(n)
        case n: CstHeading             => visitor.visitHeading(n)
        case n: CstParagraph           => visitor.visitParagraph(n)
        case n: CstDelimitedBlock      => visitor.visitDelimitedBlock(n)
        case n: CstList                => visitor.visitList(n)
        case n: CstTable               => visitor.visitTable(n)
        case n: CstInclude             => visitor.visitInclude(n)
        case n: CstLineComment         => visitor.visitLineComment(n)
        case n: CstAttributeEntry      => visitor.visitAttributeEntry(n)
        case n: CstAdmonitionParagraph => visitor.visitAdmonitionParagraph(n)
        case n: CstBlankLine           => visitor.visitBlankLine(n)
        case n: CstParagraphLine       => visitor.visitParagraphLine(n)
        case n: CstListItem            => visitor.visitListItem(n)
        case n: CstBlockTitle          => visitor.visitBlockTitle(n)
        case n: CstAttributeList       => visitor.visitAttributeList(n)
        case n: CstTableRow            => visitor.visitTableRow(n)
        case n: CstTableCell           => visitor.visitTableCell(n)
        case n: CstText                => visitor.visitText(n)
        case n: CstBold                => visitor.visitBold(n)
        case n: CstItalic              => visitor.visitItalic(n)
        case n: CstMono                => visitor.visitMono(n)
        case n: CstAttributeRef        => visitor.visitAttributeRef(n)
        case n: CstAutolink            => visitor.visitAutolink(n)
        case n: CstUrlMacro            => visitor.visitUrlMacro(n)
        case n: CstLinkMacro           => visitor.visitLinkMacro(n)
        case n: CstMailtoMacro         => visitor.visitMailtoMacro(n)
        case n: CstVerbatimContent     => visitor.visitVerbatimContent(n)
        case n: CstNestedContent       => visitor.visitNestedContent(n)
        case n: CstCellInlines         => visitor.visitCellInlines(n)
        case n: CstCellBlocks          => visitor.visitCellBlocks(n)

    /** Return all direct child nodes of a node. */
    def children(node: CstNode): List[CstNode] = node match
        case d: CstDocument             => d.header.toList ++ d.content
        case dh: CstDocumentHeader      => List(dh.title) ++ dh.attributes
        case h: CstHeading              => h.title
        case p: CstParagraph            => p.lines
        case pl: CstParagraphLine       => pl.content
        case db: CstDelimitedBlock      => db.attributes.toList ++ db.title.toList ++ List(db.content)
        case _: CstVerbatimContent      => Nil
        case nc: CstNestedContent       => nc.children
        case l: CstList                 => l.items
        case li: CstListItem            => li.content
        case t: CstTable                => t.attributes.toList ++ t.title.toList ++ t.rows
        case tr: CstTableRow            => tr.cells
        case tc: CstTableCell           => List(tc.content)
        case ci: CstCellInlines         => ci.content
        case cb: CstCellBlocks          => cb.content
        case _: CstInclude              => Nil
        case _: CstLineComment          => Nil
        case _: CstAttributeEntry       => Nil
        case ap: CstAdmonitionParagraph => ap.content
        case _: CstBlankLine            => Nil
        case _: CstAttributeList        => Nil
        case bt: CstBlockTitle          => bt.content
        case _: CstText                 => Nil
        case b: CstBold                 => b.content
        case i: CstItalic               => i.content
        case m: CstMono                 => m.content
        case _: CstAttributeRef         => Nil
        case _: CstAutolink             => Nil
        case n: CstUrlMacro             => n.text
        case n: CstLinkMacro            => n.text
        case n: CstMailtoMacro          => n.text

    /** Pre-order left fold: visits each node before its children, accumulating left-to-right. Stack-safe via
      * trampolining.
      */
    def foldLeft[A](node: CstNode)(init: A)(f: (A, CstNode) => A): A =
        def go(n: CstNode, acc: A): TailRec[A] =
            val newAcc = f(acc, n)
            val kids   = children(n)
            if kids.isEmpty then done(newAcc)
            else
                kids.foldLeft(done(newAcc)) { (tailAcc, child) =>
                    tailAcc.flatMap(a => tailcall(go(child, a)))
                }
        go(node, init).result

    /** Post-order right fold: visits children before their parent, accumulating right-to-left. Stack-safe via
      * trampolining.
      */
    def foldRight[A](node: CstNode)(init: A)(f: (CstNode, A) => A): A =
        def go(n: CstNode, acc: A): TailRec[A] =
            val kids = children(n)
            val childResult =
                if kids.isEmpty then done(acc)
                else
                    kids.foldRight(done(acc)) { (child, tailAcc) =>
                        tailAcc.flatMap(a => tailcall(go(child, a)))
                    }
            childResult.map(a => f(n, a))
        go(node, init).result

    /** Pre-order fold (alias for `foldLeft`). */
    def fold[A](node: CstNode)(init: A)(f: (A, CstNode) => A): A = foldLeft(node)(init)(f)

    /** Collect values from all nodes in the tree that match a partial function (pre-order). */
    def collect[B](node: CstNode)(pf: PartialFunction[CstNode, B]): List[B] =
        val buf = ArrayBuffer.empty[B]
        foldLeft(node)(())((_, n) => if pf.isDefinedAt(n) then buf += pf(n))
        buf.toList

    /** Collect values from all nodes in the tree that match a partial function (post-order). */
    def collectPostOrder[B](node: CstNode)(pf: PartialFunction[CstNode, B]): List[B] =
        val buf = ArrayBuffer.empty[B]
        foldRight(node)(())((n, _) => if pf.isDefinedAt(n) then buf += pf(n))
        buf.toList

    /** Count all nodes in the tree. */
    def count(node: CstNode): Int = foldLeft(node)(0)((n, _) => n + 1)

/** Extension methods for visiting and folding over CST nodes. */
extension (node: CstNode)
    def visit[A](visitor: CstVisitor[A]): A                           = CstVisitor.visit(node, visitor)
    def foldLeft[A](init: A)(f: (A, CstNode) => A): A                 = CstVisitor.foldLeft(node)(init)(f)
    def foldRight[A](init: A)(f: (CstNode, A) => A): A                = CstVisitor.foldRight(node)(init)(f)
    def fold[A](init: A)(f: (A, CstNode) => A): A                     = CstVisitor.fold(node)(init)(f)
    def children: List[CstNode]                                       = CstVisitor.children(node)
    def collect[B](pf: PartialFunction[CstNode, B]): List[B]          = CstVisitor.collect(node)(pf)
    def collectPostOrder[B](pf: PartialFunction[CstNode, B]): List[B] = CstVisitor.collectPostOrder(node)(pf)
    def count: Int                                                    = CstVisitor.count(node)
