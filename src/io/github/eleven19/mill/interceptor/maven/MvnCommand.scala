package io.github.eleven19.mill.interceptor.maven

/** Parsed representation of a Maven command invocation. */
final case class MvnCommand(
    phases: List[MavenPhase],
    projects: List[String],
    alsoMake: Boolean,
    alsoMakeDependents: Boolean,
    skipTests: Boolean,
    profiles: List[String],
    offline: Boolean,
    debug: Boolean,
    quiet: Boolean,
    nonRecursive: Boolean,
    threads: Option[Int],
    properties: Map[String, String],
    goals: List[String]
)

object MvnCommand:

    val empty: MvnCommand = MvnCommand(
        phases = Nil,
        projects = Nil,
        alsoMake = false,
        alsoMakeDependents = false,
        skipTests = false,
        profiles = Nil,
        offline = false,
        debug = false,
        quiet = false,
        nonRecursive = false,
        threads = None,
        properties = Map.empty,
        goals = Nil
    )
