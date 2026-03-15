package io.github.eleven19.mill.interceptor.gradle

import io.github.eleven19.mill.interceptor.MillTask
import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object GradleTaskSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("GradleTask")(
        suite("fromString")(
            test("parses clean") {
                Sync.defer(assertTrue(GradleTask.fromString("clean") == Some(GradleTask.Clean)))
            },
            test("parses classes") {
                Sync.defer(assertTrue(GradleTask.fromString("classes") == Some(GradleTask.Classes)))
            },
            test("parses compile") {
                Sync.defer(assertTrue(GradleTask.fromString("compile") == Some(GradleTask.Compile)))
            },
            test("parses compileJava") {
                Sync.defer(assertTrue(GradleTask.fromString("compileJava") == Some(GradleTask.CompileJava)))
            },
            test("parses test") {
                Sync.defer(assertTrue(GradleTask.fromString("test") == Some(GradleTask.Test)))
            },
            test("parses check") {
                Sync.defer(assertTrue(GradleTask.fromString("check") == Some(GradleTask.Check)))
            },
            test("parses jar") {
                Sync.defer(assertTrue(GradleTask.fromString("jar") == Some(GradleTask.Jar)))
            },
            test("parses assemble") {
                Sync.defer(assertTrue(GradleTask.fromString("assemble") == Some(GradleTask.Assemble)))
            },
            test("parses build") {
                Sync.defer(assertTrue(GradleTask.fromString("build") == Some(GradleTask.Build)))
            },
            test("parses publish") {
                Sync.defer(assertTrue(GradleTask.fromString("publish") == Some(GradleTask.Publish)))
            },
            test("parses publishToMavenLocal") {
                Sync.defer(assertTrue(GradleTask.fromString("publishToMavenLocal") == Some(GradleTask.PublishToMavenLocal)))
            },
            test("returns None for unknown task") {
                Sync.defer(assertTrue(GradleTask.fromString("unknownTask").isEmpty))
            },
            test("is case-sensitive unlike Maven") {
                Sync.defer(assertTrue(GradleTask.fromString("CLEAN").isEmpty))
            }
        ),
        suite("millTasks")(
            test("Clean maps to clean") {
                Sync.defer(assertTrue(GradleTask.Clean.millTasks == List(MillTask("clean"))))
            },
            test("Build maps to compile, test, jar") {
                Sync.defer(
                    assertTrue(
                        GradleTask.Build.millTasks == List(MillTask("compile"), MillTask("test"), MillTask("jar"))
                    )
                )
            },
            test("Publish maps to compile, test, jar, publish") {
                Sync.defer(
                    assertTrue(
                        GradleTask.Publish.millTasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publish")
                        )
                    )
                )
            },
            test("PublishToMavenLocal maps to compile, test, jar, publishLocal") {
                Sync.defer(
                    assertTrue(
                        GradleTask.PublishToMavenLocal.millTasks == List(
                            MillTask("compile"),
                            MillTask("test"),
                            MillTask("jar"),
                            MillTask("publishLocal")
                        )
                    )
                )
            }
        )
    )
