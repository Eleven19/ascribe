Feature: Block - Listing
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file | output_file |
      | multiple-lines | submodules/tck/tests/block/listing/multiple-lines-input.adoc | submodules/tck/tests/block/listing/multiple-lines-output.json |