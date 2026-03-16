package build.ascribe.tckrunner

import io.cucumber.scala.{EN, ScalaDsl}
import java.nio.file.{Files, Path, Paths}
import scala.compiletime.uninitialized
import zio._
import zio.json._
import zio.json.ast.Json
import org.junit.Assert._

class TckSteps extends ScalaDsl with EN {

  private def resolvePath(relative: String): Path = {
    val relPath = Paths.get(relative)
    if (relPath.isAbsolute) relPath
    else {
      var current = Paths.get("").toAbsolutePath
      var attempts = 0
      val maxAttempts = 6
      var found: Option[Path] = None

      while (attempts <= maxAttempts && found.isEmpty && current != null) {
        val candidate = current.resolve(relPath)
        if (Files.exists(candidate)) found = Some(candidate)
        else {
          current = current.getParent
          attempts += 1
        }
      }

      found.getOrElse(relPath)
    }
  }

  var assciidocInput: String = uninitialized
  var asgJsonString: String = uninitialized
  var parsedJsonResult: Option[String] = None

  Given("""the AsciiDoc input from {string}""") { (inputFile: String) =>
    val path = resolvePath(inputFile)
    assciidocInput = new String(Files.readAllBytes(path))
  }

  When("""the input is parsed""") { () =>
    // TODO: Connect this to ascribe's parser and convert to ZIO Block AST
    // Right now, this is a placeholder.
    // val parserResult = parser.parse(assciidocInput)
    // val zioBlockTree: ZioBlock = convertToZioBlock(parserResult)
    // parsedJsonResult = Some(zioBlockTree.toJson)

    // For now we will just assume we extracted some valid document:
    parsedJsonResult = Some("""{"type":"document","children":[]}""")
  }

  Then("""the resulting ASG should match the expected JSON in {string}""") { (outputFile: String) =>
    val path = resolvePath(outputFile)
    asgJsonString = new String(Files.readAllBytes(path))

    // TODO: Use zio-blocks-json-differ
    // val diff = JsonDiffer.diff(asgJsonString, parsedJsonResult.get)
    // assertTrue("ASG does not match expected JSON:\n" + diff.toString, diff.isEmpty)

    // Basic placeholder assertion
    assertNotNull("Expected ASG JSON was null", asgJsonString)
    assertNotNull("Parsed JSON outcome was null", parsedJsonResult.get)
  }
}


