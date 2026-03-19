package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import scala.collection.mutable.ArrayBuffer
import scala.util.control.TailCalls.*

/** Visitor trait for ASG nodes. Each method has a default that delegates up the type hierarchy, so you only need to
  * override the methods you care about.
  *
  * Hierarchy: Node → Document, Block, Inline. Override `visitNode` to handle all nodes, `visitBlock` for all blocks,
  * etc.
  */
trait AsgVisitor[A]:

    // --- Top-level ---
    def visitNode(node: Node): A

    // --- Category defaults ---
    def visitDocument(node: Document): A = visitNode(node)
    def visitBlock(node: Block): A       = visitNode(node)
    def visitInline(node: Inline): A     = visitNode(node)

    // --- Block types ---
    def visitSection(node: Section): A       = visitBlock(node)
    def visitHeading(node: Heading): A       = visitBlock(node)
    def visitParagraph(node: Paragraph): A   = visitBlock(node)
    def visitListing(node: Listing): A       = visitBlock(node)
    def visitLiteral(node: Literal): A       = visitBlock(node)
    def visitPass(node: Pass): A             = visitBlock(node)
    def visitStem(node: Stem): A             = visitBlock(node)
    def visitVerse(node: Verse): A           = visitBlock(node)
    def visitSidebar(node: Sidebar): A       = visitBlock(node)
    def visitExample(node: Example): A       = visitBlock(node)
    def visitAdmonition(node: Admonition): A = visitBlock(node)
    def visitOpen(node: Open): A             = visitBlock(node)
    def visitQuote(node: Quote): A           = visitBlock(node)
    def visitList(node: List): A             = visitBlock(node)
    def visitDList(node: DList): A           = visitBlock(node)
    def visitListItem(node: ListItem): A     = visitBlock(node)
    def visitDListItem(node: DListItem): A   = visitBlock(node)
    def visitBreak(node: Break): A           = visitBlock(node)
    def visitAudio(node: Audio): A           = visitBlock(node)
    def visitVideo(node: Video): A           = visitBlock(node)
    def visitImage(node: Image): A           = visitBlock(node)
    def visitToc(node: Toc): A               = visitBlock(node)
    def visitTable(node: Table): A           = visitBlock(node)
    def visitTableRow(node: TableRow): A     = visitBlock(node)
    def visitTableCell(node: TableCell): A   = visitBlock(node)

    // --- Inline types ---
    def visitSpan(node: Span): A       = visitInline(node)
    def visitRef(node: Ref): A         = visitInline(node)
    def visitText(node: Text): A       = visitInline(node)
    def visitCharRef(node: CharRef): A = visitInline(node)
    def visitRaw(node: Raw): A         = visitInline(node)

/** Utilities for visiting and folding over ASG trees.
  *
  * Traversal uses trampolining (`scala.util.control.TailCalls`) for stack safety, so arbitrarily deep ASG trees can be
  * processed without risk of stack overflow.
  */
