package io.github.eleven19.mill.interceptor.sbt

/** Parsed representation of an sbt command invocation (batch-style arguments). */
final case class SbtCommand(
    tasks: List[String],
    projects: List[String],
    batch: Boolean,
    offline: Boolean,
    noColor: Boolean
)

object SbtCommand:

    val empty: SbtCommand = SbtCommand(
        tasks = Nil,
        projects = Nil,
        batch = false,
        offline = false,
        noColor = false
    )
