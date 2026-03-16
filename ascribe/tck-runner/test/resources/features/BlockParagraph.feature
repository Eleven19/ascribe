Feature: Block - Paragraph
  Scenario Outline: TCK validation for <test_name>
    Given the AsciiDoc input from "<input_file>"
    When the input is parsed
    Then the resulting ASG should match the expected JSON in "<output_file>"

    Examples:
      | test_name | input_file | output_file |
      | multiple-lines | submodules/tck/tests/block/paragraph/multiple-lines-input.adoc | submodules/tck/tests/block/paragraph/multiple-lines-output.json |
      | paragraph-empty-lines-paragraph | submodules/tck/tests/block/paragraph/paragraph-empty-lines-paragraph-input.adoc | submodules/tck/tests/block/paragraph/paragraph-empty-lines-paragraph-output.json |
      | sibling-paragraphs | submodules/tck/tests/block/paragraph/sibling-paragraphs-input.adoc | submodules/tck/tests/block/paragraph/sibling-paragraphs-output.json |
      | single-line | submodules/tck/tests/block/paragraph/single-line-input.adoc | submodules/tck/tests/block/paragraph/single-line-output.json |