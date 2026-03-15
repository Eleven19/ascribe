package io.github.eleven19.mill.interceptor

import kyo.*
import scribe.Logger

/** Kyo [[Log.Unsafe]] implementation that delegates to Scribe. */
object ScribeLog:

    def apply(name: String, level: Log.Level = Log.Level.debug): Log =
        Log(new Unsafe.Scribe(Logger(name), level))

    def apply(logger: Logger, level: Log.Level): Log =
        Log(new Unsafe.Scribe(logger, level))

    object Unsafe:

        final class Scribe(logger: Logger, val level: Log.Level) extends Log.Unsafe:

            private inline def msgWithPosition(inline msg: => Text)(using frame: Frame): String =
                s"[${frame.position.show}] $msg"

            inline def trace(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.trace(msgWithPosition(msg))

            inline def trace(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.trace(msgWithPosition(msg), t)

            inline def debug(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.debug(msgWithPosition(msg))

            inline def debug(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.debug(msgWithPosition(msg), t)

            inline def info(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.info(msgWithPosition(msg))

            inline def info(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.info(msgWithPosition(msg), t)

            inline def warn(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.warn(msgWithPosition(msg))

            inline def warn(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.warn(msgWithPosition(msg), t)

            inline def error(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.error(msgWithPosition(msg))

            inline def error(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.error(msgWithPosition(msg), t)
        end Scribe
    end Unsafe
end ScribeLog
