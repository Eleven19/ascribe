Feature: Inline - Macros (Links and URLs)
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file | output_file |
      | autolink-https | ascribe/tck-runner/test/resources/tests/inline/macros/autolink/https-input.adoc | ascribe/tck-runner/test/resources/tests/inline/macros/autolink/https-output.json |
      | link-macro-simple | ascribe/tck-runner/test/resources/tests/inline/macros/link-macro/simple-input.adoc | ascribe/tck-runner/test/resources/tests/inline/macros/link-macro/simple-output.json |
