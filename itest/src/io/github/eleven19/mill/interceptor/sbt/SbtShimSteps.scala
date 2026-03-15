package io.github.eleven19.mill.interceptor.sbt

import io.cucumber.scala.{EN, ScalaDsl}
import io.github.eleven19.mill.interceptor.MillTask
import munit.Assertions.*

class SbtShimSteps extends ScalaDsl with EN:

    private var rawArgs: List[String]   = Nil
    private var parsedCmd: SbtCommand   = SbtCommand.empty
    private var millTasks: List[MillTask] = Nil

    Given("an sbt command with arguments {string}") { (argsStr: String) =>
        rawArgs =
            if argsStr.trim.isEmpty then Nil
            else argsStr.trim.split("\\s+").toList
    }

    When("the sbt command is parsed and mapped") { () =>
        parsedCmd = SbtArgParser.parse(rawArgs)
        millTasks = SbtCommandMapper.toMillTasks(parsedCmd)
    }

    When("the sbt command is parsed") { () =>
        parsedCmd = SbtArgParser.parse(rawArgs)
        millTasks = SbtCommandMapper.toMillTasks(parsedCmd)
    }

    Then("the sbt mapped Mill tasks should be {string}") { (expected: String) =>
        val expectedNames = expected.trim.split("\\s+").toList
        val actualNames   = millTasks.map(_.name)
        assertEquals(actualNames, expectedNames)
    }

    Then("there should be no mapped Mill tasks") { () =>
        assert(millTasks.isEmpty, s"Expected no Mill tasks but got: ${millTasks.map(_.name)}")
    }

    Then("the parsed sbt command should have batch enabled") { () =>
        assert(parsedCmd.batch, "Expected batch flag to be enabled")
    }

    Then("the parsed sbt command should have offline enabled") { () =>
        assert(parsedCmd.offline, "Expected offline flag to be enabled")
    }
