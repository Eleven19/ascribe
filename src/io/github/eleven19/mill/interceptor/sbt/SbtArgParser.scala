package io.github.eleven19.mill.interceptor.sbt

import caseapp.*

/** Parses raw sbt CLI arguments into a [[SbtCommand]].
  *
  * sbt batch usage: sbt [options] [command+] e.g. sbt -batch clean compile test Named flags are handled by case-app;
  * remaining positional arguments are treated as sbt task names.
  */
object SbtArgParser:

    @AppName("sbt")
    final case class Options(
        @Name("batch") @HelpMessage("Run in batch mode (no interactive prompt)")
        batch: Boolean = false,
        @Name("no-colors") @HelpMessage("Disable colored output")
        noColor: Boolean = false,
        @Name("offline") @HelpMessage("Use offline mode")
        offline: Boolean = false
    )

    /** sbt uses single-dash long options (e.g. -batch). Normalise to -- for case-app. */
    private val sbtLongFlags = Set("-batch", "-no-colors", "-offline")

    private def normaliseFlags(args: List[String]): List[String] =
        args.map(arg => if sbtLongFlags.contains(arg) then s"-$arg" else arg)

    /** Parse a list of sbt CLI arguments into a structured [[SbtCommand]]. */
    def parse(args: List[String]): SbtCommand =
        CaseApp.detailedParseWithHelp[Options](normaliseFlags(args)) match
            case Left(_) =>
                SbtCommand.empty
            case Right((Left(_), _, _, _)) =>
                SbtCommand.empty
            case Right((Right(options), _, _, remaining)) =>
                val (projects, tasks) = classifyPositional(remaining.all)
                SbtCommand(
                    tasks = tasks,
                    projects = projects,
                    batch = options.batch,
                    offline = options.offline,
                    noColor = options.noColor
                )

    /** Split positional args into "project X" selections and task names. */
    private def classifyPositional(args: Seq[String]): (List[String], List[String]) =
        val projects = List.newBuilder[String]
        val tasks    = List.newBuilder[String]
        var i        = 0
        while i < args.size do
            if args(i) == "project" && i + 1 < args.size then
                projects += args(i + 1)
                i += 2
            else
                tasks += args(i)
                i += 1
        (projects.result(), tasks.result())
