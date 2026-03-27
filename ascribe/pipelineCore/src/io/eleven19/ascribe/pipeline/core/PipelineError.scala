package io.eleven19.ascribe.pipeline.core

import io.eleven19.ascribe.ast.DocumentPath

/** Typed errors for pipeline operations. */
enum PipelineError derives CanEqual:
    case ParseError(message: String, source: Option[DocumentPath])
    case IOError(message: String, cause: Option[Throwable])
    case RenderError(message: String)
    case RewriteError(message: String)
