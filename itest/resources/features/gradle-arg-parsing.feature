Feature: Gradle argument parsing

  The Gradle argument parser correctly handles the full range of
  Gradle CLI options and flags.

  Scenario: Empty arguments produce empty command
    Given a Gradle command with arguments ""
    When the Gradle command is parsed
    Then the parsed Gradle command should have no tasks
    And the parsed Gradle command should have no excluded tasks

  Scenario: Offline flag
    Given a Gradle command with arguments "--offline build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have offline enabled

  Scenario: Debug flag
    Given a Gradle command with arguments "--debug build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have debug enabled

  Scenario: Quiet flag
    Given a Gradle command with arguments "--quiet build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have quiet enabled

  Scenario: Info flag
    Given a Gradle command with arguments "--info build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have info enabled

  Scenario: Stacktrace flag
    Given a Gradle command with arguments "--stacktrace build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have stacktrace enabled

  Scenario: Build cache flag
    Given a Gradle command with arguments "--build-cache build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have build cache enabled

  Scenario: No build cache flag
    Given a Gradle command with arguments "--no-build-cache build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have build cache disabled

  Scenario: Parallel flag
    Given a Gradle command with arguments "--parallel build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have parallel enabled

  Scenario: Continue flag
    Given a Gradle command with arguments "--continue build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have continue enabled

  Scenario: Dry run flag
    Given a Gradle command with arguments "--dry-run build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have dry run enabled

  Scenario: System properties are captured
    Given a Gradle command with arguments "-Dfoo=bar -Dbaz=qux build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have system property "foo" with value "bar"
    And the parsed Gradle command should have system property "baz" with value "qux"

  Scenario: Project properties are captured
    Given a Gradle command with arguments "-Pversion=1.2.3 -PmyProp=hello build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have project property "version" with value "1.2.3"
    And the parsed Gradle command should have project property "myProp" with value "hello"

  Scenario: Boolean system property without value
    Given a Gradle command with arguments "-DsomeFlag build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have system property "someFlag" with value "true"

  Scenario: Excluded tasks are captured
    Given a Gradle command with arguments "-x test -x check build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have excluded task "test"
    And the parsed Gradle command should have excluded task "check"

  Scenario: Unknown tasks are captured
    Given a Gradle command with arguments "customTask"
    When the Gradle command is parsed
    Then the parsed Gradle command should have unknown task "customTask"

  Scenario: Project directory is captured
    Given a Gradle command with arguments "-p subproject build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have project dir "subproject"

  Scenario: Short flags work
    Given a Gradle command with arguments "-q -d -i -s build"
    When the Gradle command is parsed
    Then the parsed Gradle command should have quiet enabled
    And the parsed Gradle command should have debug enabled
    And the parsed Gradle command should have info enabled
    And the parsed Gradle command should have stacktrace enabled
