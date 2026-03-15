package io.github.eleven19.mill.interceptor.maven

import io.cucumber.scala.{EN, ScalaDsl}
import munit.Assertions.*

class MavenShimSteps extends ScalaDsl with EN:

    private var rawArgs: List[String]      = Nil
    private var parsedCmd: MvnCommand      = MvnCommand.empty
    private var millTasks: List[MillTask]   = Nil

    Given("a Maven command with arguments {string}") { (argsStr: String) =>
        rawArgs =
            if argsStr.trim.isEmpty then Nil
            else argsStr.trim.split("\\s+").toList
    }

    When("the command is parsed and mapped") { () =>
        parsedCmd = MvnArgParser.parse(rawArgs)
        millTasks = MillCommandMapper.toMillTasks(parsedCmd)
    }

    When("the command is parsed") { () =>
        parsedCmd = MvnArgParser.parse(rawArgs)
        millTasks = MillCommandMapper.toMillTasks(parsedCmd)
    }

    Then("the Mill tasks should be {string}") { (expected: String) =>
        val expectedNames = expected.trim.split("\\s+").toList
        val actualNames   = millTasks.map(_.name)
        assertEquals(actualNames, expectedNames)
    }

    Then("there should be no Mill tasks") { () =>
        assert(millTasks.isEmpty, s"Expected no Mill tasks but got: ${millTasks.map(_.name)}")
    }

    Then("the parsed command should have debug enabled") { () =>
        assert(parsedCmd.debug, "Expected debug flag to be enabled")
    }

    Then("the parsed command should have offline enabled") { () =>
        assert(parsedCmd.offline, "Expected offline flag to be enabled")
    }

    Then("the parsed command should have quiet enabled") { () =>
        assert(parsedCmd.quiet, "Expected quiet flag to be enabled")
    }

    Then("the parsed command should have also-make enabled") { () =>
        assert(parsedCmd.alsoMake, "Expected also-make flag to be enabled")
    }

    Then("the parsed command should have also-make-dependents enabled") { () =>
        assert(parsedCmd.alsoMakeDependents, "Expected also-make-dependents flag to be enabled")
    }

    Then("the parsed command should have non-recursive enabled") { () =>
        assert(parsedCmd.nonRecursive, "Expected non-recursive flag to be enabled")
    }

    Then("the parsed command should have no phases") { () =>
        assert(parsedCmd.phases.isEmpty, s"Expected no phases but got: ${parsedCmd.phases}")
    }

    Then("the parsed command should have no projects") { () =>
        assert(parsedCmd.projects.isEmpty, s"Expected no projects but got: ${parsedCmd.projects}")
    }

    Then("the parsed command should have profiles {string}") { (expected: String) =>
        val expectedProfiles = expected.split(",").toList.map(_.trim)
        assertEquals(parsedCmd.profiles, expectedProfiles)
    }

    Then("the parsed command should have property {string} with value {string}") {
        (key: String, value: String) =>
            assert(parsedCmd.properties.contains(key), s"Expected property '$key' not found")
            assertEquals(parsedCmd.properties(key), value)
    }

    Then("the parsed command should have thread count {int}") { (count: Int) =>
        assertEquals(parsedCmd.threads, Some(count))
    }

    Then("the parsed command should have goal {string}") { (goal: String) =>
        assert(parsedCmd.goals.contains(goal), s"Expected goal '$goal' not found in ${parsedCmd.goals}")
    }
