package build

import mill.*
import mill.scalalib.*

/** Configures Scala 3 scaladoc to generate both API docs and the static documentation site.
  *
  * The `docs/` directory at the repo root contains the static site content in scaladoc's expected format (`_docs/`,
  * `_blog/`, `sidebar.yml`). This trait filters those directories into `docResources` so scaladoc renders prose
  * markdown pages alongside API documentation with consistent styling and navigation.
  *
  * Usage:
  * {{{
  * ./mill ascribeCore.docJar       # Generate docs as JAR
  * ./mill ascribeCore.docSiteServe # Build and serve locally at http://localhost:8080
  * }}}
  */
trait DocSiteSupport extends ScalaModule {

    /** Source reference to the docs/ directory at the workspace root. */
    def docSiteRoot: T[PathRef] = Task.Source(Task.ctx().workspace / "docs")

    /** Filter docResources to include only scaladoc-compatible content (_docs/, _blog/, _assets/, sidebar.yml).
      * This excludes other content in docs/ (plans, specs, contributing guides) that would confuse scaladoc.
      */
    override def docResources = Task {
        val root     = docSiteRoot().path
        val filtered = Task.dest / "filtered"
        os.makeDir.all(filtered)

        val entries = Seq("_docs", "_blog", "_assets", "sidebar.yml")
        for (name <- entries) {
            val src = root / name
            if (os.exists(src)) os.copy(src, filtered / name, createFolders = true)
        }

        Seq(PathRef(filtered))
    }

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

    /** Serve the generated documentation site locally with Python's HTTP server. */
    def docSiteServe(port: Int = 8080) = Task.Command {
        val site = scalaDocGenerated()
        Task.log.info(s"Serving at http://localhost:$port")
        Task.log.info("Press Ctrl+C to stop.")
        os.proc("python3", "-m", "http.server", port.toString)
            .call(cwd = site.path, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
    }
}
