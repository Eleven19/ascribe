package io.github.eleven19.mill.interceptor.gradle

import io.cucumber.scala.{EN, ScalaDsl}
import io.github.eleven19.mill.interceptor.MillTask
import munit.Assertions.*

class GradleShimSteps extends ScalaDsl with EN:

    private var rawArgs: List[String]        = Nil
    private var parsedCmd: GradleCommand     = GradleCommand.empty
    private var millTasks: List[MillTask]     = Nil

    Given("a Gradle command with arguments {string}") { (argsStr: String) =>
        rawArgs =
            if argsStr.trim.isEmpty then Nil
            else argsStr.trim.split("\\s+").toList
    }

    When("the Gradle command is parsed and mapped") { () =>
        parsedCmd = GradleArgParser.parse(rawArgs)
        millTasks = GradleCommandMapper.toMillTasks(parsedCmd)
    }

    When("the Gradle command is parsed") { () =>
        parsedCmd = GradleArgParser.parse(rawArgs)
        millTasks = GradleCommandMapper.toMillTasks(parsedCmd)
    }

    Then("the Gradle mapped Mill tasks should be {string}") { (expected: String) =>
        val expectedNames = expected.trim.split("\\s+").toList
        val actualNames   = millTasks.map(_.name)
        assertEquals(actualNames, expectedNames)
    }

    Then("there should be no Gradle Mill tasks") { () =>
        assert(millTasks.isEmpty, s"Expected no Mill tasks but got: ${millTasks.map(_.name)}")
    }

    Then("the parsed Gradle command should have offline enabled") { () =>
        assert(parsedCmd.offline, "Expected offline flag to be enabled")
    }

    Then("the parsed Gradle command should have debug enabled") { () =>
        assert(parsedCmd.debug, "Expected debug flag to be enabled")
    }

    Then("the parsed Gradle command should have quiet enabled") { () =>
        assert(parsedCmd.quiet, "Expected quiet flag to be enabled")
    }

    Then("the parsed Gradle command should have info enabled") { () =>
        assert(parsedCmd.info, "Expected info flag to be enabled")
    }

    Then("the parsed Gradle command should have stacktrace enabled") { () =>
        assert(parsedCmd.stacktrace, "Expected stacktrace flag to be enabled")
    }

    Then("the parsed Gradle command should have build cache enabled") { () =>
        assertEquals(parsedCmd.buildCache, Some(true))
    }

    Then("the parsed Gradle command should have build cache disabled") { () =>
        assertEquals(parsedCmd.buildCache, Some(false))
    }

    Then("the parsed Gradle command should have parallel enabled") { () =>
        assertEquals(parsedCmd.parallel, Some(true))
    }

    Then("the parsed Gradle command should have continue enabled") { () =>
        assert(parsedCmd.continueOnFailure, "Expected continue flag to be enabled")
    }

    Then("the parsed Gradle command should have dry run enabled") { () =>
        assert(parsedCmd.dryRun, "Expected dry-run flag to be enabled")
    }

    Then("the parsed Gradle command should have no tasks") { () =>
        assert(parsedCmd.tasks.isEmpty, s"Expected no tasks but got: ${parsedCmd.tasks}")
    }

    Then("the parsed Gradle command should have no excluded tasks") { () =>
        assert(parsedCmd.excludedTasks.isEmpty, s"Expected no excluded tasks but got: ${parsedCmd.excludedTasks}")
    }

    Then("the parsed Gradle command should have system property {string} with value {string}") {
        (key: String, value: String) =>
            assert(parsedCmd.systemProperties.contains(key), s"Expected system property '$key' not found")
            assertEquals(parsedCmd.systemProperties(key), value)
    }

    Then("the parsed Gradle command should have project property {string} with value {string}") {
        (key: String, value: String) =>
            assert(parsedCmd.projectProperties.contains(key), s"Expected project property '$key' not found")
            assertEquals(parsedCmd.projectProperties(key), value)
    }

    Then("the parsed Gradle command should have excluded task {string}") { (task: String) =>
        assert(parsedCmd.excludedTasks.contains(task), s"Expected excluded task '$task' not found in ${parsedCmd.excludedTasks}")
    }

    Then("the parsed Gradle command should have unknown task {string}") { (task: String) =>
        assert(parsedCmd.unknownTasks.contains(task), s"Expected unknown task '$task' not found in ${parsedCmd.unknownTasks}")
    }

    Then("the parsed Gradle command should have project dir {string}") { (dir: String) =>
        assertEquals(parsedCmd.projectDir, Some(dir))
    }
