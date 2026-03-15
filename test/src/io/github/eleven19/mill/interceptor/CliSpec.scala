package io.github.eleven19.mill.interceptor

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object CliSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("Cli")(
        suite("parse")(
            test("intercept maven forwards remaining args") {
                val parsed = Cli.parse(Chunk("intercept", "maven", "clean", "test"))
                Sync.defer(
                    assertTrue(parsed match
                        case CliResult.Run(InterceptTool.Maven, args) => args.toList == List("clean", "test")
                        case _                                         => false
                    )
                )
            },
            test("intercept mvn accepts the alias") {
                val parsed = Cli.parse(Chunk("intercept", "mvn", "install"))
                Sync.defer(
                    assertTrue(parsed match
                        case CliResult.Run(InterceptTool.Maven, args) => args.toList == List("install")
                        case _                                         => false
                    )
                )
            },
            test("intercept sbt is supported") {
                val parsed = Cli.parse(Chunk("intercept", "sbt", "compile"))
                Sync.defer(
                    assertTrue(parsed match
                        case CliResult.Run(InterceptTool.Sbt, args) => args.toList == List("compile")
                        case _                                      => false
                    )
                )
            },
            test("intercept gradle is supported") {
                val parsed = Cli.parse(Chunk("intercept", "gradle", "build"))
                Sync.defer(
                    assertTrue(parsed match
                        case CliResult.Run(InterceptTool.Gradle, args) => args.toList == List("build")
                        case _                                         => false
                    )
                )
            },
            test("empty args returns help") {
                val parsed = Cli.parse(Chunk.empty)
                Sync.defer(assertTrue(parsed match
                    case CliResult.Help(None) => true
                    case _                    => false
                ))
            },
            test("intercept without a tool returns usage error") {
                val parsed = Cli.parse(Chunk("intercept"))
                Sync.defer(assertTrue(parsed match
                    case CliResult.Help(Some("Missing intercept tool")) => true
                    case _                                              => false
                ))
            },
            test("unknown top-level command returns help with an error") {
                val parsed = Cli.parse(Chunk("maven", "clean"))
                Sync.defer(assertTrue(parsed match
                    case CliResult.Help(Some("Unsupported command: maven")) => true
                    case _                                                   => false
                ))
            },
            test("unknown intercept tool returns help with an error") {
                val parsed = Cli.parse(Chunk("intercept", "ant", "compile"))
                Sync.defer(assertTrue(parsed match
                    case CliResult.Help(Some("Unsupported intercept tool: ant")) => true
                    case _                                                        => false
                ))
            }
        )
    )
