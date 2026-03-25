package io.eleven19.ascribe.pipeline

import kyo.{<, Sync, Abort, Path}
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.*
import parsley.{Success, Failure}

/** Resolves `CstInclude` nodes in a CST by reading the target files and splicing their parsed content in place.
  *
  * Operates at the CST level, preserving source structure of the included files and enabling nested resolution.
  */
object IncludeResolver:

    def resolve(
        cst: CstDocument,
        baseDir: Path,
        maxDepth: Int = 64
    ): CstDocument < (Sync & Abort[PipelineError]) =
        resolveContent(cst.content, baseDir, maxDepth, 0).map { resolved =>
            cst.copy(content = resolved)(cst.span)
        }

    private def resolveContent(
        content: List[CstTopLevel],
        baseDir: Path,
        maxDepth: Int,
        depth: Int
    ): List[CstTopLevel] < (Sync & Abort[PipelineError]) =
        content match
            case Nil => Nil
            case head :: tail =>
                resolveTopLevel(head, baseDir, maxDepth, depth).flatMap { resolved =>
                    resolveContent(tail, baseDir, maxDepth, depth).map { rest =>
                        resolved ++ rest
                    }
                }

    private def resolveTopLevel(
        node: CstTopLevel,
        baseDir: Path,
        maxDepth: Int,
        depth: Int
    ): List[CstTopLevel] < (Sync & Abort[PipelineError]) =
        node match
            case inc: CstInclude =>
                resolveInclude(inc, baseDir, maxDepth, depth)

            case db: CstDelimitedBlock =>
                db.content match
                    case nc: CstNestedContent =>
                        resolveContent(nc.children, baseDir, maxDepth, depth).map { resolved =>
                            List(db.copy(content = nc.copy(resolved)(nc.span))(db.span))
                        }
                    case _ => List(db)

            case other => List(other)

    private def resolveInclude(
        inc: CstInclude,
        baseDir: Path,
        maxDepth: Int,
        depth: Int
    ): List[CstTopLevel] < (Sync & Abort[PipelineError]) =
        val isOptional = inc.attributes.positional.exists(_.contains("optional")) ||
            inc.attributes.named.get("opts").exists(_.contains("optional"))
        val targetPath = Path(baseDir.toJava.resolve(inc.target).toString)
        if depth >= maxDepth then
            Abort.fail(PipelineError.ParseError(
                s"Include depth limit ($maxDepth) exceeded for: ${inc.target}",
                None
            ))
        else
            targetPath.exists.flatMap { exists =>
                if !exists then
                    if isOptional then List.empty[CstTopLevel]
                    else
                        Abort.fail(PipelineError.ParseError(
                            s"Include file not found: ${inc.target}",
                            None
                        ))
                else
                    val parentDir = Path(targetPath.toJava.getParent.toString)
                    targetPath.read.flatMap { content =>
                        Ascribe.parseCst(content) match
                            case Success(includedCst) =>
                                resolveContent(includedCst.content, parentDir, maxDepth, depth + 1)
                            case Failure(msg) =>
                                Abort.fail(PipelineError.ParseError(
                                    s"Failed to parse include file ${inc.target}: $msg",
                                    None
                                ))
                    }
            }
