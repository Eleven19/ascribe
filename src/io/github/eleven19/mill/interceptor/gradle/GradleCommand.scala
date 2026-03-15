package io.github.eleven19.mill.interceptor.gradle

/** Parsed representation of a Gradle command invocation. */
final case class GradleCommand(
    tasks: List[GradleTask],
    excludedTasks: List[String],
    projectDir: Option[String],
    offline: Boolean,
    debug: Boolean,
    quiet: Boolean,
    info: Boolean,
    stacktrace: Boolean,
    buildCache: Option[Boolean],
    parallel: Option[Boolean],
    continueOnFailure: Boolean,
    dryRun: Boolean,
    systemProperties: Map[String, String],
    projectProperties: Map[String, String],
    unknownTasks: List[String]
)

object GradleCommand:

    val empty: GradleCommand = GradleCommand(
        tasks = Nil,
        excludedTasks = Nil,
        projectDir = None,
        offline = false,
        debug = false,
        quiet = false,
        info = false,
        stacktrace = false,
        buildCache = None,
        parallel = None,
        continueOnFailure = false,
        dryRun = false,
        systemProperties = Map.empty,
        projectProperties = Map.empty,
        unknownTasks = Nil
    )
