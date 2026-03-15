package io.github.eleven19.mill.interceptor.gradle

import io.github.eleven19.mill.interceptor.MillTask

/** Maps a parsed Gradle command to a sequence of [[MillTask]]s. */
object GradleCommandMapper:

    /** Convert a [[GradleCommand]] into an ordered, deduplicated sequence of [[MillTask]]s.
      *
      * Tasks from excluded Gradle tasks (via `-x`) are filtered out. If a project directory is specified, tasks are
      * prefixed with it as a Mill module selector.
      */
    def toMillTasks(cmd: GradleCommand): List[MillTask] =
        val taskList = dedup(cmd.tasks.flatMap(_.millTasks))

        // Excluded task names map directly to Mill task names.
        // e.g. `-x test` excludes the `test` Mill task specifically,
        // NOT all Mill tasks that Gradle's `test` lifecycle would produce.
        val excludedMillNames = cmd.excludedTasks.toSet

        val filtered = taskList.filterNot(t => excludedMillNames.contains(t.name))

        val modulePrefixed = cmd.projectDir match
            case Some(dir) =>
                val module = normalizeModule(dir)
                filtered.map(_.withModule(module))
            case None => filtered

        modulePrefixed

    /** Deduplicate tasks by name while preserving order (first occurrence wins). */
    private def dedup(tasks: List[MillTask]): List[MillTask] =
        val seen  = scala.collection.mutable.LinkedHashSet.empty[String]
        val build = List.newBuilder[MillTask]
        for t <- tasks do if seen.add(t.name) then build += t
        build.result()

    /** Normalize a Gradle project path (e.g. `:sub:module`) to a Mill module selector. */
    private def normalizeModule(module: String): String =
        val stripped = module.stripPrefix(":")
        stripped.replace(':', '.').replace('/', '.')
