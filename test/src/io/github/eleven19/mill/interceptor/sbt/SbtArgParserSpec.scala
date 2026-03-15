package io.github.eleven19.mill.interceptor.sbt

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object SbtArgParserSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("SbtArgParser")(
        suite("parse - tasks")(
            test("empty args produce empty command") {
                val cmd = SbtArgParser.parse(Nil)
                Sync.defer(assertTrue(cmd.tasks.isEmpty) && assertTrue(cmd.projects.isEmpty))
            },
            test("single task is parsed") {
                val cmd = SbtArgParser.parse(List("compile"))
                Sync.defer(assertTrue(cmd.tasks == List("compile")))
            },
            test("multiple tasks are parsed in order") {
                val cmd = SbtArgParser.parse(List("clean", "compile"))
                Sync.defer(assertTrue(cmd.tasks == List("clean", "compile")))
            }
        ),
        suite("parse - flags")(
            test("-batch sets batch mode") {
                val cmd = SbtArgParser.parse(List("-batch", "compile"))
                Sync.defer(assertTrue(cmd.batch))
            },
            test("-offline sets offline mode") {
                val cmd = SbtArgParser.parse(List("-offline", "compile"))
                Sync.defer(assertTrue(cmd.offline))
            },
            test("-no-colors sets noColor") {
                val cmd = SbtArgParser.parse(List("-no-colors", "compile"))
                Sync.defer(assertTrue(cmd.noColor))
            }
        ),
        suite("parse - project selection")(
            test("project keyword selects a project") {
                val cmd = SbtArgParser.parse(List("project", "core", "compile"))
                Sync.defer(assertTrue(cmd.projects == List("core")))
            },
            test("multiple project selections are captured") {
                val cmd = SbtArgParser.parse(List("project", "api", "test", "project", "core", "compile"))
                Sync.defer(assertTrue(cmd.projects == List("api", "core")))
            },
            test("project at end of args without value is treated as task") {
                val cmd = SbtArgParser.parse(List("compile", "project"))
                Sync.defer(assertTrue(cmd.tasks.contains("project")))
            }
        )
    )
