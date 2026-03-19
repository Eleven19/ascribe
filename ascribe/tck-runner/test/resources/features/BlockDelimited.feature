Feature: Block - Delimited
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name        | input_file                                                                                  | output_file                                                                                   |
      | example-basic    | ascribe/tck-runner/test/resources/tests/block/delimited/example-basic-input.adoc             | ascribe/tck-runner/test/resources/tests/block/delimited/example-basic-output.json              |
      | quote-basic      | ascribe/tck-runner/test/resources/tests/block/delimited/quote-basic-input.adoc               | ascribe/tck-runner/test/resources/tests/block/delimited/quote-basic-output.json                |
      | literal-basic    | ascribe/tck-runner/test/resources/tests/block/delimited/literal-basic-input.adoc             | ascribe/tck-runner/test/resources/tests/block/delimited/literal-basic-output.json              |
      | open-basic       | ascribe/tck-runner/test/resources/tests/block/delimited/open-basic-input.adoc                | ascribe/tck-runner/test/resources/tests/block/delimited/open-basic-output.json                 |
      | pass-basic       | ascribe/tck-runner/test/resources/tests/block/delimited/pass-basic-input.adoc                | ascribe/tck-runner/test/resources/tests/block/delimited/pass-basic-output.json                 |
      | nested-example   | ascribe/tck-runner/test/resources/tests/block/delimited/nested-example-input.adoc            | ascribe/tck-runner/test/resources/tests/block/delimited/nested-example-output.json             |
      | verse-block      | ascribe/tck-runner/test/resources/tests/block/delimited/verse-block-input.adoc               | ascribe/tck-runner/test/resources/tests/block/delimited/verse-block-output.json                |
      | admonition-note  | ascribe/tck-runner/test/resources/tests/block/delimited/admonition-note-input.adoc           | ascribe/tck-runner/test/resources/tests/block/delimited/admonition-note-output.json            |
