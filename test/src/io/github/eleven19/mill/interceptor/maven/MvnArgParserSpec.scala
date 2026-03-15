package io.github.eleven19.mill.interceptor.maven

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

object MvnArgParserSpec extends KyoSpecDefault:

    def spec: Spec[Any, Any] = suite("MvnArgParser")(
        suite("parse - phases")(
            test("empty args produce empty command") {
                val cmd = MvnArgParser.parse(Nil)
                Sync.defer(assertTrue(cmd.phases.isEmpty) && assertTrue(cmd.goals.isEmpty))
            },
            test("single phase is parsed") {
                val cmd = MvnArgParser.parse(List("compile"))
                Sync.defer(assertTrue(cmd.phases == List(MavenPhase.Compile)))
            },
            test("multiple phases are parsed in order") {
                val cmd = MvnArgParser.parse(List("clean", "install"))
                Sync.defer(assertTrue(cmd.phases == List(MavenPhase.Clean, MavenPhase.Install)))
            },
            test("unknown positional args become goals") {
                val cmd = MvnArgParser.parse(List("dependency:tree"))
                Sync.defer(assertTrue(cmd.goals == List("dependency:tree")))
            }
        ),
        suite("parse - flags")(
            test("-o sets offline") {
                val cmd = MvnArgParser.parse(List("-o", "compile"))
                Sync.defer(assertTrue(cmd.offline))
            },
            test("-X sets debug") {
                val cmd = MvnArgParser.parse(List("-X", "compile"))
                Sync.defer(assertTrue(cmd.debug))
            },
            test("-q sets quiet") {
                val cmd = MvnArgParser.parse(List("-q", "compile"))
                Sync.defer(assertTrue(cmd.quiet))
            },
            test("-N sets nonRecursive") {
                val cmd = MvnArgParser.parse(List("-N", "compile"))
                Sync.defer(assertTrue(cmd.nonRecursive))
            },
            test("-T sets threads") {
                val cmd = MvnArgParser.parse(List("-T", "4", "compile"))
                Sync.defer(assertTrue(cmd.threads == Some(4)))
            },
            test("-am sets alsoMake") {
                val cmd = MvnArgParser.parse(List("-am", "-pl", ":core", "compile"))
                Sync.defer(assertTrue(cmd.alsoMake))
            },
            test("-amd sets alsoMakeDependents") {
                val cmd = MvnArgParser.parse(List("-amd", "-pl", ":core", "compile"))
                Sync.defer(assertTrue(cmd.alsoMakeDependents))
            }
        ),
        suite("parse - projects")(
            test("-pl parses single project") {
                val cmd = MvnArgParser.parse(List("-pl", ":core", "compile"))
                Sync.defer(assertTrue(cmd.projects == List(":core")))
            },
            test("-pl parses comma-separated projects") {
                val cmd = MvnArgParser.parse(List("-pl", ":core,:api", "compile"))
                Sync.defer(assertTrue(cmd.projects == List(":core", ":api")))
            }
        ),
        suite("parse - profiles")(
            test("-P parses single profile") {
                val cmd = MvnArgParser.parse(List("-P", "prod", "compile"))
                Sync.defer(assertTrue(cmd.profiles == List("prod")))
            },
            test("-P parses comma-separated profiles") {
                val cmd = MvnArgParser.parse(List("-P", "prod,staging", "compile"))
                Sync.defer(assertTrue(cmd.profiles == List("prod", "staging")))
            }
        ),
        suite("parse - system properties")(
            test("-Dkey=value captures property") {
                val cmd = MvnArgParser.parse(List("-Dapp.version=1.0.0", "compile"))
                Sync.defer(assertTrue(cmd.properties == Map("app.version" -> "1.0.0")))
            },
            test("-Dkey without value becomes true") {
                val cmd = MvnArgParser.parse(List("-DsomeFlag", "compile"))
                Sync.defer(assertTrue(cmd.properties == Map("someFlag" -> "true")))
            },
            test("multiple -D properties are captured") {
                val cmd = MvnArgParser.parse(List("-Dfoo=bar", "-Dbaz=qux", "compile"))
                Sync.defer(assertTrue(cmd.properties == Map("foo" -> "bar", "baz" -> "qux")))
            }
        ),
        suite("parse - skipTests")(
            test("-DskipTests sets skipTests") {
                val cmd = MvnArgParser.parse(List("-DskipTests", "install"))
                Sync.defer(assertTrue(cmd.skipTests))
            },
            test("-DskipTests=true sets skipTests") {
                val cmd = MvnArgParser.parse(List("-DskipTests=true", "install"))
                Sync.defer(assertTrue(cmd.skipTests))
            },
            test("-DskipTests=false does not set skipTests") {
                val cmd = MvnArgParser.parse(List("-DskipTests=false", "install"))
                Sync.defer(assertTrue(!cmd.skipTests))
            },
            test("-Dmaven.test.skip=true sets skipTests") {
                val cmd = MvnArgParser.parse(List("-Dmaven.test.skip=true", "install"))
                Sync.defer(assertTrue(cmd.skipTests))
            },
            test("skipTests properties are not included in user properties") {
                val cmd = MvnArgParser.parse(List("-DskipTests", "-Dfoo=bar", "install"))
                Sync.defer(assertTrue(cmd.properties == Map("foo" -> "bar")))
            }
        ),
        suite("parse - ignored flags")(
            test("-e is accepted without error") {
                val cmd = MvnArgParser.parse(List("-e", "compile"))
                Sync.defer(assertTrue(cmd.phases == List(MavenPhase.Compile)))
            },
            test("-f is accepted and skipped") {
                val cmd = MvnArgParser.parse(List("-f", "pom.xml", "compile"))
                Sync.defer(assertTrue(cmd.phases == List(MavenPhase.Compile)))
            }
        )
    )
