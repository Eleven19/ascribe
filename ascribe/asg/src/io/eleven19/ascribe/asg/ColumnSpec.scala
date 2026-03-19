package io.eleven19.ascribe.asg

import zio.blocks.schema.Schema

enum HAlign derives Schema:
    case Left, Center, Right

enum VAlign derives Schema:
    case Top, Middle, Bottom

case class ColumnSpec(
    width: Option[Int] = None,
    halign: Option[HAlign] = None,
    valign: Option[VAlign] = None
) derives Schema
