package io.github.eleven19.mill.interceptor.maven

/** Maps a parsed Maven command to a sequence of [[MillTask]]s. */
object MillCommandMapper:

    /** Convert a [[MvnCommand]] into an ordered, deduplicated sequence of [[MillTask]]s. */
    def toMillTasks(cmd: MvnCommand): List[MillTask] =
        val phaseTasks = dedup(cmd.phases.flatMap(_.millTasks))

        val filtered =
            if cmd.skipTests then phaseTasks.filterNot(_.name == "test")
            else phaseTasks

        val modulePrefixed = cmd.projects match
            case Nil => filtered
            case modules =>
                for
                    m    <- modules
                    task <- filtered
                yield task.withModule(normalizeModule(m))

        modulePrefixed

    /** Deduplicate tasks by name while preserving order (first occurrence wins). */
    private def dedup(tasks: List[MillTask]): List[MillTask] =
        val seen  = scala.collection.mutable.LinkedHashSet.empty[String]
        val build = List.newBuilder[MillTask]
        for t <- tasks do if seen.add(t.name) then build += t
        build.result()

    /** Normalize a Maven module path (e.g. `:sub-module` or `groupId:artifactId`) to a Mill module selector. */
    private def normalizeModule(module: String): String =
        val stripped = module.stripPrefix(":")
        stripped.replace(':', '.')
