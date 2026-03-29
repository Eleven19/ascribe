package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import io.eleven19.ascribe.pipeline.core.PipelineError
import parsley.{Failure, Success}

/** Resolves `CstInclude` nodes using [[https://github.com/com-lihaoyi/os-lib os-lib]]; behavior matches the Kyo
  * [[io.eleven19.ascribe.pipeline.IncludeResolver]].
  */
object IncludeResolver:

    def resolve(
        cst: CstDocument,
        baseDir: os.Path,
        maxDepth: Int = 64
    ): Either[PipelineError, CstDocument] =
        resolveContent(cst.content, baseDir, maxDepth, 0).map { resolved =>
            cst.copy(content = resolved)(cst.span)
        }

    private def resolveContent(
        content: List[CstTopLevel],
        baseDir: os.Path,
        maxDepth: Int,
        depth: Int
    ): Either[PipelineError, List[CstTopLevel]] =
        content match
            case Nil => Right(Nil)
            case head :: tail =>
                resolveTopLevel(head, baseDir, maxDepth, depth).flatMap { resolved =>
                    resolveContent(tail, baseDir, maxDepth, depth).map { rest =>
                        resolved ++ rest
                    }
                }

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
                        resolveContent(nc.children, baseDir, maxDepth, depth).map { resolved =>
                            List(db.copy(content = nc.copy(resolved)(nc.span))(db.span))
                        }
                    case _ => Right(List(db))

            case other => Right(List(other))

    private def resolveInclude(
        inc: CstInclude,
        baseDir: os.Path,
        maxDepth: Int,
        depth: Int
    ): Either[PipelineError, List[CstTopLevel]] =
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
            Left(
                PipelineError.ParseError(
                    s"Include depth limit ($maxDepth) exceeded for: ${inc.target}",
                    None
                )
            )
        else if !os.exists(targetPath) then
            if isOptional then Right(Nil)
            else
                Left(
                    PipelineError.ParseError(
                        s"Include file not found: ${inc.target}",
                        None
                    )
                )
        else
            val parentDir = targetPath / os.up
            readString(targetPath).flatMap { content =>
                Ascribe.parseCst(content) match
                    case Success(includedCst) =>
                        resolveContent(includedCst.content, parentDir, maxDepth, depth + 1)
                    case Failure(msg) =>
                        Left(
                            PipelineError.ParseError(
                                s"Failed to parse include file ${inc.target}: $msg",
                                None
                            )
                        )
            }

    private def readString(path: os.Path): Either[PipelineError, String] =
        try Right(os.read(path))
        catch
            case e: Exception =>
                Left(PipelineError.IOError(Option(e.getMessage).getOrElse("read failed"), Some(e)))
