package io.github.eleven19.mill.interceptor.gradle

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object GradleArgParserSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("GradleArgParser")(
        suite("parse - tasks")(
            test("empty args produce empty command") {
                val cmd = GradleArgParser.parse(Nil)
                Sync.defer(assertTrue(cmd.tasks.isEmpty) && assertTrue(cmd.unknownTasks.isEmpty))
            },
            test("single task is parsed") {
                val cmd = GradleArgParser.parse(List("build"))
                Sync.defer(assertTrue(cmd.tasks == List(GradleTask.Build)))
            },
            test("multiple tasks are parsed in order") {
                val cmd = GradleArgParser.parse(List("clean", "build"))
                Sync.defer(assertTrue(cmd.tasks == List(GradleTask.Clean, GradleTask.Build)))
            },
            test("unknown tasks are captured") {
                val cmd = GradleArgParser.parse(List("customTask"))
                Sync.defer(assertTrue(cmd.unknownTasks == List("customTask")))
            }
        ),
        suite("parse - flags")(
            test("--offline sets offline") {
                val cmd = GradleArgParser.parse(List("--offline", "build"))
                Sync.defer(assertTrue(cmd.offline))
            },
            test("-d sets debug") {
                val cmd = GradleArgParser.parse(List("-d", "build"))
                Sync.defer(assertTrue(cmd.debug))
            },
            test("-q sets quiet") {
                val cmd = GradleArgParser.parse(List("-q", "build"))
                Sync.defer(assertTrue(cmd.quiet))
            },
            test("-i sets info") {
                val cmd = GradleArgParser.parse(List("-i", "build"))
                Sync.defer(assertTrue(cmd.info))
            },
            test("-s sets stacktrace") {
                val cmd = GradleArgParser.parse(List("-s", "build"))
                Sync.defer(assertTrue(cmd.stacktrace))
            },
            test("--build-cache sets buildCache to true") {
                val cmd = GradleArgParser.parse(List("--build-cache", "build"))
                Sync.defer(assertTrue(cmd.buildCache == Some(true)))
            },
            test("--no-build-cache sets buildCache to false") {
                val cmd = GradleArgParser.parse(List("--no-build-cache", "build"))
                Sync.defer(assertTrue(cmd.buildCache == Some(false)))
            },
            test("--parallel sets parallel to true") {
                val cmd = GradleArgParser.parse(List("--parallel", "build"))
                Sync.defer(assertTrue(cmd.parallel == Some(true)))
            },
            test("--no-parallel sets parallel to false") {
                val cmd = GradleArgParser.parse(List("--no-parallel", "build"))
                Sync.defer(assertTrue(cmd.parallel == Some(false)))
            },
            test("-c sets continueOnFailure") {
                val cmd = GradleArgParser.parse(List("-c", "build"))
                Sync.defer(assertTrue(cmd.continueOnFailure))
            },
            test("-m sets dryRun") {
                val cmd = GradleArgParser.parse(List("-m", "build"))
                Sync.defer(assertTrue(cmd.dryRun))
            },
            test("-p sets projectDir") {
                val cmd = GradleArgParser.parse(List("-p", "subdir", "build"))
                Sync.defer(assertTrue(cmd.projectDir == Some("subdir")))
            }
        ),
        suite("parse - system properties")(
            test("-D extracts system property") {
                val cmd = GradleArgParser.parse(List("-Dorg.gradle.debug=true", "build"))
                Sync.defer(assertTrue(cmd.systemProperties == Map("org.gradle.debug" -> "true")))
            },
            test("multiple -D properties are captured") {
                val cmd = GradleArgParser.parse(List("-Dfoo=bar", "-Dbaz=qux", "build"))
                Sync.defer(assertTrue(cmd.systemProperties == Map("foo" -> "bar", "baz" -> "qux")))
            },
            test("-D without value defaults to true") {
                val cmd = GradleArgParser.parse(List("-DsomeFlag", "build"))
                Sync.defer(assertTrue(cmd.systemProperties == Map("someFlag" -> "true")))
            }
        ),
        suite("parse - project properties")(
            test("-P extracts project property") {
                val cmd = GradleArgParser.parse(List("-Pversion=1.2.3", "build"))
                Sync.defer(assertTrue(cmd.projectProperties == Map("version" -> "1.2.3")))
            },
            test("multiple -P properties are captured") {
                val cmd = GradleArgParser.parse(List("-Puser=admin", "-Ppass=secret", "build"))
                Sync.defer(assertTrue(cmd.projectProperties == Map("user" -> "admin", "pass" -> "secret")))
            }
        ),
        suite("parse - excluded tasks")(
            test("-x extracts excluded task") {
                val cmd = GradleArgParser.parse(List("-x", "test", "build"))
                Sync.defer(assertTrue(cmd.excludedTasks == List("test")))
            },
            test("--exclude-task extracts excluded task") {
                val cmd = GradleArgParser.parse(List("--exclude-task", "test", "build"))
                Sync.defer(assertTrue(cmd.excludedTasks == List("test")))
            },
            test("multiple exclusions are captured") {
                val cmd = GradleArgParser.parse(List("-x", "test", "-x", "lint", "build"))
                Sync.defer(assertTrue(cmd.excludedTasks == List("test", "lint")))
            }
        ),
        suite("parse - init-script")(
            test("--init-script is parsed and ignored for task mapping") {
                val cmd = GradleArgParser.parse(List("--init-script", "temp.gradle", "build"))
                Sync.defer(assertTrue(cmd.tasks == List(GradleTask.Build)))
            }
        )
    )
