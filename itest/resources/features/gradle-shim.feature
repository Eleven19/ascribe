Feature: Gradle shim translates Gradle commands to Mill tasks

  The Gradle interceptor accepts standard Gradle CLI arguments and
  translates them into the equivalent Mill task invocations.
  Gradle tasks map to one or more Mill tasks that together reproduce
  the effect of that task.

  Scenario: Clean task
    Given a Gradle command with arguments "clean"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "clean"

  Scenario: Build task includes compile, test, and jar
    Given a Gradle command with arguments "build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test jar"

  Scenario: Compile task
    Given a Gradle command with arguments "compile"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile"

  Scenario: Classes task maps to compile
    Given a Gradle command with arguments "classes"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile"

  Scenario: Test task includes compile
    Given a Gradle command with arguments "test"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test"

  Scenario: Check task includes compile and test
    Given a Gradle command with arguments "check"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test"

  Scenario: Jar task includes compile
    Given a Gradle command with arguments "jar"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile jar"

  Scenario: Assemble task includes compile and jar
    Given a Gradle command with arguments "assemble"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile jar"

  Scenario: Publish task includes full lifecycle through publish
    Given a Gradle command with arguments "publish"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test jar publish"

  Scenario: PublishToMavenLocal task includes full lifecycle through publishLocal
    Given a Gradle command with arguments "publishToMavenLocal"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test jar publishLocal"

  Scenario: Clean and build together
    Given a Gradle command with arguments "clean build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "clean compile test jar"

  Scenario: Clean build publish produces deduplicated task list
    Given a Gradle command with arguments "clean build publish"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "clean compile test jar publish"

  Scenario: Duplicate tasks are deduplicated
    Given a Gradle command with arguments "compile test"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test"

  # --- Task exclusion ---

  Scenario: Exclude test task from build with -x
    Given a Gradle command with arguments "-x test build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile jar"

  Scenario: Exclude test from clean build
    Given a Gradle command with arguments "clean build -x test"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "clean compile jar"

  Scenario: Exclude test from publish
    Given a Gradle command with arguments "publish --exclude-task test"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile jar publish"

  # --- Ignored flags ---

  Scenario: Gradle system properties are ignored for task mapping
    Given a Gradle command with arguments "-Dorg.gradle.internal.http.connectTimeout=120000 build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test jar"

  Scenario: Gradle project properties are captured
    Given a Gradle command with arguments "-Pversion=1.2.3 build"
    When the Gradle command is parsed and mapped
    Then the parsed Gradle command should have project property "version" with value "1.2.3"

  Scenario: Init script is ignored for task mapping
    Given a Gradle command with arguments "--init-script temp-init.gradle build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "compile test jar"

  Scenario: Full command with all flags is handled correctly
    Given a Gradle command with arguments "-Dorg.gradle.internal.http.connectTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000 -PmavenUsername=r -PmavenPassword=r -Pversion=1.2.3 --init-script temp-init.gradle clean build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "clean compile test jar"

  # --- Project directory ---

  Scenario: Project directory prefixes Mill tasks
    Given a Gradle command with arguments "-p core build"
    When the Gradle command is parsed and mapped
    Then the Gradle mapped Mill tasks should be "core.compile core.test core.jar"

  # --- No tasks ---

  Scenario: No tasks specified produces empty result
    Given a Gradle command with arguments "-Pversion=1.0.0"
    When the Gradle command is parsed and mapped
    Then there should be no Gradle Mill tasks
