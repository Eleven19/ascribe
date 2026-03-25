package io.eleven19.ascribe.ast

/** A node in a document tree. Either a single document (leaf) or a directory (branch). Both cases carry a `path` field
  * accessible via the `nodePath` extension without pattern matching.
  */
enum TreeNode derives CanEqual:
    case DocLeaf(path: DocumentPath, document: Document)
    case TreeBranch(path: DocumentPath, children: List[TreeNode])

object TreeNode:

    extension (node: TreeNode)

        /** The path of this node within the tree. */
        def nodePath: DocumentPath = node match
            case DocLeaf(p, _)    => p
            case TreeBranch(p, _) => p

/** A tree of AsciiDoc documents, modeling a directory of `.adoc` files.
  *
  * Inspired by Laika's DocumentTree concept but using our AST types. All operations are pure — effectful variants live
  * in the pipeline module.
  *
  * Note: `fromDocuments` produces a flat tree (all documents as direct children of root) regardless of path depth. The
  * `DocumentPath` on each leaf carries the full path, but the tree structure is not nested. Hierarchical grouping by
  * directory is a future enhancement.
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

    /** Filter documents, keeping only those whose path satisfies the predicate. Branches with no remaining children are
      * pruned. If the root itself is filtered out, returns `DocumentTree.empty`.
      */
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
        def go(node: TreeNode): Option[Document] = node match
            case TreeNode.DocLeaf(p, d)       => if p == path then Some(d) else None
            case TreeNode.TreeBranch(_, kids) => kids.collectFirst(Function.unlift(go))
        go(root)

    /** Number of documents in the tree. */
    def size: Int =
        def go(node: TreeNode): Int = node match
            case _: TreeNode.DocLeaf          => 1
            case TreeNode.TreeBranch(_, kids) => kids.map(go).sum
        go(root)

object DocumentTree:

    /** Create a single-document tree. */
    def single(path: DocumentPath, document: Document): DocumentTree =
        DocumentTree(TreeNode.DocLeaf(path, document))

    /** Create a single-document tree with a default path. Note: merging multiple trees created this way will produce
      * duplicate paths.
      */
    def single(document: Document): DocumentTree =
        single(DocumentPath("document.adoc"), document)

    /** Create a tree from a flat list of (path, document) pairs. The tree structure is flat — all documents are direct
      * children of a root branch. The paths on each leaf carry their full path.
      */
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
