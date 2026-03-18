package build

import mill.*
import mill.scalalib.*

/** Configures Scala 3 scaladoc with project metadata for API documentation.
  *
  * Note: The static documentation site (docs/_docs/) is deployed alongside the API docs by the GitHub Actions workflow,
  * not integrated into scaladoc's static site feature. This avoids a Mill/scaladoc compatibility issue where Mill's
  * docResources-based -siteroot conflicts with scaladoc's output directory management.
  */
trait DocSiteSupport extends ScalaModule {

    override def scalaDocOptions = Task {
        Seq(
            "-project", "Ascribe",
            "-project-version", "0.1.0-SNAPSHOT",
            "-project-url", "https://github.com/Eleven19/ascribe",
            "-source-links:github://Eleven19/ascribe/main",
            "-social-links:github::https://github.com/Eleven19/ascribe",
            "-no-link-warnings"
        )
    }
}
