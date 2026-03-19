package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import io.eleven19.ascribe.asg.{ColumnSpec, HAlign, VAlign}

object ColsParser:

    def parse(cols: String): Chunk[ColumnSpec] =
        val entries = cols.split(",").map(_.trim).toList
        Chunk.from(entries.flatMap(parseEntry))

    private def parseEntry(entry: String): List[ColumnSpec] =
        val multiplierPattern = """^(\d+)\*(.*)$""".r
        entry match
            case multiplierPattern(nStr, rest) =>
                val n    = nStr.toInt
                val spec = parseSpec(rest)
                List.fill(n)(spec)
            case _ =>
                List(parseSpec(entry))

    private def parseSpec(s: String): ColumnSpec =
        var remaining              = s
        var halign: Option[HAlign] = None
        var valign: Option[VAlign] = None
        var width: Option[Int]     = None

        if remaining.startsWith("<") then
            halign = Some(HAlign.Left); remaining = remaining.drop(1)
        else if remaining.startsWith("^") then
            halign = Some(HAlign.Center); remaining = remaining.drop(1)
        else if remaining.startsWith(">") then
            halign = Some(HAlign.Right); remaining = remaining.drop(1)

        if remaining.startsWith(".<") then
            valign = Some(VAlign.Top); remaining = remaining.drop(2)
        else if remaining.startsWith(".^") then
            valign = Some(VAlign.Middle); remaining = remaining.drop(2)
        else if remaining.startsWith(".>") then
            valign = Some(VAlign.Bottom); remaining = remaining.drop(2)

        if remaining.nonEmpty && remaining.forall(_.isDigit) then width = Some(remaining.toInt)

        ColumnSpec(width, halign, valign)
