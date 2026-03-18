---
title: Visitor & Fold Guide
---

# Visitor & Fold Guide

Ascribe provides visitor traits and fold operations for both the AST and ASG, enabling type-safe tree traversal without manual recursion. All fold operations use trampolining (`scala.util.control.TailCalls`) for stack safety.

## AstVisitor

The `AstVisitor[A]` trait defines methods for each AST node type, with hierarchical defaults:

```scala
trait AstVisitor[A]:
  def visitNode(node: AstNode): A              // must implement

  def visitDocument(node: Document): A         = visitNode(node)
  def visitBlock(node: Block): A               = visitNode(node)
  def visitInline(node: Inline): A             = visitNode(node)

  def visitHeading(node: Heading): A           = visitBlock(node)
  def visitParagraph(node: Paragraph): A       = visitBlock(node)
  def visitSection(node: Section): A           = visitBlock(node)

  def visitText(node: Text): A                 = visitInline(node)
  def visitBold(node: Bold): A                 = visitInline(node)
  // ... etc.
```

Override only the methods you need. Unoverridden methods delegate up the hierarchy: `visitParagraph` calls `visitBlock`, which calls `visitNode`.

### Dispatching

Use `AstVisitor.visit` or the extension method:

```scala
val result = myNode.visit(myVisitor)
// or
val result = AstVisitor.visit(myNode, myVisitor)
```

## AsgVisitor

The `AsgVisitor[A]` trait follows the same pattern for ASG nodes:

```scala
trait AsgVisitor[A]:
  def visitNode(node: Node): A

  def visitDocument(node: Document): A   = visitNode(node)
  def visitBlock(node: Block): A         = visitNode(node)
  def visitInline(node: Inline): A       = visitNode(node)

  def visitParagraph(node: Paragraph): A = visitBlock(node)
  def visitSpan(node: Span): A           = visitInline(node)
  def visitText(node: Text): A           = visitInline(node)
  // ... covers all 25+ ASG node types
```

## Fold Operations

Both `AstVisitor` and `AsgVisitor` companion objects provide fold operations. These are stack-safe via trampolining.

### foldLeft (Pre-Order)

Visits each node before its children, accumulating left-to-right:

```scala
val totalNodes = AstVisitor.foldLeft(doc)(0)((count, _) => count + 1)
// or via extension method:
val totalNodes = doc.foldLeft(0)((count, _) => count + 1)
```

### foldRight (Post-Order)

Visits children before their parent, accumulating right-to-left:

```scala
val result = doc.foldRight(List.empty[String]) { (node, acc) =>
  node match
    case t: Text => t.content :: acc
    case _       => acc
}
```

### fold

Alias for `foldLeft`:

```scala
val count = doc.fold(0)((n, _) => n + 1)
```

### collect

Collects values from all nodes matching a partial function (pre-order):

```scala
// Extract all text content from an AST document
val texts = AstVisitor.collect(doc) {
  case t: Text => t.content
}
// Returns: List[String]
```

### collectPostOrder

Same as `collect` but in post-order:

```scala
val texts = doc.collectPostOrder {
  case t: Text => t.content
}
```

### count

Count all nodes in a subtree:

```scala
val nodeCount = doc.count
```

## Extension Methods

All operations are available as extension methods on `AstNode` and `Node`:

```scala
import io.eleven19.ascribe.ast.*

val doc: Document = ???
doc.visit(myVisitor)
doc.foldLeft(init)(f)
doc.foldRight(init)(f)
doc.fold(init)(f)
doc.children            // List[AstNode] -- direct children
doc.collect(pf)
doc.collectPostOrder(pf)
doc.count
```

The same extensions exist for ASG `Node`:

```scala
import io.eleven19.ascribe.asg.*

val asgDoc: Document = ???
asgDoc.visit(myAsgVisitor)
asgDoc.foldLeft(init)(f)
asgDoc.children         // Chunk[Node] -- direct children
asgDoc.collect(pf)
asgDoc.count
```

## Practical Examples

### Extract all text from a document

```scala
val allText = doc.collect { case t: ast.Text => t.content }
println(allText.mkString(" "))
```

### Count paragraphs

```scala
val paragraphCount = doc.foldLeft(0) { (count, node) =>
  node match
    case _: ast.Paragraph => count + 1
    case _                => count
}
```

### Find all headings with their levels

```scala
val headings = doc.collect {
  case h: ast.Heading => (h.level, h.title.collect { case t: ast.Text => t.content }.mkString)
}
// Returns: List[(Int, String)]
```

### Custom visitor: collect section titles from ASG

```scala
val sectionTitles = new AsgVisitor[Option[String]]:
  def visitNode(node: Node): Option[String] = None
  override def visitSection(node: Section): Option[String] =
    node.title.map(_.collect { case t: Text => t.value }.mkString)

asgDoc.children.flatMap(child => child.visit(sectionTitles))
```
