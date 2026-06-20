Feature: Block - Attributes
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file                                                                         | output_file                                                                        |
      | block-class   | ascribe/tck-runner/test/resources/tests/block/attributes/block-class-input.adoc   | ascribe/tck-runner/test/resources/tests/block/attributes/block-class-output.json   |
      | block-combined| ascribe/tck-runner/test/resources/tests/block/attributes/block-combined-input.adoc| ascribe/tck-runner/test/resources/tests/block/attributes/block-combined-output.json|

