package io.eleven19.ascribe.ast

/** A path identifying a document's location within a DocumentTree.
  *
  * Segments represent directory nesting. E.g., `DocumentPath("chapters", "intro.adoc")` represents a document at
  * `chapters/intro.adoc` within the tree.
  */
opaque type DocumentPath = List[String]

object DocumentPath:

    def apply(segments: String*): DocumentPath = segments.toList

    def fromString(path: String): DocumentPath =
        path.split("/").filter(_.nonEmpty).toList

    val root: DocumentPath = List.empty

    extension (p: DocumentPath)
        def segments: List[String]           = p
        def name: String                     = p.lastOption.getOrElse("")
        def parent: DocumentPath             = if p.isEmpty then p else p.init
        def /(segment: String): DocumentPath = p :+ segment
        def isRoot: Boolean                  = p.isEmpty
        def depth: Int                       = p.length
        def render: String                   = p.mkString("/")

    given CanEqual[DocumentPath, DocumentPath] = CanEqual.derived
