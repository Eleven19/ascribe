Feature: Constrained Formatting Spans

  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file                                                                                   | output_file                                                                                  |
      | bold-constrained-multi-char      | ascribe/tck-runner/test/resources/tests/inline/span/bold/constrained-multi-char-input.adoc      | ascribe/tck-runner/test/resources/tests/inline/span/bold/constrained-multi-char-output.json      |
      | italic-constrained-multi-char    | ascribe/tck-runner/test/resources/tests/inline/span/italic/constrained-multi-char-input.adoc    | ascribe/tck-runner/test/resources/tests/inline/span/italic/constrained-multi-char-output.json    |
      | mono-constrained-multi-char      | ascribe/tck-runner/test/resources/tests/inline/span/mono/constrained-multi-char-input.adoc      | ascribe/tck-runner/test/resources/tests/inline/span/mono/constrained-multi-char-output.json      |
      | bold-constrained-single-char     | ascribe/tck-runner/test/resources/tests/inline/span/bold/constrained-single-char-input.adoc      | ascribe/tck-runner/test/resources/tests/inline/span/bold/constrained-single-char-output.json      |
      | italic-constrained-single-char   | ascribe/tck-runner/test/resources/tests/inline/span/italic/constrained-single-char-input.adoc    | ascribe/tck-runner/test/resources/tests/inline/span/italic/constrained-single-char-output.json    |
      | mono-constrained-single-char     | ascribe/tck-runner/test/resources/tests/inline/span/mono/constrained-single-char-input.adoc      | ascribe/tck-runner/test/resources/tests/inline/span/mono/constrained-single-char-output.json      |
