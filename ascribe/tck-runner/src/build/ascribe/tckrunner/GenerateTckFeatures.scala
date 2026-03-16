package build.ascribe.tckrunner

import os.{Path, pwd, up, exists, list, isDir, makeDir, write}

object GenerateTckFeatures {

  private def findRepoRoot(start: Path): Path = {
    if (exists(start / ".git")) start
    else {
      val parent = start / up
      if (parent == start) start
      else findRepoRoot(parent)
    }
  }

  def main(args: Array[String]): Unit = {
    println("Generating AsciiDoc TCK Feature Files...")

    val repoRoot = findRepoRoot(pwd)
    val tckDir = repoRoot / "submodules" / "tck" / "tests"
    val featureOutDir = repoRoot / "ascribe" / "tck-runner" / "test" / "resources" / "features"
    makeDir.all(featureOutDir)

    // Block constructs
    val blockDir = tckDir / "block"
    if (exists(blockDir)) {
      for {
        constructDir <- list(blockDir)
        if isDir(constructDir)
      } {
        val constructName = constructDir.last.capitalize
        val featureFile = featureOutDir / s"Block${constructName}.feature"

        val featureLines = Seq(
          s"Feature: Block - $constructName",
          s"  Scenario Outline: TCK validation for <test_name>",
          s"""    Given the AsciiDoc input from "<input_file>"""",
          s"    When the input is parsed",
          s"""    Then the resulting ASG should match the expected JSON in "<output_file>"""",
          "",
          "    Examples:",
          "      | test_name | input_file | output_file |"
        )

        val examples = list(constructDir)
          .filter(_.last.endsWith("-input.adoc"))
          .map { inputFile =>
            val baseName = inputFile.last.stripSuffix("-input.adoc")
            val relativeInput = inputFile.relativeTo(repoRoot)
            val outputFile = constructDir / s"${baseName}-output.json"
            val relativeOutput = outputFile.relativeTo(repoRoot)

            s"      | $baseName | $relativeInput | $relativeOutput |"
          }

        if (examples.nonEmpty) {
          println(s"  Generating block feature: ${featureFile.last}")
          write.over(featureFile, (featureLines ++ examples).mkString("\n"))
        }
      }
    }
    // Inline constructs
    val inlineDir = tckDir / "inline"
    if (exists(inlineDir)) {
      for {
        constructDir <- list(inlineDir)
        if isDir(constructDir)
      } {
        val constructName = constructDir.last.capitalize
        val featureFile = featureOutDir / s"Inline${constructName}.feature"

        val featureLines = Seq(
          s"Feature: Inline - $constructName",
          s"  Scenario Outline: TCK validation for <test_name>",
          s"""    Given the AsciiDoc input from "<input_file>"""",
          s"    When the input is parsed",
          s"""    Then the resulting ASG should match the expected JSON in "<output_file>"""",
          "",
          "    Examples:",
          "      | test_name | input_file | output_file |"
        )

        val examples = list(constructDir)
          .filter(_.last.endsWith("-input.adoc"))
          .map { inputFile =>
            val baseName = inputFile.last.stripSuffix("-input.adoc")
            val relativeInput = inputFile.relativeTo(repoRoot)
            val outputFile = constructDir / s"${baseName}-output.json"
            val relativeOutput = outputFile.relativeTo(repoRoot)

            s"      | $baseName | $relativeInput | $relativeOutput |"
          }

        if (examples.nonEmpty) {
          println(s"  Generating inline feature: ${featureFile.last}")
          write.over(featureFile, (featureLines ++ examples).mkString("\n"))
        }
      }
    }
    println(s"Feature generation complete. Files written to: $featureOutDir")
  }
}

