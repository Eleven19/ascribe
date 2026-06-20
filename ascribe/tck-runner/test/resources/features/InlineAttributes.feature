Feature: Inline - Attributes
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file                                                                          | output_file                                                                         |
      | class     | ascribe/tck-runner/test/resources/tests/inline/attributes/class-input.adoc         | ascribe/tck-runner/test/resources/tests/inline/attributes/class-output.json         |
      | id        | ascribe/tck-runner/test/resources/tests/inline/attributes/id-input.adoc            | ascribe/tck-runner/test/resources/tests/inline/attributes/id-output.json            |
      | combined  | ascribe/tck-runner/test/resources/tests/inline/attributes/combined-input.adoc      | ascribe/tck-runner/test/resources/tests/inline/attributes/combined-output.json      |
      | role      | ascribe/tck-runner/test/resources/tests/inline/attributes/role-input.adoc          | ascribe/tck-runner/test/resources/tests/inline/attributes/role-output.json          |
