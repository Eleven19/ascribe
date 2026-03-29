Feature: AsciiDoc document parsing

  The AsciiDoc parser accepts a subset of AsciiDoc source text and produces a
  structured Document AST. It handles headings, paragraphs, unordered and
  ordered lists, and inline markup (bold, italic, monospace).

  # ---------------------------------------------------------------------------
  # Headings
  # ---------------------------------------------------------------------------

  Scenario: Level-1 heading
    Given an AsciiDoc source of "= My Title"
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a level 1 heading with title "My Title"

  Scenario: Level-2 heading
    Given an AsciiDoc source of "== Chapter One"
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a level 2 heading with title "Chapter One"

  Scenario: Level-5 heading
    Given an AsciiDoc source of "===== Deep Section"
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a level 5 heading with title "Deep Section"

  # ---------------------------------------------------------------------------
  # Paragraphs
  # ---------------------------------------------------------------------------

  Scenario: Single paragraph
    Given an AsciiDoc source of "Hello, world."
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a paragraph containing the text "Hello, world."

  Scenario: Paragraph with bold inline
    Given an AsciiDoc source of "Use **parsley** to parse."
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a paragraph containing bold text "parsley"

  Scenario: Paragraph with italic inline
    Given an AsciiDoc source of "This is __important__."
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a paragraph containing italic text "important"

  Scenario: Paragraph with monospace inline
    Given an AsciiDoc source of "Use ``apt-get`` to install."
    When the source is parsed
    Then the document contains 1 block
    And block 1 is a paragraph containing monospace text "apt-get"

  # ---------------------------------------------------------------------------
  # Lists
  # ---------------------------------------------------------------------------

  Scenario: Single-item unordered list
    Given an AsciiDoc source of "* only item"
    When the source is parsed
    Then the document contains 1 block
    And block 1 is an unordered list with 1 item
    And unordered list item 1 contains the text "only item"

  Scenario: Multi-item unordered list
    Given the following AsciiDoc source:
      """
      * alpha
      * beta
      * gamma
      """
    When the source is parsed
    Then the document contains 1 block
    And block 1 is an unordered list with 3 items

  Scenario: Single-item ordered list
    Given an AsciiDoc source of ". first step"
    When the source is parsed
    Then the document contains 1 block
    And block 1 is an ordered list with 1 item

  # ---------------------------------------------------------------------------
  # Multi-block documents
  # ---------------------------------------------------------------------------

  Scenario: Heading followed by paragraph
    Given the following AsciiDoc source:
      """
      = Introduction

      This is the intro paragraph.
      """
    When the source is parsed
    Then the document contains 2 blocks
    And block 1 is a level 1 heading with title "Introduction"
    And block 2 is a paragraph containing the text "This is the intro paragraph."

  Scenario: Complete document with heading, paragraph, and list
    Given the following AsciiDoc source:
      """
      = Guide

      Read the steps:

      * step one
      * step two
      """
    When the source is parsed
    Then the document contains 3 blocks
    And block 1 is a level 1 heading with title "Guide"
    And block 2 is a paragraph containing the text "Read the steps:"
    And block 3 is an unordered list with 2 items
