package io.github.eleven19.mill.interceptor.gradle

import caseapp.*

/** Parses raw Gradle CLI arguments into a [[GradleCommand]].
  *
  * Named flags are handled by case-app via [[GradleArgParser.Options]]. `-D` system properties and `-P` project
  * properties use prefix patterns that case-app cannot express, so they are extracted beforehand. `-x`/`--exclude-task`
  * pairs are also extracted before case-app parsing. Remaining positional arguments are classified as Gradle lifecycle
  * tasks or unknown task names.
  */
object GradleArgParser:

    /** case-app model for Gradle's named CLI flags. */
    @AppName("gradle")
    final case class Options(
        @Name("p") @HelpMessage("Specifies the project directory")
        projectDir: Option[String] = None,
        @HelpMessage("Work offline")
        offline: Boolean = false,
        @Name("d") @HelpMessage("Log in debug mode")
        debug: Boolean = false,
        @Name("q") @HelpMessage("Log errors only")
        quiet: Boolean = false,
        @Name("i") @HelpMessage("Set log level to info")
        info: Boolean = false,
        @Name("s") @HelpMessage("Print out the stacktrace for all exceptions")
        stacktrace: Boolean = false,
        @Name("S") @HelpMessage("Print out the full stacktrace for all exceptions")
        fullStacktrace: Boolean = false,
        @HelpMessage("Enable the Gradle build cache")
        buildCache: Boolean = false,
        @HelpMessage("Disable the Gradle build cache")
        noBuildCache: Boolean = false,
        @HelpMessage("Build projects in parallel")
        parallel: Boolean = false,
        @HelpMessage("Disable parallel project execution")
        noParallel: Boolean = false,
        @Name("c") @Name("continue") @HelpMessage("Continue task execution after a task failure")
        continueOnFailure: Boolean = false,
        @Name("m") @HelpMessage("Run the builds with all task actions disabled")
        dryRun: Boolean = false,
        @HelpMessage("Specifies the Gradle build file to use")
        buildFile: Option[String] = None,
        @HelpMessage("Specifies the settings file to use")
        settingsFile: Option[String] = None,
        @HelpMessage("Specifies an initialization script")
        initScript: Option[String] = None,
        @HelpMessage("Sets the warning mode")
        warningMode: Option[String] = None,
        @HelpMessage("Disables the Gradle daemon")
        noDaemon: Boolean = false,
        @HelpMessage("Uses the Gradle daemon")
        daemon: Boolean = false
    )

    /** Parse a list of Gradle CLI arguments into a structured [[GradleCommand]]. */
    def parse(args: List[String]): GradleCommand =
        val (sysProps, afterSysProps)   = extractPrefixedProperties(args, "-D")
        val (projProps, afterProjProps) = extractPrefixedProperties(afterSysProps, "-P")
        val (excluded, cleanedArgs)     = extractExcludedTasks(afterProjProps)

        CaseApp.detailedParseWithHelp[Options](cleanedArgs) match
            case Left(_) =>
                GradleCommand.empty
            case Right((Left(_), _, _, _)) =>
                GradleCommand.empty
            case Right((Right(options), _, _, remainingArgs)) =>
                val (tasks, unknownTasks) = classifyPositionalArgs(remainingArgs.all)

                val buildCache = (options.buildCache, options.noBuildCache) match
                    case (true, _) => Some(true)
                    case (_, true) => Some(false)
                    case _         => None

                val parallel = (options.parallel, options.noParallel) match
                    case (true, _) => Some(true)
                    case (_, true) => Some(false)
                    case _         => None

                GradleCommand(
                    tasks = tasks,
                    excludedTasks = excluded,
                    projectDir = options.projectDir,
                    offline = options.offline,
                    debug = options.debug,
                    quiet = options.quiet,
                    info = options.info,
                    stacktrace = options.stacktrace || options.fullStacktrace,
                    buildCache = buildCache,
                    parallel = parallel,
                    continueOnFailure = options.continueOnFailure,
                    dryRun = options.dryRun,
                    systemProperties = sysProps,
                    projectProperties = projProps,
                    unknownTasks = unknownTasks
                )

    /** Extract `-<prefix>key=value` and `-<prefix>key` arguments. */
    private def extractPrefixedProperties(
        args: List[String],
        prefix: String
    ): (Map[String, String], List[String]) =
        val props = Map.newBuilder[String, String]
        val rest  = List.newBuilder[String]
        for arg <- args do
            if arg.startsWith(prefix) && arg.length > prefix.length then
                val prop = arg.substring(prefix.length)
                prop.indexOf('=') match
                    case -1  => props += (prop                   -> "true")
                    case idx => props += (prop.substring(0, idx) -> prop.substring(idx + 1))
            else rest += arg
        (props.result(), rest.result())

    /** Extract `-x <task>` and `--exclude-task <task>` pairs from the arg list. */
    private def extractExcludedTasks(args: List[String]): (List[String], List[String]) =
        val excluded = List.newBuilder[String]
        val rest     = List.newBuilder[String]
        var skipNext = false
        for (arg, idx) <- args.zipWithIndex do
            if skipNext then skipNext = false
            else if (arg == "-x" || arg == "--exclude-task") && idx + 1 < args.length then
                excluded += args(idx + 1)
                skipNext = true
            else rest += arg
        (excluded.result(), rest.result())

    /** Classify positional arguments as Gradle lifecycle tasks or unknown task names. */
    private def classifyPositionalArgs(args: Seq[String]): (List[GradleTask], List[String]) =
        val tasks   = List.newBuilder[GradleTask]
        val unknown = List.newBuilder[String]
        for arg <- args do
            GradleTask.fromString(arg) match
                case Some(task) => tasks += task
                case None       => unknown += arg
        (tasks.result(), unknown.result())
