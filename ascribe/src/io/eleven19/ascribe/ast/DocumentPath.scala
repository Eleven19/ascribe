package io.eleven19.ascribe.ast

/** A path identifying a document's location within a DocumentTree.
  *
  * Segments represent directory nesting. E.g., `DocumentPath("chapters", "intro.adoc")` represents a document at
  * `chapters/intro.adoc` within the tree.
  *
  * Empty segments are silently filtered: `DocumentPath("a", "", "b")` produces the same path as `DocumentPath("a",
  * "b")`. `DocumentPath()` with no arguments produces `DocumentPath.root`.
  */
opaque type DocumentPath = List[String]

object DocumentPath:

    /** Create a path from segments. Empty segments are filtered out. */
    def apply(segments: String*): DocumentPath = segments.filter(_.nonEmpty).toList

    /** Parse a slash-separated path string. Double slashes are normalized (empty segments filtered). */
    def fromString(path: String): DocumentPath =
        path.split("/").filter(_.nonEmpty).toList

    /** The root (empty) path. */
    val root: DocumentPath = List.empty

    extension (p: DocumentPath)
        def segments: List[String] = p

        /** The last segment of the path, or `""` if root. */
        def name: String = p.lastOption.getOrElse("")

        /** The parent path, or root if already root. See also `parentOption`. */
        def parent: DocumentPath = if p.isEmpty then p else p.init

        /** The parent path, or `None` if already root. */
        def parentOption: Option[DocumentPath] = if p.isEmpty then None else Some(p.init)

        /** Append a segment. Empty segments are ignored. */
        def /(segment: String): DocumentPath =
            if segment.nonEmpty then p :+ segment else p

        def isRoot: Boolean = p.isEmpty
        def depth: Int      = p.length
        def render: String  = p.mkString("/")

        /** Whether `other` is within this path (i.e., this path is a prefix of `other`). */
        def contains(other: DocumentPath): Boolean =
            other.startsWith(p) && other.length > p.length

        /** Whether this path starts with `prefix`. */
        def startsWith(prefix: DocumentPath): Boolean =
            p.startsWith(prefix)

    given CanEqual[DocumentPath, DocumentPath] = CanEqual.derived
