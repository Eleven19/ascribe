package build

import mill.*
import mill.scalalib.*

/** Configures Scala 3 scaladoc with project metadata for API documentation. */
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

/** Mixin for assembling and serving the full documentation site (API docs + prose content).
  *
  * Usage:
  * {{{
  * ./mill ascribe.docSiteAssemble   # Build the combined site
  * ./mill ascribe.docSiteServe      # Build and serve locally at http://localhost:8080
  * }}}
  */
trait DocSiteAssembly extends ScalaModule {

    /** Source reference to the prose documentation directory. */
    def docSiteContentDir: T[PathRef] = Task.Source(Task.ctx().workspace / "docs")

    /** Assemble the full documentation site: API docs (scaladoc) + prose content (docs/_docs/). */
    def docSiteAssemble: T[PathRef] = Task {
        val apiDocs    = scalaDocGenerated().path
        val contentDir = docSiteContentDir().path
        val siteDir    = Task.dest / "site"

        os.copy(apiDocs, siteDir, createFolders = true)

        val proseDir  = contentDir / "_docs"
        val blogDir   = contentDir / "_blog"
        val assetsDir = contentDir / "_assets"
        if (os.exists(proseDir)) os.copy(proseDir, siteDir / "docs", createFolders = true)
        if (os.exists(blogDir)) os.copy(blogDir, siteDir / "blog", createFolders = true)
        if (os.exists(assetsDir)) os.copy(assetsDir, siteDir / "assets", createFolders = true)

        Task.log.info(s"Site assembled at: $siteDir")
        PathRef(siteDir)
    }

    /** Assemble and serve locally with Python's HTTP server. */
    def docSiteServe(port: Int = 8080) = Task.Command {
        val site = docSiteAssemble()
        Task.log.info(s"Serving at http://localhost:$port")
        Task.log.info(s"  API docs: http://localhost:$port/index.html")
        Task.log.info(s"  Prose:    http://localhost:$port/docs/")
        Task.log.info("Press Ctrl+C to stop.")
        os.proc("python3", "-m", "http.server", port.toString)
            .call(cwd = site.path, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
    }
}
