package io.github.eleven19.mill.interceptor

import kyo.*

enum InterceptTool derives CanEqual:
    case Maven, Sbt, Gradle

enum CliResult derives CanEqual:
    case Run(tool: InterceptTool, forwardedArgs: Chunk[String])
    case Help(error: Option[String])

object Cli:

    val usage: String =
        """Usage:
          |  mill-interceptor intercept <maven|mvn|sbt|gradle> [args...]
          |
          |Commands:
          |  intercept  Run the selected tool interceptor
          |""".stripMargin

    def parse(args: Chunk[String]): CliResult =
        args.toList match
            case Nil                       => CliResult.Help(None)
            case "-h" :: _ | "--help" :: _ => CliResult.Help(None)
            case "intercept" :: Nil        => CliResult.Help(Some("Missing intercept tool"))
            case "intercept" :: tool :: rest =>
                tool match
                    case "maven" | "mvn" =>
                        CliResult.Run(InterceptTool.Maven, Chunk.from(rest))
                    case "sbt" =>
                        CliResult.Run(InterceptTool.Sbt, Chunk.from(rest))
                    case "gradle" =>
                        CliResult.Run(InterceptTool.Gradle, Chunk.from(rest))
                    case other =>
                        CliResult.Help(Some(s"Unsupported intercept tool: $other"))
            case command :: _ => CliResult.Help(Some(s"Unsupported command: $command"))
