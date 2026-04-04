Feature: Block - Paragraph
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file                                                                              | output_file                                                                               |
      | basic              | ascribe/tck-runner/test/resources/tests/block/paragraph/basic-input.adoc                | ascribe/tck-runner/test/resources/tests/block/paragraph/basic-output.json                  |
      | multi-line         | ascribe/tck-runner/test/resources/tests/block/paragraph/multi-line-input.adoc           | ascribe/tck-runner/test/resources/tests/block/paragraph/multi-line-output.json             |
      | role-on-paragraph  | ascribe/tck-runner/test/resources/tests/block/paragraph/role-on-paragraph-input.adoc    | ascribe/tck-runner/test/resources/tests/block/paragraph/role-on-paragraph-output.json      |
