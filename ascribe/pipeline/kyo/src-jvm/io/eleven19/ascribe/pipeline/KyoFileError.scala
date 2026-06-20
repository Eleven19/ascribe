package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.{<, Abort, FileException, FileFsException, FileReadException, FileWriteException}

private[pipeline] object KyoFileError:

    private def toPipelineError(error: FileException): PipelineError =
        PipelineError.IOError(error.getMessage, Some(error))

    def read[A, S](value: A < (S & Abort[FileReadException])): A < (S & Abort[PipelineError]) =
        Abort.recover[FileReadException](error => Abort.fail(toPipelineError(error)))(value)

    def write[A, S](value: A < (S & Abort[FileWriteException])): A < (S & Abort[PipelineError]) =
        Abort.recover[FileWriteException](error => Abort.fail(toPipelineError(error)))(value)

    def fs[A, S](value: A < (S & Abort[FileFsException])): A < (S & Abort[PipelineError]) =
        Abort.recover[FileFsException](error => Abort.fail(toPipelineError(error)))(value)
