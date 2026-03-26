package io.eleven19.ascribe.ast

import scala.collection.mutable.ArrayBuffer
import scala.util.control.TailCalls.*

/** Visitor trait for AST nodes. Each method has a default that delegates up the type hierarchy, so you only need to
  * override the methods you care about.
  *
  * Hierarchy: AstNode → Document, Block, Inline, ListItem. Override `visitNode` to handle all nodes, `visitBlock` for
  * all blocks, etc.
  */
trait AstVisitor[A]:

    // --- Top-level ---
    def visitNode(node: AstNode): A

    // --- Category defaults ---
    def visitDocument(node: Document): A             = visitNode(node)
    def visitDocumentHeader(node: DocumentHeader): A = visitNode(node)
    def visitBlock(node: Block): A                   = visitNode(node)
    def visitInline(node: Inline): A                 = visitNode(node)
    def visitListItem(node: ListItem): A             = visitNode(node)

    // --- Block types ---
    def visitHeading(node: Heading): A             = visitBlock(node)
    def visitSection(node: Section): A             = visitBlock(node)
    def visitParagraph(node: Paragraph): A         = visitBlock(node)
    def visitListing(node: Listing): A             = visitBlock(node)
    def visitLiteral(node: Literal): A             = visitBlock(node)
    def visitSidebar(node: Sidebar): A             = visitBlock(node)
    def visitExample(node: Example): A             = visitBlock(node)
    def visitQuote(node: Quote): A                 = visitBlock(node)
    def visitOpen(node: Open): A                   = visitBlock(node)
    def visitComment(node: Comment): A             = visitBlock(node)
    def visitPass(node: Pass): A                   = visitBlock(node)
    def visitUnorderedList(node: UnorderedList): A = visitBlock(node)
    def visitOrderedList(node: OrderedList): A     = visitBlock(node)
    def visitAdmonition(node: Admonition): A       = visitBlock(node)
    def visitTable(node: Table): A                 = visitBlock(node)
    def visitTableRow(node: TableRow): A           = visitNode(node)
    def visitTableCell(node: TableCell): A         = visitNode(node)
    def visitAttributeList(node: AttributeList): A = visitNode(node)
    def visitTitle(node: Title): A                 = visitNode(node)

    // --- Inline types ---
    def visitText(node: Text): A                       = visitInline(node)
    def visitBold(node: Bold): A                       = visitInline(node)
    def visitConstrainedBold(node: ConstrainedBold): A = visitInline(node)
    def visitItalic(node: Italic): A                   = visitInline(node)
    def visitMono(node: Mono): A                       = visitInline(node)

/** Utilities for visiting and folding over AST trees.
  *
  * Traversal uses trampolining (`scala.util.control.TailCalls`) for stack safety, so arbitrarily deep AST trees can be
  * processed without risk of stack overflow.
  */
