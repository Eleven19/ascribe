Feature: Block - Listing
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name      | input_file                                                                           | output_file                                                                            |
      | source-ruby    | ascribeCore/tck-runner/test/resources/tests/block/listing/source-ruby-input.adoc          | ascribeCore/tck-runner/test/resources/tests/block/listing/source-ruby-output.json           |
      | titled-source  | ascribeCore/tck-runner/test/resources/tests/block/listing/titled-source-input.adoc        | ascribeCore/tck-runner/test/resources/tests/block/listing/titled-source-output.json         |
