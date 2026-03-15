package io.github.eleven19.mill.interceptor.maven

import io.github.eleven19.mill.interceptor.MillTask
import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object MavenPhaseSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("MavenPhase")(
        suite("fromString")(
            test("parses clean") {
                Sync.defer(assertTrue(MavenPhase.fromString("clean") == Some(MavenPhase.Clean)))
            },
            test("parses validate") {
                Sync.defer(assertTrue(MavenPhase.fromString("validate") == Some(MavenPhase.Validate)))
            },
            test("parses compile") {
                Sync.defer(assertTrue(MavenPhase.fromString("compile") == Some(MavenPhase.Compile)))
            },
            test("parses test") {
                Sync.defer(assertTrue(MavenPhase.fromString("test") == Some(MavenPhase.Test)))
            },
            test("parses package") {
                Sync.defer(assertTrue(MavenPhase.fromString("package") == Some(MavenPhase.Package)))
            },
            test("parses verify") {
                Sync.defer(assertTrue(MavenPhase.fromString("verify") == Some(MavenPhase.Verify)))
            },
            test("parses install") {
                Sync.defer(assertTrue(MavenPhase.fromString("install") == Some(MavenPhase.Install)))
            },
            test("parses deploy") {
                Sync.defer(assertTrue(MavenPhase.fromString("deploy") == Some(MavenPhase.Deploy)))
            },
            test("is case-insensitive") {
                Sync.defer(assertTrue(MavenPhase.fromString("COMPILE") == Some(MavenPhase.Compile)))
            },
            test("returns None for unknown phase") {
                Sync.defer(assertTrue(MavenPhase.fromString("unknown").isEmpty))
            },
            test("returns None for empty string") {
                Sync.defer(assertTrue(MavenPhase.fromString("").isEmpty))
            }
        ),
        suite("millTasks")(
            test("Clean maps to clean") {
                Sync.defer(assertTrue(MavenPhase.Clean.millTasks == List(MillTask("clean"))))
            },
            test("Validate maps to empty") {
                Sync.defer(assertTrue(MavenPhase.Validate.millTasks.isEmpty))
            },
            test("Compile maps to compile") {
                Sync.defer(assertTrue(MavenPhase.Compile.millTasks == List(MillTask("compile"))))
            },
            test("Test maps to compile and test") {
                Sync.defer(assertTrue(MavenPhase.Test.millTasks == List(MillTask("compile"), MillTask("test"))))
            },
            test("Package maps to compile, test, jar") {
                Sync.defer(
                    assertTrue(
                        MavenPhase.Package.millTasks == List(MillTask("compile"), MillTask("test"), MillTask("jar"))
                    )
                )
            },
            test("Install maps to compile, test, jar, publishLocal") {
                Sync.defer(
                    assertTrue(
                        MavenPhase.Install.millTasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publishLocal")
                        )
                    )
                )
            },
            test("Deploy maps to compile, test, jar, publish") {
                Sync.defer(
                    assertTrue(
                        MavenPhase.Deploy.millTasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publish")
                        )
                    )
                )
            }
        )
    )
