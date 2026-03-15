Feature: Maven argument parsing

  The Maven argument parser correctly handles the full range of
  Maven CLI options and flags.

  Scenario: Empty arguments produce empty command
    Given a Maven command with arguments ""
    When the command is parsed
    Then the parsed command should have no phases
    And the parsed command should have no projects

  Scenario: Also-make flag
    Given a Maven command with arguments "-am -pl :core compile"
    When the command is parsed
    Then the parsed command should have also-make enabled

  Scenario: Also-make-dependents flag
    Given a Maven command with arguments "-amd -pl :core compile"
    When the command is parsed
    Then the parsed command should have also-make-dependents enabled

  Scenario: Non-recursive flag
    Given a Maven command with arguments "-N compile"
    When the command is parsed
    Then the parsed command should have non-recursive enabled

  Scenario: Thread count
    Given a Maven command with arguments "-T 4 compile"
    When the command is parsed
    Then the parsed command should have thread count 4

  Scenario: Long-form options
    Given a Maven command with arguments "--projects :core --also-make --offline --debug compile"
    When the command is parsed
    Then the parsed command should have also-make enabled
    And the parsed command should have offline enabled
    And the parsed command should have debug enabled

  Scenario: Unknown goals are captured
    Given a Maven command with arguments "dependency:tree"
    When the command is parsed
    Then the parsed command should have goal "dependency:tree"

  Scenario: Multiple system properties
    Given a Maven command with arguments "-Dfoo=bar -Dbaz=qux compile"
    When the command is parsed
    Then the parsed command should have property "foo" with value "bar"
    And the parsed command should have property "baz" with value "qux"

  Scenario: Boolean system property without value
    Given a Maven command with arguments "-DsomeFlag compile"
    When the command is parsed
    Then the parsed command should have property "someFlag" with value "true"
