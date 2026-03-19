package io.eleven19.ascribe.asg

import zio.blocks.schema.Schema

enum HAlign derives Schema:
    case Left, Center, Right

enum VAlign derives Schema:
    case Top, Middle, Bottom

/** Content style for table columns and cells. */
enum CellStyle derives Schema:
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
    given Schema[ColSpan]                 = summon[Schema[Int]].transform[ColSpan](identity, identity)
    extension (c: ColSpan) def value: Int = c

/** Row span factor — number of consecutive rows a cell spans. */
opaque type RowSpan = Int

object RowSpan:
    def apply(n: Int): RowSpan            = n
    given Schema[RowSpan]                 = summon[Schema[Int]].transform[RowSpan](identity, identity)
    extension (r: RowSpan) def value: Int = r

case class ColumnSpec(
    width: Option[Int] = None,
    halign: Option[HAlign] = None,
    valign: Option[VAlign] = None,
    style: Option[CellStyle] = None
) derives Schema
