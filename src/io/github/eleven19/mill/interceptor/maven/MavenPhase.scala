package io.github.eleven19.mill.interceptor.maven

/** Maven lifecycle phases and their mapping to Mill tasks.
  *
  * Each phase maps to one or more [[MillTask]]s that together reproduce the effect of that Maven phase. The tasks are
  * ordered so that earlier prerequisites come first (mirroring the Maven lifecycle).
  */
enum MavenPhase(val millTasks: List[MillTask]) derives CanEqual:
    case Clean    extends MavenPhase(List(MillTask("clean")))
    case Validate extends MavenPhase(Nil)
    case Compile  extends MavenPhase(List(MillTask("compile")))
    case Test     extends MavenPhase(List(MillTask("compile"), MillTask("test")))
    case Package  extends MavenPhase(List(MillTask("compile"), MillTask("test"), MillTask("jar")))
    case Verify   extends MavenPhase(List(MillTask("compile"), MillTask("test")))

    case Install
        extends MavenPhase(List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publishLocal")))
    case Deploy extends MavenPhase(List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publish")))

object MavenPhase:

    def fromString(s: String): Option[MavenPhase] = s.toLowerCase match
        case "clean"    => Some(Clean)
        case "validate" => Some(Validate)
        case "compile"  => Some(Compile)
        case "test"     => Some(Test)
        case "package"  => Some(Package)
        case "verify"   => Some(Verify)
        case "install"  => Some(Install)
        case "deploy"   => Some(Deploy)
        case _          => None
