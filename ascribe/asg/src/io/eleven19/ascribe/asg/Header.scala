package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

/** Document header with optional title and authors.
  * Not a Node — embedded within Document.
  * All fields are optional per the ASG schema.
  */
case class Header(
    title: Option[Chunk[Inline]] = None,
    authors: Chunk[Author] = Chunk.empty,
    location: Option[Location] = None
)

/** Author information from the document header. */
case class Author(
    fullname: Option[String] = None,
    initials: Option[String] = None,
    firstname: Option[String] = None,
    middlename: Option[String] = None,
    lastname: Option[String] = None,
    address: Option[String] = None
) derives Schema
