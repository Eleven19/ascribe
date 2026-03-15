package io.github.eleven19.mill.interceptor.maven

import io.github.eleven19.mill.interceptor.MillTask
import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object MillCommandMapperSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("MillCommandMapper")(
        suite("toMillTasks")(
            test("empty command produces no tasks") {
                val tasks = MillCommandMapper.toMillTasks(MvnCommand.empty)
                Sync.defer(assertTrue(tasks.isEmpty))
            },
            test("single phase maps to its tasks") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Compile))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"))))
            },
            test("test phase includes compile") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Test))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("test"))))
            },
            test("install phase includes full lifecycle") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Install))
                val tasks = MillCommandMapper.toMillTasks(cmd)
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
            test("clean + install deduplicates tasks") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Clean, MavenPhase.Install))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(
                            MillTask("clean"),
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publishLocal")
                        )
                    )
                )
            },
            test("compile + test deduplicates compile") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Compile, MavenPhase.Test))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"), MillTask("test"))))
            },
            test("validate produces no tasks") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Validate))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks.isEmpty))
            }
        ),
        suite("skipTests")(
            test("skipTests removes test tasks from install") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Install), skipTests = true)
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(
                    assertTrue(
                        tasks == List(MillTask("compile"), MillTask("jar"), MillTask("publishLocal"))
                    )
                )
            },
            test("skipTests removes test from test phase") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Test), skipTests = true)
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("compile"))))
            }
        ),
        suite("project targeting")(
            test("single project prefixes all tasks") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Compile), projects = List(":core"))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("core.compile"))))
            },
            test("multiple projects expand tasks per project") {
                val cmd = MvnCommand.empty.copy(phases = List(MavenPhase.Compile), projects = List(":core", ":api"))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("core.compile"), MillTask("api.compile"))))
            },
            test("module normalization strips leading colon") {
                val cmd   = MvnCommand.empty.copy(phases = List(MavenPhase.Compile), projects = List(":sub"))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("sub.compile"))))
            },
            test("module with nested colons converts to dots") {
                val cmd = MvnCommand.empty.copy(phases = List(MavenPhase.Compile), projects = List(":parent:child"))
                val tasks = MillCommandMapper.toMillTasks(cmd)
                Sync.defer(assertTrue(tasks == List(MillTask("parent.child.compile"))))
            }
        )
    )
