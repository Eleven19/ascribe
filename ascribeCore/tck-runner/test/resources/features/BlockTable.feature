Feature: Block - Table
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name     | input_file                                                                       | output_file                                                                        |
      | basic-2x2     | ascribeCore/tck-runner/test/resources/tests/block/table/basic-2x2-input.adoc         | ascribeCore/tck-runner/test/resources/tests/block/table/basic-2x2-output.json          |
      | header-row    | ascribeCore/tck-runner/test/resources/tests/block/table/header-row-input.adoc        | ascribeCore/tck-runner/test/resources/tests/block/table/header-row-output.json         |
      | single-column | ascribeCore/tck-runner/test/resources/tests/block/table/single-column-input.adoc     | ascribeCore/tck-runner/test/resources/tests/block/table/single-column-output.json      |
      | inline-markup | ascribeCore/tck-runner/test/resources/tests/block/table/inline-markup-input.adoc     | ascribeCore/tck-runner/test/resources/tests/block/table/inline-markup-output.json      |
      | empty-cells   | ascribeCore/tck-runner/test/resources/tests/block/table/empty-cells-input.adoc       | ascribeCore/tck-runner/test/resources/tests/block/table/empty-cells-output.json        |
      | cols-equal     | ascribeCore/tck-runner/test/resources/tests/block/table/cols-equal-input.adoc        | ascribeCore/tck-runner/test/resources/tests/block/table/cols-equal-output.json         |
      | cols-widths    | ascribeCore/tck-runner/test/resources/tests/block/table/cols-widths-input.adoc       | ascribeCore/tck-runner/test/resources/tests/block/table/cols-widths-output.json        |
      | cols-alignment | ascribeCore/tck-runner/test/resources/tests/block/table/cols-alignment-input.adoc    | ascribeCore/tck-runner/test/resources/tests/block/table/cols-alignment-output.json     |
      | cols-valign    | ascribeCore/tck-runner/test/resources/tests/block/table/cols-valign-input.adoc       | ascribeCore/tck-runner/test/resources/tests/block/table/cols-valign-output.json        |
      | cols-mixed        | ascribeCore/tck-runner/test/resources/tests/block/table/cols-mixed-input.adoc        | ascribeCore/tck-runner/test/resources/tests/block/table/cols-mixed-output.json         |
      | table-title       | ascribeCore/tck-runner/test/resources/tests/block/table/table-title-input.adoc       | ascribeCore/tck-runner/test/resources/tests/block/table/table-title-output.json        |
      | header-explicit   | ascribeCore/tck-runner/test/resources/tests/block/table/header-explicit-input.adoc   | ascribeCore/tck-runner/test/resources/tests/block/table/header-explicit-output.json    |
      | header-noheader   | ascribeCore/tck-runner/test/resources/tests/block/table/header-noheader-input.adoc   | ascribeCore/tck-runner/test/resources/tests/block/table/header-noheader-output.json    |
      | footer            | ascribeCore/tck-runner/test/resources/tests/block/table/footer-input.adoc            | ascribeCore/tck-runner/test/resources/tests/block/table/footer-output.json             |
      | header-footer     | ascribeCore/tck-runner/test/resources/tests/block/table/header-footer-input.adoc     | ascribeCore/tck-runner/test/resources/tests/block/table/header-footer-output.json      |
      | frame-grid            | ascribeCore/tck-runner/test/resources/tests/block/table/frame-grid-input.adoc            | ascribeCore/tck-runner/test/resources/tests/block/table/frame-grid-output.json             |
      | stripes               | ascribeCore/tck-runner/test/resources/tests/block/table/stripes-input.adoc               | ascribeCore/tck-runner/test/resources/tests/block/table/stripes-output.json                |
      | cols-multiplier-align | ascribeCore/tck-runner/test/resources/tests/block/table/cols-multiplier-align-input.adoc | ascribeCore/tck-runner/test/resources/tests/block/table/cols-multiplier-align-output.json  |
      | stacked-attrs         | ascribeCore/tck-runner/test/resources/tests/block/table/stacked-attrs-input.adoc         | ascribeCore/tck-runner/test/resources/tests/block/table/stacked-attrs-output.json          |
      | full-attrs            | ascribeCore/tck-runner/test/resources/tests/block/table/full-attrs-input.adoc            | ascribeCore/tck-runner/test/resources/tests/block/table/full-attrs-output.json             |
      | cols-styles           | ascribeCore/tck-runner/test/resources/tests/block/table/cols-styles-input.adoc           | ascribeCore/tck-runner/test/resources/tests/block/table/cols-styles-output.json            |
      | cell-styles           | ascribeCore/tck-runner/test/resources/tests/block/table/cell-styles-input.adoc           | ascribeCore/tck-runner/test/resources/tests/block/table/cell-styles-output.json            |
      | span-columns          | ascribeCore/tck-runner/test/resources/tests/block/table/span-columns-input.adoc          | ascribeCore/tck-runner/test/resources/tests/block/table/span-columns-output.json           |
      | span-rows             | ascribeCore/tck-runner/test/resources/tests/block/table/span-rows-input.adoc             | ascribeCore/tck-runner/test/resources/tests/block/table/span-rows-output.json              |
      | dup-cells             | ascribeCore/tck-runner/test/resources/tests/block/table/dup-cells-input.adoc             | ascribeCore/tck-runner/test/resources/tests/block/table/dup-cells-output.json              |
      | csv-basic             | ascribeCore/tck-runner/test/resources/tests/block/table/csv-basic-input.adoc             | ascribeCore/tck-runner/test/resources/tests/block/table/csv-basic-output.json              |
      | dsv-basic             | ascribeCore/tck-runner/test/resources/tests/block/table/dsv-basic-input.adoc             | ascribeCore/tck-runner/test/resources/tests/block/table/dsv-basic-output.json              |
      | nested-simple         | ascribeCore/tck-runner/test/resources/tests/block/table/nested-simple-input.adoc         | ascribeCore/tck-runner/test/resources/tests/block/table/nested-simple-output.json          |
