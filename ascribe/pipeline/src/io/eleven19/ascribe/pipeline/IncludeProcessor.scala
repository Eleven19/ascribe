package io.eleven19.ascribe.pipeline

import kyo.{<, IO, Abort, Path, Sync}

/** Preprocesses AsciiDoc source text, resolving `include::` directives by reading referenced files and inlining their
  * content.
  *
  * Phase 1 supports:
  *   - Basic file inclusion: `include::path/to/file.adoc[]`
  *   - Relative path resolution from the current document's directory
  *   - Recursive includes (included files can themselves contain includes)
  *   - `opts=optional` to suppress errors for missing files
  *
  * Not yet supported: `lines`, `tag(s)`, `leveloffset`, `indent`, `encoding`, URL targets.
  */
object IncludeProcessor:

    private val includePattern = """^include::(.+?)\[(.*)\]\s*$""".r

    /** Process all include directives in the source text, resolving relative to `baseDir`. */
    def process(source: String, baseDir: Path): String < (IO & Abort[PipelineError]) =
        processLines(source.linesIterator.toList, baseDir, maxDepth = 64, currentDepth = 0)

    /** Process include directives with a depth limit to prevent infinite recursion. */
    private def processLines(
        lines: List[String],
        baseDir: Path,
        maxDepth: Int,
        currentDepth: Int
    ): String < (IO & Abort[PipelineError]) =
        lines match
            case Nil => ""
            case head :: tail =>
                processLine(head, baseDir, maxDepth, currentDepth).map { processedHead =>
                    processLines(tail, baseDir, maxDepth, currentDepth).map { processedTail =>
                        if processedTail.isEmpty then processedHead
                        else processedHead + "\n" + processedTail
                    }
                }

    private def processLine(
        line: String,
        baseDir: Path,
        maxDepth: Int,
        currentDepth: Int
    ): String < (IO & Abort[PipelineError]) =
        line match
            case includePattern(target, attrs) =>
                val isOptional = attrs.contains("opts=optional") || attrs.contains("optional")
                val targetPath = Path(baseDir.toJava.resolve(target).toString)
                if currentDepth >= maxDepth then
                    Abort.fail(PipelineError.ParseError(
                        s"Include depth limit ($maxDepth) exceeded for: $target",
                        None
                    ))
                else
                    includeFile(targetPath, target, isOptional, maxDepth, currentDepth)
            case _ => line

    private def includeFile(
        targetPath: Path,
        target: String,
        isOptional: Boolean,
        maxDepth: Int,
        currentDepth: Int
    ): String < (IO & Abort[PipelineError]) =
        targetPath.exists.map { exists =>
            if !exists then
                if isOptional then ""
                else
                    Abort.fail(PipelineError.ParseError(
                        s"Include file not found: $target",
                        None
                    ))
            else
                val parentDir = Path(targetPath.toJava.getParent.toString)
                targetPath.read.map { content =>
                    // Recursively process includes in the included content
                    processLines(
                        content.linesIterator.toList,
                        parentDir,
                        maxDepth,
                        currentDepth + 1
                    )
                }
        }
