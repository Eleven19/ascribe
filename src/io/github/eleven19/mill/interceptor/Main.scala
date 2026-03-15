package io.github.eleven19.mill.interceptor

import kyo.*
import maven.Mvn
import sbt.Sbt
import gradle.Gradle

object Main extends KyoApp:
    private val scribeLog = ScribeLog("io.github.eleven19.mill.interceptor")

    run {
        Log.let(scribeLog) {
            direct {
                process(args) match
                    case CliResult.Run(InterceptTool.Maven, forwardedArgs) =>
                        Mvn.run(forwardedArgs).now
                    case CliResult.Run(InterceptTool.Sbt, forwardedArgs) =>
                        Sbt.run(forwardedArgs).now
                    case CliResult.Run(InterceptTool.Gradle, forwardedArgs) =>
                        Gradle.run(forwardedArgs).now
                    case CliResult.Help(error) =>
                        error match
                            case Some(message) =>
                                Log.error(message).now
                            case None =>
                                ()

                        println(Cli.usage)

                        error match
                            case Some(message) =>
                                Abort.fail(new IllegalArgumentException(message)).now
                            case None =>
                                ()
            }
        }
    }

    def process(args: Chunk[String]): CliResult =
        Cli.parse(args)
end Main
