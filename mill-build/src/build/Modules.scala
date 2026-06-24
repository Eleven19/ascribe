package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import io.eleven19.kymora.kyo.mill.test as kymoraTest

object Versions:
  val kyo = "1.0.0-RC4+40-1844110f-SNAPSHOT"

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def repositories = Task {
    super.repositories() ++ Seq("https://central.sonatype.com/repository/maven-snapshots/")
  }

  override def scalaVersion = Task {
    "3.8.4"
  }

  override def scalacOptions = Task {
    Seq(
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
      "-language:strictEquality",
      "-deprecation",
      "-feature",
      "-Werror"
    )
  }
}

trait CommonScalaTestModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def repositories = Task {
    super.repositories() ++ Seq("https://central.sonatype.com/repository/maven-snapshots/")
  }
}

trait KyoTestModule extends kymoraTest.KyoTestModule:
  override def kyoVersion = Task {
    Versions.kyo
  }

trait CommonScalaJSModule extends ScalaJSModule with scalafmt.ScalafmtModule {
  override def repositories = Task {
    super.repositories() ++ Seq("https://central.sonatype.com/repository/maven-snapshots/")
  }

  def scalaJSVersion = "1.22.0"
}

trait KyoJSTestModule extends kymoraTest.KyoTestJSModule:
  override def kyoVersion = Task {
    Versions.kyo
  }

/** Groups `ascribe/pipeline/{core,html,...}` so Mill discovers `ascribe.pipeline.*` children. */
trait PipelineContainerModule extends Module
