package io.github.eleven19.mill.interceptor.maven

import caseapp.*

/** Parses raw Maven CLI arguments into a [[MvnCommand]].
  *
  * Named flags are handled by case-app via [[MvnArgParser.Options]]. `-D` system properties use a prefix pattern that
  * case-app cannot express, so they are extracted before case-app sees the args. Remaining positional arguments are
  * classified as lifecycle phases or Maven goals.
  */
object MvnArgParser:

    /** case-app model for Maven's named CLI flags. */
    @AppName("mvn")
    final case class Options(
        @Name("pl") @HelpMessage("Comma-delimited list of specified reactor projects to build")
        projects: Option[String] = None,
        @Name("am") @HelpMessage("If project list is specified, also build projects required by the list")
        alsoMake: Boolean = false,
        @Name("amd") @HelpMessage("If project list is specified, also build projects that depend on the list")
        alsoMakeDependents: Boolean = false,
        @Name("o") @HelpMessage("Work offline")
        offline: Boolean = false,
        @Name("X") @HelpMessage("Produce execution debug output")
        debug: Boolean = false,
        @Name("q") @HelpMessage("Quiet output")
        quiet: Boolean = false,
        @Name("N") @HelpMessage("Do not recurse into sub-projects")
        nonRecursive: Boolean = false,
        @Name("T") @HelpMessage("Thread count for parallel builds")
        threads: Option[Int] = None,
        @Name("P") @HelpMessage("Comma-delimited list of profiles to activate")
        activateProfiles: Option[String] = None,
        @Name("f") @HelpMessage("Force the use of an alternate POM file")
        file: Option[String] = None,
        @Name("e") @HelpMessage("Produce execution error messages")
        errors: Boolean = false,
        @Name("U") @HelpMessage("Forces a check for updated releases and snapshots")
        updateSnapshots: Boolean = false,
        @Name("B") @HelpMessage("Run in non-interactive (batch) mode")
        batchMode: Boolean = false
    )

    /** Maven uses single-dash multi-character flags (e.g. `-pl`, `-am`, `-amd`) which is non-standard. case-app treats
      * multi-char names as long options (requiring `--`). This set lists the Maven flags that need normalising.
      */
    private val mavenShortFlags: Set[String] = Set(
        "-pl",
        "-am",
        "-amd"
    )

    /** Normalise Maven's single-dash multi-char flags to double-dash so case-app can recognise them.
      */
    private def normaliseMavenFlags(args: List[String]): List[String] =
        args.map { arg =>
            if mavenShortFlags.contains(arg) then s"-$arg"
            else arg
        }

    /** Parse a list of Maven CLI arguments into a structured [[MvnCommand]]. */
    def parse(args: List[String]): MvnCommand =
        val (dProps, cleanedArgs) = extractSystemProperties(normaliseMavenFlags(args))
        val skipTests = dProps.exists {
            case ("skipTests", v)       => v.isEmpty || v.equalsIgnoreCase("true")
            case ("maven.test.skip", v) => v.equalsIgnoreCase("true")
            case _                      => false
        }
        val userProps = dProps
            .collect {
                case (k, _) if k == "skipTests" || k == "maven.test.skip" => None
                case (k, v) => Some(k -> (if v.isEmpty then "true" else v))
            }
            .flatten
            .toMap

        CaseApp.detailedParseWithHelp[Options](cleanedArgs) match
            case Left(_) =>
                MvnCommand.empty
            case Right((Left(_), _, _, _)) =>
                MvnCommand.empty
            case Right((Right(options), _, _, remainingArgs)) =>
                val (phases, goals) = classifyPositionalArgs(remainingArgs.all)
                MvnCommand(
                    phases = phases,
                    projects = options.projects.toList.flatMap(_.split(",").toList.map(_.trim)),
                    alsoMake = options.alsoMake,
                    alsoMakeDependents = options.alsoMakeDependents,
                    skipTests = skipTests,
                    profiles = options.activateProfiles.toList.flatMap(_.split(",").toList.map(_.trim)),
                    offline = options.offline,
                    debug = options.debug,
                    quiet = options.quiet,
                    nonRecursive = options.nonRecursive,
                    threads = options.threads,
                    properties = userProps,
                    goals = goals
                )

    /** Extract `-Dkey=value` and `-Dkey` arguments, returning the properties and the remaining args with `-D` entries
      * removed.
      */
    private def extractSystemProperties(args: List[String]): (List[(String, String)], List[String]) =
        val props = List.newBuilder[(String, String)]
        val rest  = List.newBuilder[String]
        for arg <- args do
            if arg.startsWith("-D") then
                val prop = arg.stripPrefix("-D")
                prop.indexOf('=') match
                    case -1  => props += (prop                   -> "")
                    case idx => props += (prop.substring(0, idx) -> prop.substring(idx + 1))
            else rest += arg
        (props.result(), rest.result())

    /** Classify positional arguments as Maven lifecycle phases or unrecognised goals. */
    private def classifyPositionalArgs(args: Seq[String]): (List[MavenPhase], List[String]) =
        val phases = List.newBuilder[MavenPhase]
        val goals  = List.newBuilder[String]
        for arg <- args do
            MavenPhase.fromString(arg) match
                case Some(phase) => phases += phase
                case None        => goals += arg
        (phases.result(), goals.result())
