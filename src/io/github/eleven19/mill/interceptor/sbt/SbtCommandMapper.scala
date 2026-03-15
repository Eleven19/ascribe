package io.github.eleven19.mill.interceptor.sbt

import io.github.eleven19.mill.interceptor.MillTask

/** Maps a parsed sbt command to a sequence of [[MillTask]]s.
  *
  * sbt task names are mapped to Mill tasks; test implies compile first (as in sbt). Order is preserved and duplicates
  * removed.
  */
object SbtCommandMapper:

    /** One sbt task name -> one or more Mill tasks (in order). */
    private def sbtTaskToMill(name: String): List[MillTask] =
        name.toLowerCase match
            case "clean"   => List(MillTask("clean"))
            case "compile" => List(MillTask("compile"))
            case "test"    => List(MillTask("compile"), MillTask("test"))
            case "run"     => List(MillTask("run"))
            case "package" => List(MillTask("jar"))
            case "publishlocal" =>
                List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publishLocal"))
            case "publish" => List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publish"))
            case _         => Nil

    /** Convert a [[SbtCommand]] into an ordered, deduplicated sequence of [[MillTask]]s. */
    def toMillTasks(cmd: SbtCommand): List[MillTask] =
        val flat    = cmd.tasks.flatMap(sbtTaskToMill)
        val deduped = dedup(flat)
        cmd.projects match
            case Nil => deduped
            case modules =>
                for
                    m    <- modules
                    task <- deduped
                yield task.withModule(normalizeModule(m))

    private def dedup(tasks: List[MillTask]): List[MillTask] =
        val seen  = scala.collection.mutable.LinkedHashSet.empty[String]
        val build = List.newBuilder[MillTask]
        for t <- tasks do if seen.add(t.name) then build += t
        build.result()

    private def normalizeModule(module: String): String =
        module.stripPrefix(":").replace(':', '.')
