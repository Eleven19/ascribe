package build.ascribe.tckrunner

import io.cucumber.scala.{EN, ScalaDsl}
import java.nio.file.{Files, Path, Paths}
import scala.compiletime.uninitialized
import org.junit.Assert.*
import zio.json.*
import zio.json.ast.Json

import io.eleven19.ascribe.Ascribe

given CanEqual[Json, Json] = CanEqual.derived
import io.eleven19.ascribe.asg
import io.eleven19.ascribe.asg.AsgCodecs
import io.eleven19.ascribe.bridge.AstToAsg

class TckSteps extends ScalaDsl with EN {

    private def resolvePath(relative: String): Path = {
        val relPath = Paths.get(relative)
        if (relPath.isAbsolute) relPath
        else {
            var current     = Paths.get("").toAbsolutePath
            var attempts    = 0
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

    var asciidocInput: String          = uninitialized
    var parsedAsgDoc: Option[asg.Document] = None
    var parseError: Option[String]     = None

    Given("""the AsciiDoc input from {string}""") { (inputFile: String) =>
        val path = resolvePath(inputFile)
        asciidocInput = new String(Files.readAllBytes(path))
    }

    When("""the input is parsed""") { () =>
        Ascribe.parse(asciidocInput) match
            case parsley.Success(astDoc) =>
                parsedAsgDoc = Some(AstToAsg.convert(astDoc))
                parseError = None
            case parsley.Failure(msg) =>
                parsedAsgDoc = None
                parseError = Some(msg.toString)
    }

    Then("""the resulting ASG should match the expected JSON in {string}""") { (outputFile: String) =>
        parseError.foreach { err =>
            fail(s"Parser failed on input:\n$err")
        }

        val doc          = parsedAsgDoc.getOrElse(fail("No parsed result available").asInstanceOf[asg.Document])
        val path         = resolvePath(outputFile)
        val expectedJson = new String(Files.readAllBytes(path)).trim

        // Detect inline-only tests: expected JSON is an array, not an object
        val actualJson = if (expectedJson.startsWith("[")) {
            // Extract inlines from first paragraph in the document
            doc.blocks.headOption match {
                case Some(p: asg.Paragraph) => AsgCodecs.encodeInlines(p.inlines)
                case _ => fail(s"Expected paragraph for inline test but got: ${doc.blocks}").asInstanceOf[String]
            }
        } else {
            AsgCodecs.encode(doc)
        }

        val actualAst = actualJson.fromJson[Json] match
            case Right(j) => j
            case Left(e)  => fail(s"Failed to parse actual JSON: $e").asInstanceOf[Json]

        val expectedAst = expectedJson.fromJson[Json] match
            case Right(j) => j
            case Left(e)  => fail(s"Failed to parse expected JSON: $e").asInstanceOf[Json]

        if (actualAst != expectedAst) {
            fail(
                s"""ASG JSON mismatch.
                   |
                   |=== Expected ===
                   |$expectedJson
                   |
                   |=== Actual ===
                   |$actualJson""".stripMargin
            )
        }
    }
}
