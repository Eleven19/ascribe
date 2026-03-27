package io.eleven19.ascribe

/** Re-export pipeline-core types at `io.eleven19.ascribe.pipeline` for Kyo bindings and call sites. */
package object pipeline:
    export io.eleven19.ascribe.pipeline.core.{PipelineError, RewriteAction, RewriteRule}
