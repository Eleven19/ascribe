package io.github.eleven19.mill.interceptor.gradle

import kyo.*

object Gradle:

    def run(args: Chunk[String]) = direct {
        val cmd       = GradleArgParser.parse(args.toList)
        val millTasks = GradleCommandMapper.toMillTasks(cmd)

        if millTasks.isEmpty then
            Log.warn("No Mill tasks mapped from the provided Gradle arguments").now
            Abort
                .fail(
                    new IllegalArgumentException(
                        s"No actionable Gradle tasks found in: ${args.toList.mkString(" ")}"
                    )
                )
                .now
        else
            Log.info(s"Gradle tasks: ${cmd.tasks.mkString(", ")}").now
            Log.info(s"Mapped Mill tasks: ${millTasks.map(_.name).mkString(" ")}").now

            if cmd.excludedTasks.nonEmpty then Log.info(s"Excluded tasks: ${cmd.excludedTasks.mkString(", ")}").now

            if cmd.dryRun then Log.info("Dry run mode — Mill will not be executed").now
            else
                cmd.projectProperties.get("version") match
                    case Some(v) => Log.info(s"Project version: $v").now
                    case None    => ()

                val millArgs = Chunk.from(millTasks.flatMap(_.toArgs))
                Log.info(s"Executing: mill ${millArgs.toList.mkString(" ")}").now
                val exitCode = Process
                    .Command("mill" +: millArgs*)
                    .stdin(Process.Input.Inherit)
                    .stdout(Process.Output.Inherit)
                    .stderr(Process.Output.Inherit)
                    .waitFor
                    .now
                if exitCode != 0 then Abort.fail(new RuntimeException(s"Mill exited with code $exitCode")).now
    }
