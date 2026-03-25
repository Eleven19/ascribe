package io.eleven19.ascribe.ast

/** A node in a document tree. Either a single document (leaf) or a directory (branch). */
enum TreeNode derives CanEqual:
    case DocLeaf(path: DocumentPath, document: Document)
    case TreeBranch(path: DocumentPath, children: List[TreeNode])

/** A tree of AsciiDoc documents, modeling a directory of `.adoc` files.
  *
  * Inspired by Laika's DocumentTree concept but using our AST types. All operations are pure — effectful variants live
  * in the pipeline module.
  */
case class DocumentTree(root: TreeNode) derives CanEqual:

    /** All documents in the tree as a flat list of (path, document) pairs. */
    def allDocuments: List[(DocumentPath, Document)] =
        def go(node: TreeNode): List[(DocumentPath, Document)] = node match
            case TreeNode.DocLeaf(p, d)       => List((p, d))
            case TreeNode.TreeBranch(_, kids) => kids.flatMap(go)
        go(root)

    /** Map a transformation over every document in the tree. */
    def mapDocuments(f: Document => Document): DocumentTree =
        def go(node: TreeNode): TreeNode = node match
            case TreeNode.DocLeaf(p, d)       => TreeNode.DocLeaf(p, f(d))
            case TreeNode.TreeBranch(p, kids) => TreeNode.TreeBranch(p, kids.map(go))
        DocumentTree(go(root))

    /** Filter documents, keeping only those whose path satisfies the predicate. */
    def filter(pred: DocumentPath => Boolean): DocumentTree =
        def go(node: TreeNode): Option[TreeNode] = node match
            case leaf @ TreeNode.DocLeaf(p, _) =>
                if pred(p) then Some(leaf) else None
            case TreeNode.TreeBranch(p, kids) =>
                val filtered = kids.flatMap(go)
                if filtered.nonEmpty then Some(TreeNode.TreeBranch(p, filtered))
                else None
        DocumentTree(go(root).getOrElse(TreeNode.TreeBranch(DocumentPath.root, Nil)))

    /** Collect values from documents matching a partial function. */
    def collect[B](pf: PartialFunction[(DocumentPath, Document), B]): List[B] =
        allDocuments.collect(pf)

    /** Find a document by path. */
    def get(path: DocumentPath): Option[Document] =
        allDocuments.collectFirst { case (p, d) if p == path => d }

    /** Number of documents in the tree. */
    def size: Int = allDocuments.size

object DocumentTree:

    /** Create a single-document tree. */
    def single(path: DocumentPath, document: Document): DocumentTree =
        DocumentTree(TreeNode.DocLeaf(path, document))

    /** Create a single-document tree with a default path. */
    def single(document: Document): DocumentTree =
        single(DocumentPath("document.adoc"), document)

    /** Create a tree from a flat list of (path, document) pairs. */
    def fromDocuments(docs: List[(DocumentPath, Document)]): DocumentTree =
        DocumentTree(
            TreeNode.TreeBranch(
                DocumentPath.root,
                docs.map((p, d) => TreeNode.DocLeaf(p, d))
            )
        )

    /** An empty document tree. */
    val empty: DocumentTree =
        DocumentTree(TreeNode.TreeBranch(DocumentPath.root, Nil))
