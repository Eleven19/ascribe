package build

import mill.*
import mill.scalalib.*

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def scalaVersion = Task {
    "3.8.2"
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

trait CommonScalaTestModule extends ScalaModule {
  private val unsafeMemoryAccessOption = "--sun-misc-unsafe-memory-access=allow"

  override def forkArgs = Task {
    super.forkArgs() ++ Seq(unsafeMemoryAccessOption)
  }
}
