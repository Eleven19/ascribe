package io.github.eleven19.mill.interceptor.gradle

import io.github.eleven19.mill.interceptor.MillTask
import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object GradleCommandMapperSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("GradleCommandMapper")(
        suite("toMillTasks")(
            test("empty command produces no tasks") {
                val tasks = GradleCommandMapper.toMillTasks(GradleCommand.empty)
                Sync.defer(assertTrue(tasks.isEmpty))
            },
            test("build maps to compile, test, jar") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Build))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(tasks == List(MillTask("compile"), MillTask("test"), MillTask("jar")))
                )
            },
            test("clean + build deduplicates tasks") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Clean, GradleTask.Build))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(tasks == List(MillTask("clean"), MillTask("compile"), MillTask("test"), MillTask("jar")))
                )
            },
            test("compile + test deduplicates compile") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Compile, GradleTask.Test))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("test"))))
            }
        ),
        suite("task exclusion")(
            test("-x test removes test from build") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Build), excludedTasks = List("test"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("jar"))))
            },
            test("-x test removes test from publish") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Publish), excludedTasks = List("test"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(tasks == List(MillTask("compile"), MillTask("jar"), MillTask("publish")))
                )
            },
            test("multiple exclusions are applied") {
                val cmd = GradleCommand.empty.copy(
                    tasks = List(GradleTask.Build),
                    excludedTasks = List("test", "jar")
                )
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"))))
            }
        ),
        suite("project directory")(
            test("project dir prefixes all tasks") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Build), projectDir = Some("core"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(MillTask("core.compile"), MillTask("core.test"), MillTask("core.jar"))
                    )
                )
            },
            test("colon-prefixed project dir is normalized") {
                val cmd   = GradleCommand.empty.copy(tasks = List(GradleTask.Compile), projectDir = Some(":sub"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("sub.compile"))))
            },
            test("nested colon paths become dots") {
                val cmd = GradleCommand.empty.copy(tasks = List(GradleTask.Compile), projectDir = Some(":a:b"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("a.b.compile"))))
            },
            test("slash paths become dots") {
                val cmd = GradleCommand.empty.copy(tasks = List(GradleTask.Compile), projectDir = Some("a/b"))
                val tasks = GradleCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("a.b.compile"))))
            }
        )
    )
