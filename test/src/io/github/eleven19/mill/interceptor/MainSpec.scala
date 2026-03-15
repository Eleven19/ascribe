package io.github.eleven19.mill.interceptor

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object MainSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("Main")(
        suite("process")(
            test("routes intercept gradle without environment variables") {
                val parsed = Main.process(Chunk("intercept", "gradle", "build"))
                Sync.defer(
                    assertTrue(parsed match
                        case CliResult.Run(InterceptTool.Gradle, args) => args.toList == List("build")
                        case _                                         => false
                    )
                )
            },
            test("keeps direct top-level tool commands invalid") {
                val parsed = Main.process(Chunk("maven", "clean"))
                Sync.defer(assertTrue(parsed match
                    case CliResult.Help(Some("Unsupported command: maven")) => true
                    case _                                                   => false
                ))
            }
        )
    )
