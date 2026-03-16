package build

import mill.*
import mill.scalalib.JavaModule
import mill.scalalib.PublishModule
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

trait PublishSupport extends PublishModule { this: JavaModule =>
  private val defaultPublishVersion = "0.1.0-SNAPSHOT"

  def publishVersion = Task.Input {
    sys.env.getOrElse("ASCRIBE_PUBLISH_VERSION", defaultPublishVersion)
  }

  def pomSettings = Task {
    PomSettings(
      description = "An AsciiDoc parser library for Scala.",
      organization = "io.github.eleven19",
      url = "https://github.com/Eleven19/ascribe",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("Eleven19", "ascribe"),
      developers = Seq(
        Developer(
          id = "DamianReeves",
          name = "Damian Reeves",
          url = "https://github.com/DamianReeves"
        )
      )
    )
  }
}
