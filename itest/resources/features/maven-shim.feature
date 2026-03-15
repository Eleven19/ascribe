Feature: Maven shim translates Maven commands to Mill tasks

  The Maven interceptor accepts standard Maven CLI arguments and
  translates them into the equivalent Mill task invocations.
  Maven phases map to one or more Mill tasks that together reproduce
  the effect of that phase.

  Scenario: Single lifecycle phase
    Given a Maven command with arguments "clean"
    When the command is parsed and mapped
    Then the Mill tasks should be "clean"

  Scenario: Compile phase
    Given a Maven command with arguments "compile"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile"

  Scenario: Test phase includes compile
    Given a Maven command with arguments "test"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile test"

  Scenario: Package phase includes compile, test, and jar
    Given a Maven command with arguments "package"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile test jar"

  Scenario: Clean and package together
    Given a Maven command with arguments "clean package"
    When the command is parsed and mapped
    Then the Mill tasks should be "clean compile test jar"

  Scenario: Install phase includes full lifecycle through publishLocal
    Given a Maven command with arguments "install"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile test jar publishLocal"

  Scenario: Deploy phase includes full lifecycle through publish
    Given a Maven command with arguments "deploy"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile test jar publish"

  Scenario: Clean install produces deduplicated task list
    Given a Maven command with arguments "clean install"
    When the command is parsed and mapped
    Then the Mill tasks should be "clean compile test jar publishLocal"

  Scenario: Skip tests flag removes test tasks from install
    Given a Maven command with arguments "install -DskipTests"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile jar publishLocal"

  Scenario: Skip tests with explicit true value
    Given a Maven command with arguments "install -DskipTests=true"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile jar publishLocal"

  Scenario: Skip tests flag removes test tasks
    Given a Maven command with arguments "clean test -DskipTests"
    When the command is parsed and mapped
    Then the Mill tasks should be "clean compile"

  Scenario: Skip tests with property
    Given a Maven command with arguments "clean test -Dmaven.test.skip=true"
    When the command is parsed and mapped
    Then the Mill tasks should be "clean compile"

  Scenario: Project targeting with -pl
    Given a Maven command with arguments "-pl :core compile"
    When the command is parsed and mapped
    Then the Mill tasks should be "core.compile"

  Scenario: Multiple projects with install
    Given a Maven command with arguments "-pl :core,:api install"
    When the command is parsed and mapped
    Then the Mill tasks should be "core.compile core.test core.jar core.publishLocal api.compile api.test api.jar api.publishLocal"

  Scenario: Validate phase produces no Mill task
    Given a Maven command with arguments "validate"
    When the command is parsed and mapped
    Then there should be no Mill tasks

  Scenario: Debug flag is captured
    Given a Maven command with arguments "-X compile"
    When the command is parsed and mapped
    Then the parsed command should have debug enabled

  Scenario: Offline flag is captured
    Given a Maven command with arguments "-o compile"
    When the command is parsed and mapped
    Then the parsed command should have offline enabled

  Scenario: Quiet flag is captured
    Given a Maven command with arguments "-q compile"
    When the command is parsed and mapped
    Then the parsed command should have quiet enabled

  Scenario: Profile activation
    Given a Maven command with arguments "-P prod,staging compile"
    When the command is parsed and mapped
    Then the parsed command should have profiles "prod,staging"

  Scenario: System properties are captured
    Given a Maven command with arguments "-Dapp.version=1.0.0 compile"
    When the command is parsed and mapped
    Then the parsed command should have property "app.version" with value "1.0.0"

  Scenario: Duplicate phases are deduplicated
    Given a Maven command with arguments "compile test"
    When the command is parsed and mapped
    Then the Mill tasks should be "compile test"
