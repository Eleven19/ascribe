package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.pipeline.core.PipelineError
import ox.either
import ox.either.{fail, ok}
import parsley.{Failure, Success}

import scala.collection.mutable

/** Resolves `CstInclude` nodes using [[https://github.com/com-lihaoyi/os-lib os-lib]]; behavior matches the Kyo
  * [[io.eleven19.ascribe.pipeline.IncludeResolver]].
  */
object IncludeResolver:

    def resolve(
        cst: CstDocument,
        baseDir: os.Path,
        maxDepth: Int = 64
    ): Either[PipelineError, CstDocument] =
        either:
            val resolved = resolveContent(cst.content, baseDir, maxDepth, 0).ok()
            cst.copy(content = resolved)(cst.span)

    private def resolveContent(
        content: List[CstTopLevel],
        baseDir: os.Path,
        maxDepth: Int,
        depth: Int
    ): Either[PipelineError, List[CstTopLevel]] =
        either:
            val buf = mutable.ListBuffer.empty[CstTopLevel]
            for head <- content do
                buf ++= resolveTopLevel(head, baseDir, maxDepth, depth).ok()
            buf.toList

    private def resolveTopLevel(
        node: CstTopLevel,
        baseDir: os.Path,
        maxDepth: Int,
        depth: Int
    ): Either[PipelineError, List[CstTopLevel]] =
        node match
            case inc: CstInclude =>
                resolveInclude(inc, baseDir, maxDepth, depth)

            case db: CstDelimitedBlock =>
                db.content match
                    case nc: CstNestedContent =>
                        either:
                            val resolved = resolveContent(nc.children, baseDir, maxDepth, depth).ok()
                            List(db.copy(content = nc.copy(resolved)(nc.span))(db.span))
                    case _ => Right(List(db))

            case other => Right(List(other))

    private def resolveInclude(
        inc: CstInclude,
        baseDir: os.Path,
        maxDepth: Int,
        depth: Int
    ): Either[PipelineError, List[CstTopLevel]] =
        either:
            val isOptional =
                inc.attributes.positional.exists { raw =>
                    raw.split(",").map(_.trim).exists { token =>
                        if token.contains("=") then
                            val kv = token.split("=", 2)
                            kv(0).trim == "opts" && kv(1).trim.split(",").map(_.trim).contains("optional")
                        else token == "optional"
                    }
                } ||
                    inc.attributes.named.get("opts").exists(v => v.split(",").map(_.trim).contains("optional"))
            val targetPath = baseDir / os.SubPath(inc.target)
            if depth >= maxDepth then
                PipelineError
                    .ParseError(
                        s"Include depth limit ($maxDepth) exceeded for: ${inc.target}",
                        None
                    )
                    .fail()
            if !os.exists(targetPath) then
                if isOptional then Nil
                else
                    PipelineError
                        .ParseError(
                            s"Include file not found: ${inc.target}",
                            None
                        )
                        .fail()
            else
                val parentDir = targetPath / os.up
                val content   = readString(targetPath).ok()
                Ascribe.parseCst(content) match
                    case Success(includedCst) =>
                        resolveContent(includedCst.content, parentDir, maxDepth, depth + 1).ok()
                    case Failure(msg) =>
                        PipelineError
                            .ParseError(
                                s"Failed to parse include file ${inc.target}: $msg",
                                None
                            )
                            .fail()

    private def readString(path: os.Path): Either[PipelineError, String] =
        try Right(os.read(path))
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("read failed"), Some(e)))