object AstVisitor:

    /** Dispatch a node to the appropriate visitor method. */
    def visit[A](node: AstNode, visitor: AstVisitor[A]): A = node match
        case n: Document        => visitor.visitDocument(n)
        case n: DocumentHeader  => visitor.visitDocumentHeader(n)
        case n: Section         => visitor.visitSection(n)
        case n: Heading         => visitor.visitHeading(n)
        case n: Paragraph       => visitor.visitParagraph(n)
        case n: Listing         => visitor.visitListing(n)
        case n: Literal         => visitor.visitLiteral(n)
        case n: Sidebar         => visitor.visitSidebar(n)
        case n: Example         => visitor.visitExample(n)
        case n: Quote           => visitor.visitQuote(n)
        case n: Open            => visitor.visitOpen(n)
        case n: Comment         => visitor.visitComment(n)
        case n: Pass            => visitor.visitPass(n)
        case n: UnorderedList   => visitor.visitUnorderedList(n)
        case n: OrderedList     => visitor.visitOrderedList(n)
        case n: Admonition      => visitor.visitAdmonition(n)
        case n: Table           => visitor.visitTable(n)
        case n: TableRow        => visitor.visitTableRow(n)
        case n: TableCell       => visitor.visitTableCell(n)
        case n: AttributeList   => visitor.visitAttributeList(n)
        case n: Title           => visitor.visitTitle(n)
        case n: Text            => visitor.visitText(n)
        case n: Bold            => visitor.visitBold(n)
        case n: ConstrainedBold => visitor.visitConstrainedBold(n)
        case n: Italic          => visitor.visitItalic(n)
        case n: Mono            => visitor.visitMono(n)
        case n: ListItem        => visitor.visitListItem(n)

    /** Return all direct child nodes of a node. */
    def children(node: AstNode): List[AstNode] = node match
        case d: Document        => d.header.toList ++ d.blocks
        case dh: DocumentHeader => dh.title
        case s: Section         => s.title ++ s.blocks
        case h: Heading         => h.title
        case p: Paragraph       => p.content
        case _: Listing         => Nil // verbatim content, no child nodes
        case _: Literal         => Nil // verbatim content, no child nodes
        case _: Comment         => Nil // discarded content, no child nodes
        case _: Pass            => Nil // raw content, no child nodes
        case sb: Sidebar        => sb.blocks
        case eb: Example        => eb.blocks
        case qb: Quote          => qb.blocks
        case ob: Open           => ob.blocks
        case u: UnorderedList   => u.items
        case o: OrderedList     => o.items
        case a: Admonition      => a.blocks
        case tb: Table          => tb.title.toList ++ tb.attributes.toList ++ tb.rows
        case tr: TableRow       => tr.cells
        case tc: TableCell =>
            tc.content match
                case CellContent.Inlines(content) => content
                case CellContent.Blocks(blocks)   => blocks
        case _: AttributeList    => Nil
        case bt: Title           => bt.content
        case t: Text             => Nil
        case b: Bold             => b.content
        case cb: ConstrainedBold => cb.content
        case i: Italic           => i.content
        case m: Mono             => m.content
        case li: ListItem        => li.content

    /** Pre-order left fold: visits each node before its children, accumulating left-to-right. Stack-safe via
      * trampolining.
      */
    def foldLeft[A](node: AstNode)(init: A)(f: (A, AstNode) => A): A =
        def go(n: AstNode, acc: A): TailRec[A] =
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
    def foldRight[A](node: AstNode)(init: A)(f: (AstNode, A) => A): A =
        def go(n: AstNode, acc: A): TailRec[A] =
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
    def fold[A](node: AstNode)(init: A)(f: (A, AstNode) => A): A = foldLeft(node)(init)(f)

    /** Collect values from all nodes in the tree that match a partial function (pre-order). */
    def collect[B](node: AstNode)(pf: PartialFunction[AstNode, B]): List[B] =
        val buf = ArrayBuffer.empty[B]
        foldLeft(node)(()) { (_, n) =>
            if pf.isDefinedAt(n) then buf += pf(n)
        }
        buf.toList

    /** Collect values from all nodes in the tree that match a partial function (post-order). */
    def collectPostOrder[B](node: AstNode)(pf: PartialFunction[AstNode, B]): List[B] =
        val buf = ArrayBuffer.empty[B]
        foldRight(node)(()) { (n, _) =>
            if pf.isDefinedAt(n) then buf += pf(n)
        }
        buf.toList

    /** Count all nodes in the tree. */
    def count(node: AstNode): Int =
        foldLeft(node)(0)((n, _) => n + 1)

/** Extension methods for visiting and folding over AST nodes. */
extension (node: AstNode)

    /** Apply a visitor to this node. */
    def visit[A](visitor: AstVisitor[A]): A = AstVisitor.visit(node, visitor)

    /** Pre-order left fold over this node and all descendants. */
    def foldLeft[A](init: A)(f: (A, AstNode) => A): A = AstVisitor.foldLeft(node)(init)(f)

    /** Post-order right fold over this node and all descendants. */
    def foldRight[A](init: A)(f: (AstNode, A) => A): A = AstVisitor.foldRight(node)(init)(f)

    /** Pre-order fold (alias for `foldLeft`). */
    def fold[A](init: A)(f: (A, AstNode) => A): A = AstVisitor.fold(node)(init)(f)

    /** Return all direct child nodes. */
    def children: List[AstNode] = AstVisitor.children(node)

    /** Collect values from all descendant nodes matching a partial function (pre-order). */
    def collect[B](pf: PartialFunction[AstNode, B]): List[B] = AstVisitor.collect(node)(pf)

    /** Collect values from all descendant nodes matching a partial function (post-order). */
    def collectPostOrder[B](pf: PartialFunction[AstNode, B]): List[B] = AstVisitor.collectPostOrder(node)(pf)

    /** Count all nodes in this subtree. */
    def count: Int = AstVisitor.count(node)
