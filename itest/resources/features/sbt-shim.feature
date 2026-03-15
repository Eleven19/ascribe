Feature: sbt shim translates sbt commands to Mill tasks

  The sbt interceptor accepts sbt-style CLI arguments (e.g. batch mode)
  and translates them into the equivalent Mill task invocations.
  sbt task names map to one or more Mill tasks that reproduce the same effect.

  Scenario: Single task clean
    Given an sbt command with arguments "clean"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "clean"

  Scenario: Compile task
    Given an sbt command with arguments "compile"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "compile"

  Scenario: Test task includes compile
    Given an sbt command with arguments "test"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "compile test"

  Scenario: Package task maps to jar
    Given an sbt command with arguments "package"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "jar"

  Scenario: Clean and compile
    Given an sbt command with arguments "clean compile"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "clean compile"

  Scenario: PublishLocal full lifecycle
    Given an sbt command with arguments "publishLocal"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "compile test jar publishLocal"

  Scenario: Publish full lifecycle
    Given an sbt command with arguments "publish"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "compile test jar publish"

  Scenario: Run task
    Given an sbt command with arguments "run"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "run"

  Scenario: Unknown task produces no Mill tasks
    Given an sbt command with arguments "unknownTask"
    When the sbt command is parsed and mapped
    Then there should be no mapped Mill tasks

  Scenario: Batch flag is captured
    Given an sbt command with arguments "-batch compile"
    When the sbt command is parsed and mapped
    Then the parsed sbt command should have batch enabled

  Scenario: Offline flag is captured
    Given an sbt command with arguments "-offline compile"
    When the sbt command is parsed and mapped
    Then the parsed sbt command should have offline enabled

  Scenario: Project selection
    Given an sbt command with arguments "project core compile"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "core.compile"

  Scenario: Multiple projects
    Given an sbt command with arguments "project api test project core compile"
    When the sbt command is parsed and mapped
    Then the sbt mapped Mill tasks should be "api.compile api.test core.compile core.test"
