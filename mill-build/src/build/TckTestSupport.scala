package build

import mill.*
import mill.scalalib.JavaModule

trait TckTestSupport extends JavaModule {

  /** Path to TCK test data root (containing tests/block/, tests/inline/).
    * Defaults to submodules/tck relative to workspace root.
    * Reads directly from filesystem — does NOT depend on tckSync.
    * The submodule must be initialized beforehand (via tck.refresh or git submodule update).
    */
  def tckTestDataPath: T[os.Path] = Task {
    Task.ctx().workspace / "submodules" / "tck"
  }

  /** Hand-written feature files to merge with generated ones. Override to provide. */
  def customTckFeatures: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

  /** Generate .feature files from TCK test data.
    * Note: Directory names are title-cased per segment (e.g., "no-markup" -> "No-Markup").
    * Entries are sorted for deterministic output.
    */
  def generateTckFeatures: T[PathRef] = Task {
    val tckRoot = tckTestDataPath()
    val tckDir = tckRoot / "tests"
    val outDir = Task.dest / "features"
    os.makeDir.all(outDir)

    def generateForCategory(categoryDir: os.Path, prefix: String): Unit = {
      if (os.exists(categoryDir)) {
        for {
          constructDir <- os.list(categoryDir).sorted
          if os.isDir(constructDir)
        } {
          val constructName = constructDir.last.split("-").map(_.capitalize).mkString("-")
          val featureFile = outDir / s"$prefix${constructName}.feature"

          val featureLines = Seq(
            s"Feature: $prefix - $constructName",
            s"  Scenario Outline: TCK validation for <test_name>",
            s"""    Given the AsciiDoc input from "<input_file>"""",
            s"    When the input is parsed",
            s"""    Then the resulting ASG should match the expected JSON in "<output_file>"""",
            "",
            "    Examples:",
            "      | test_name | input_file | output_file |"
          )

          val workspace = Task.ctx().workspace
          val examples = os.list(constructDir).sorted
            .filter(_.last.endsWith("-input.adoc"))
            .map { inputFile =>
              val baseName = inputFile.last.stripSuffix("-input.adoc")
              val relativeInput = inputFile.relativeTo(workspace)
              val outputFile = constructDir / s"${baseName}-output.json"
              val relativeOutput = outputFile.relativeTo(workspace)
              s"      | $baseName | $relativeInput | $relativeOutput |"
            }

          if (examples.nonEmpty) {
            Task.log.info(s"  Generating feature: ${featureFile.last}")
            os.write.over(featureFile, (featureLines ++ examples).mkString("\n") + "\n")
          }
        }
      }
    }

    generateForCategory(tckDir / "block", "Block")
    generateForCategory(tckDir / "inline", "Inline")

    Task.log.info(s"Feature generation complete. Files written to: $outDir")
    PathRef(Task.dest)
  }

  /** Merged features: generated + custom. All entries are directories. */
  def tckFeatures: T[Seq[PathRef]] = Task {
    Seq(generateTckFeatures()) ++ customTckFeatures()
  }

  /** Override resources to include generated and custom TCK features alongside source resources. */
  override def resources: T[Seq[PathRef]] = Task {
    super.resources() ++ tckFeatures()
  }
}
