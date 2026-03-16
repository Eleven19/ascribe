Feature: Block - Header
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file | output_file |
      | attribute-entries-below-title | submodules/tck/tests/block/header/attribute-entries-below-title-input.adoc | submodules/tck/tests/block/header/attribute-entries-below-title-output.json |