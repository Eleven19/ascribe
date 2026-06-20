package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import io.eleven19.kymora.kyo.mill.test as kymoraTest

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
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

trait CommonScalaTestModule extends ScalaModule with scalafmt.ScalafmtModule

trait KyoTestModule extends kymoraTest.KyoTestModule

trait CommonScalaJSModule extends ScalaJSModule with scalafmt.ScalafmtModule {
  def scalaJSVersion = "1.22.0"
}

trait KyoJSTestModule extends kymoraTest.KyoTestJSModule

/** Groups `ascribe/pipeline/{core,html,...}` so Mill discovers `ascribe.pipeline.*` children. */
trait PipelineContainerModule extends Module
