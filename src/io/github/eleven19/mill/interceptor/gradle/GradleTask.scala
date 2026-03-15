package io.github.eleven19.mill.interceptor.gradle

import io.github.eleven19.mill.interceptor.MillTask

/** Gradle lifecycle tasks and their mapping to Mill tasks.
  *
  * Each Gradle task maps to one or more [[MillTask]]s that together reproduce the effect of that Gradle task. Tasks are
  * ordered so that earlier prerequisites come first.
  *
  * Unlike Maven's strict lifecycle phases, Gradle tasks are more ad-hoc. The mapping here covers the standard Gradle
  * lifecycle tasks from the `base`, `java`, and `maven-publish` plugins.
  */
enum GradleTask(val millTasks: List[MillTask]) derives CanEqual:
    case Clean       extends GradleTask(List(MillTask("clean")))
    case Classes     extends GradleTask(List(MillTask("compile")))
    case Compile     extends GradleTask(List(MillTask("compile")))
    case CompileJava extends GradleTask(List(MillTask("compile")))
    case Test        extends GradleTask(List(MillTask("compile"), MillTask("test")))
    case Check       extends GradleTask(List(MillTask("compile"), MillTask("test")))
    case Jar         extends GradleTask(List(MillTask("compile"), MillTask("jar")))
    case Assemble    extends GradleTask(List(MillTask("compile"), MillTask("jar")))
    case Build       extends GradleTask(List(MillTask("compile"), MillTask("test"), MillTask("jar")))
    case Publish extends GradleTask(List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publish")))

    case PublishToMavenLocal
        extends GradleTask(List(MillTask("compile"), MillTask("test"), MillTask("jar"), MillTask("publishLocal")))

object GradleTask:

    def fromString(s: String): Option[GradleTask] = s match
        case "clean"               => Some(Clean)
        case "classes"             => Some(Classes)
        case "compile"             => Some(Compile)
        case "compileJava"         => Some(CompileJava)
        case "test"                => Some(Test)
        case "check"               => Some(Check)
        case "jar"                 => Some(Jar)
        case "assemble"            => Some(Assemble)
        case "build"               => Some(Build)
        case "publish"             => Some(Publish)
        case "publishToMavenLocal" => Some(PublishToMavenLocal)
        case _                     => None
