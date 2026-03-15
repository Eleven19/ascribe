package io.github.eleven19.mill.interceptor.sbt

import io.github.eleven19.mill.interceptor.MillTask
import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object SbtCommandMapperSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("SbtCommandMapper")(
        suite("toMillTasks")(
            test("empty command produces no tasks") {
                val tasks = SbtCommandMapper.toMillTasks(SbtCommand.empty)
                Sync.defer(assertTrue(tasks.isEmpty))
            },
            test("clean maps to clean") {
                val cmd   = SbtCommand.empty.copy(tasks = List("clean"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("clean"))))
            },
            test("compile maps to compile") {
                val cmd   = SbtCommand.empty.copy(tasks = List("compile"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"))))
            },
            test("test includes compile") {
                val cmd   = SbtCommand.empty.copy(tasks = List("test"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("test"))))
            },
            test("package maps to jar") {
                val cmd   = SbtCommand.empty.copy(tasks = List("package"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("jar"))))
            },
            test("run maps to run") {
                val cmd   = SbtCommand.empty.copy(tasks = List("run"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("run"))))
            },
            test("publishLocal includes full lifecycle") {
                val cmd   = SbtCommand.empty.copy(tasks = List("publishLocal"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publishLocal")
                        )
                    )
                )
            },
            test("publish includes full lifecycle") {
                val cmd   = SbtCommand.empty.copy(tasks = List("publish"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publish")
                        )
                    )
                )
            },
            test("unknown task produces no Mill tasks") {
                val cmd   = SbtCommand.empty.copy(tasks = List("unknownTask"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks.isEmpty))
            },
            test("clean + compile deduplicates") {
                val cmd   = SbtCommand.empty.copy(tasks = List("clean", "compile"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("clean"), MillTask("compile"))))
            },
            test("compile + test deduplicates compile") {
                val cmd   = SbtCommand.empty.copy(tasks = List("compile", "test"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("test"))))
            }
        ),
        suite("project targeting")(
            test("single project prefixes all tasks") {
                val cmd   = SbtCommand.empty.copy(tasks = List("compile"), projects = List("core"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("core.compile"))))
            },
            test("multiple projects expand tasks per project") {
                val cmd   = SbtCommand.empty.copy(tasks = List("test"), projects = List("api", "core"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(
                            MillTask("api.compile"),
                            MillTask("api.test"),
                            MillTask("core.compile"),
                            MillTask("core.test")
                        )
                    )
                )
            },
            test("colon-prefixed project is normalized") {
                val cmd   = SbtCommand.empty.copy(tasks = List("compile"), projects = List(":core"))
                val tasks = SbtCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("core.compile"))))
            }
        )
    )