object AsgVisitor:

    /** Dispatch a node to the appropriate visitor method. */
    def visit[A](node: Node, visitor: AsgVisitor[A]): A = node match
        case n: Document   => visitor.visitDocument(n)
        case n: Section    => visitor.visitSection(n)
        case n: Heading    => visitor.visitHeading(n)
        case n: Paragraph  => visitor.visitParagraph(n)
        case n: Listing    => visitor.visitListing(n)
        case n: Literal    => visitor.visitLiteral(n)
        case n: Pass       => visitor.visitPass(n)
        case n: Stem       => visitor.visitStem(n)
        case n: Verse      => visitor.visitVerse(n)
        case n: Sidebar    => visitor.visitSidebar(n)
        case n: Example    => visitor.visitExample(n)
        case n: Admonition => visitor.visitAdmonition(n)
        case n: Open       => visitor.visitOpen(n)
        case n: Quote      => visitor.visitQuote(n)
        case n: List       => visitor.visitList(n)
        case n: DList      => visitor.visitDList(n)
        case n: ListItem   => visitor.visitListItem(n)
        case n: DListItem  => visitor.visitDListItem(n)
        case n: Break      => visitor.visitBreak(n)
        case n: Audio      => visitor.visitAudio(n)
        case n: Video      => visitor.visitVideo(n)
        case n: Image      => visitor.visitImage(n)
        case n: Toc        => visitor.visitToc(n)
        case n: Table      => visitor.visitTable(n)
        case n: TableRow   => visitor.visitTableRow(n)
        case n: TableCell  => visitor.visitTableCell(n)
        case n: Span       => visitor.visitSpan(n)
        case n: Ref        => visitor.visitRef(n)
        case n: Text       => visitor.visitText(n)
        case n: CharRef    => visitor.visitCharRef(n)
        case n: Raw        => visitor.visitRaw(n)

    /** Return all direct child nodes of a node. */
    def children(node: Node): Chunk[Node] =
        def optInlines(opt: Option[Chunk[Inline]]): Chunk[Node] =
            opt.getOrElse(Chunk.empty)
        node match
            case d: Document =>
                d.header.flatMap(_.title).getOrElse(Chunk.empty[Inline]).asInstanceOf[Chunk[Node]] ++ d.blocks
            case s: Section    => optInlines(s.title) ++ optInlines(s.reftext) ++ s.blocks
            case h: Heading    => optInlines(h.title) ++ optInlines(h.reftext)
            case p: Paragraph  => optInlines(p.title) ++ optInlines(p.reftext) ++ p.inlines
            case l: Listing    => optInlines(l.title) ++ optInlines(l.reftext) ++ l.inlines
            case l: Literal    => optInlines(l.title) ++ optInlines(l.reftext) ++ l.inlines
            case p: Pass       => optInlines(p.title) ++ optInlines(p.reftext) ++ p.inlines
            case s: Stem       => optInlines(s.title) ++ optInlines(s.reftext) ++ s.inlines
            case v: Verse      => optInlines(v.title) ++ optInlines(v.reftext) ++ v.inlines
            case s: Sidebar    => optInlines(s.title) ++ optInlines(s.reftext) ++ s.blocks
            case e: Example    => optInlines(e.title) ++ optInlines(e.reftext) ++ e.blocks
            case a: Admonition => optInlines(a.title) ++ optInlines(a.reftext) ++ a.blocks
            case o: Open       => optInlines(o.title) ++ optInlines(o.reftext) ++ o.blocks
            case q: Quote      => optInlines(q.title) ++ optInlines(q.reftext) ++ q.blocks
            case l: List       => optInlines(l.title) ++ optInlines(l.reftext) ++ l.items
            case d: DList      => optInlines(d.title) ++ optInlines(d.reftext) ++ d.items
            case li: ListItem =>
                optInlines(li.title) ++ optInlines(li.reftext) ++ li.principal ++ li.blocks
            case di: DListItem =>
                optInlines(di.title) ++ optInlines(di.reftext) ++
                    di.terms.flatMap(identity) ++ di.principal.getOrElse(Chunk.empty) ++ di.blocks
            case b: Break => optInlines(b.title) ++ optInlines(b.reftext)
            case a: Audio => optInlines(a.title) ++ optInlines(a.reftext)
            case v: Video => optInlines(v.title) ++ optInlines(v.reftext)
            case i: Image => optInlines(i.title) ++ optInlines(i.reftext)
            case t: Toc   => optInlines(t.title) ++ optInlines(t.reftext)
            case t: Table =>
                optInlines(t.title) ++ optInlines(t.reftext) ++ t.header.getOrElse(Chunk.empty) ++ t.rows ++ t.footer
                    .getOrElse(Chunk.empty)
            case tr: TableRow  => tr.cells
            case tc: TableCell => tc.inlines
            case s: Span       => s.inlines
            case r: Ref        => r.inlines
            case _: Text       => Chunk.empty
            case _: CharRef    => Chunk.empty
            case _: Raw        => Chunk.empty

    /** Pre-order left fold: visits each node before its children, accumulating left-to-right. Stack-safe via
      * trampolining.
      */
    def foldLeft[A](node: Node)(init: A)(f: (A, Node) => A): A =
        def go(n: Node, acc: A): TailRec[A] =
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
    def foldRight[A](node: Node)(init: A)(f: (Node, A) => A): A =
        def go(n: Node, acc: A): TailRec[A] =
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
    def fold[A](node: Node)(init: A)(f: (A, Node) => A): A = foldLeft(node)(init)(f)

    /** Collect values from all nodes in the tree that match a partial function (pre-order). */
    def collect[B](node: Node)(pf: PartialFunction[Node, B]): Chunk[B] =
        val buf = ArrayBuffer.empty[B]
        foldLeft(node)(()) { (_, n) =>
            if pf.isDefinedAt(n) then buf += pf(n)
        }
        Chunk.from(buf)

    /** Collect values from all nodes in the tree that match a partial function (post-order). */
    def collectPostOrder[B](node: Node)(pf: PartialFunction[Node, B]): Chunk[B] =
        val buf = ArrayBuffer.empty[B]
        foldRight(node)(()) { (n, _) =>
            if pf.isDefinedAt(n) then buf += pf(n)
        }
        Chunk.from(buf)

    /** Count all nodes in the tree. */
    def count(node: Node): Int =
        foldLeft(node)(0)((n, _) => n + 1)

/** Extension methods for visiting and folding over ASG nodes. */
extension (node: Node)

    /** Apply a visitor to this node. */
    def visit[A](visitor: AsgVisitor[A]): A = AsgVisitor.visit(node, visitor)

    /** Pre-order left fold over this node and all descendants. */
    def foldLeft[A](init: A)(f: (A, Node) => A): A = AsgVisitor.foldLeft(node)(init)(f)

    /** Post-order right fold over this node and all descendants. */
    def foldRight[A](init: A)(f: (Node, A) => A): A = AsgVisitor.foldRight(node)(init)(f)

    /** Pre-order fold (alias for `foldLeft`). */
    def fold[A](init: A)(f: (A, Node) => A): A = AsgVisitor.fold(node)(init)(f)

    /** Return all direct child nodes. */
    def children: Chunk[Node] = AsgVisitor.children(node)

    /** Collect values from all descendant nodes matching a partial function (pre-order). */
    def collect[B](pf: PartialFunction[Node, B]): Chunk[B] = AsgVisitor.collect(node)(pf)

    /** Collect values from all descendant nodes matching a partial function (post-order). */
    def collectPostOrder[B](pf: PartialFunction[Node, B]): Chunk[B] = AsgVisitor.collectPostOrder(node)(pf)

    /** Count all nodes in this subtree. */
    def count: Int = AsgVisitor.count(node)
