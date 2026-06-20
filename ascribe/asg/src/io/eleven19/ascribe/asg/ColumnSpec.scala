package io.eleven19.ascribe.asg


enum HAlign:
    case Left, Center, Right

enum VAlign:
    case Top, Middle, Bottom

/** Content style for table columns and cells. */
enum CellStyle:
    case Default, AsciiDoc, Emphasis, Header, Literal, Monospace, Strong

object CellStyle:

    def fromChar(c: Char): Option[CellStyle] = c match
        case 'd' => Some(Default)
        case 'a' => Some(AsciiDoc)
        case 'e' => Some(Emphasis)
        case 'h' => Some(Header)
        case 'l' => Some(Literal)
        case 'm' => Some(Monospace)
        case 's' => Some(Strong)
        case _   => None

/** Column span factor — number of consecutive columns a cell spans. */
opaque type ColSpan = Int

object ColSpan:
    def apply(n: Int): ColSpan            = n
    extension (c: ColSpan) def value: Int = c

/** Row span factor — number of consecutive rows a cell spans. */
opaque type RowSpan = Int

object RowSpan:
    def apply(n: Int): RowSpan            = n
    extension (r: RowSpan) def value: Int = r

/** Duplication count — number of times a cell is duplicated. */
opaque type DupCount = Int

object DupCount:
    def apply(n: Int): DupCount            = n
    extension (d: DupCount) def value: Int = d

case class ColumnSpec(
    width: Option[Int] = None,
    halign: Option[HAlign] = None,
    valign: Option[VAlign] = None,
    style: Option[CellStyle] = None
)
