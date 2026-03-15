package io.github.eleven19.mill.interceptor.maven

import kyo.*

object Mvn:

    def run(args: Chunk[String]) = direct {
        val cmd       = MvnArgParser.parse(args.toList)
        val millTasks = MillCommandMapper.toMillTasks(cmd)

        if millTasks.isEmpty then
            Log.warn("No Mill tasks mapped from the provided Maven arguments").now
            Abort
                .fail(
                    new IllegalArgumentException(
                        s"No actionable Maven phases found in: ${args.toList.mkString(" ")}"
                    )
                )
                .now
        else
            Log.info(s"Maven phases: ${cmd.phases.mkString(", ")}").now
            Log.info(s"Mapped Mill tasks: ${millTasks.map(_.name).mkString(" ")}").now

            if cmd.skipTests then Log.info("Tests will be skipped").now

            cmd.projects match
                case Nil  => ()
                case mods => Log.info(s"Target modules: ${mods.mkString(", ")}").now

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
