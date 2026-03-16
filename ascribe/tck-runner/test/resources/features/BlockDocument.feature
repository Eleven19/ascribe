Feature: Block - Document
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file | output_file |
      | body-only | submodules/tck/tests/block/document/body-only-input.adoc | submodules/tck/tests/block/document/body-only-output.json |
      | header-body | submodules/tck/tests/block/document/header-body-input.adoc | submodules/tck/tests/block/document/header-body-output.json |