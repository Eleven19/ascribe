Feature: Block - Table
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name     | input_file                                                                       | output_file                                                                        |
      | basic-2x2     | ascribe/tck-runner/test/resources/tests/block/table/basic-2x2-input.adoc         | ascribe/tck-runner/test/resources/tests/block/table/basic-2x2-output.json          |
      | header-row    | ascribe/tck-runner/test/resources/tests/block/table/header-row-input.adoc        | ascribe/tck-runner/test/resources/tests/block/table/header-row-output.json         |
      | single-column | ascribe/tck-runner/test/resources/tests/block/table/single-column-input.adoc     | ascribe/tck-runner/test/resources/tests/block/table/single-column-output.json      |
      | inline-markup | ascribe/tck-runner/test/resources/tests/block/table/inline-markup-input.adoc     | ascribe/tck-runner/test/resources/tests/block/table/inline-markup-output.json      |
      | empty-cells   | ascribe/tck-runner/test/resources/tests/block/table/empty-cells-input.adoc       | ascribe/tck-runner/test/resources/tests/block/table/empty-cells-output.json        |
      | cols-equal     | ascribe/tck-runner/test/resources/tests/block/table/cols-equal-input.adoc        | ascribe/tck-runner/test/resources/tests/block/table/cols-equal-output.json         |
      | cols-widths    | ascribe/tck-runner/test/resources/tests/block/table/cols-widths-input.adoc       | ascribe/tck-runner/test/resources/tests/block/table/cols-widths-output.json        |
      | cols-alignment | ascribe/tck-runner/test/resources/tests/block/table/cols-alignment-input.adoc    | ascribe/tck-runner/test/resources/tests/block/table/cols-alignment-output.json     |
      | cols-valign    | ascribe/tck-runner/test/resources/tests/block/table/cols-valign-input.adoc       | ascribe/tck-runner/test/resources/tests/block/table/cols-valign-output.json        |
      | cols-mixed        | ascribe/tck-runner/test/resources/tests/block/table/cols-mixed-input.adoc        | ascribe/tck-runner/test/resources/tests/block/table/cols-mixed-output.json         |
      | table-title       | ascribe/tck-runner/test/resources/tests/block/table/table-title-input.adoc       | ascribe/tck-runner/test/resources/tests/block/table/table-title-output.json        |
      | header-explicit   | ascribe/tck-runner/test/resources/tests/block/table/header-explicit-input.adoc   | ascribe/tck-runner/test/resources/tests/block/table/header-explicit-output.json    |
      | header-noheader   | ascribe/tck-runner/test/resources/tests/block/table/header-noheader-input.adoc   | ascribe/tck-runner/test/resources/tests/block/table/header-noheader-output.json    |
      | footer            | ascribe/tck-runner/test/resources/tests/block/table/footer-input.adoc            | ascribe/tck-runner/test/resources/tests/block/table/footer-output.json             |
      | header-footer     | ascribe/tck-runner/test/resources/tests/block/table/header-footer-input.adoc     | ascribe/tck-runner/test/resources/tests/block/table/header-footer-output.json      |
