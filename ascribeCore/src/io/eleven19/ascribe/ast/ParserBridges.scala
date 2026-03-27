package io.eleven19.ascribe.ast

import parsley.Parsley
import parsley.position.pos

/** Helper to construct a Span from raw (line, col) tuples returned by Parsley's `pos`. */
def mkSpan(s: (Int, Int), e: (Int, Int)): Span =
    Span(Position(s._1, s._2), Position(e._1, e._2))

/** Bridge for zero-argument AST nodes. Captures start and end position. */
trait PosParserBridge0[+A]:
    def apply()(span: Span): A

    def parser: Parsley[A] =
        (pos <~> pos).map { case (s, e) => apply()(mkSpan(s, e)) }

/** Bridge for single-argument AST nodes. Captures start and end position around the argument. */
trait PosParserBridge1[-A, +B]:
    def apply(a: A)(span: Span): B

    def apply(a: Parsley[A]): Parsley[B] =
        (pos <~> a <~> pos).map { case ((s, a), e) => apply(a)(mkSpan(s, e)) }

/** Bridge for two-argument AST nodes. */
trait PosParserBridge2[-A, -B, +C]:
    def apply(a: A, b: B)(span: Span): C

    def apply(a: Parsley[A], b: Parsley[B]): Parsley[C] =
        (pos <~> a <~> b <~> pos).map { case (((s, a), b), e) => apply(a, b)(mkSpan(s, e)) }

/** Bridge for three-argument AST nodes. */
trait PosParserBridge3[-A, -B, -C, +D]:
    def apply(a: A, b: B, c: C)(span: Span): D

    def apply(a: Parsley[A], b: Parsley[B], c: Parsley[C]): Parsley[D] =
        (pos <~> a <~> b <~> c <~> pos).map { case ((((s, a), b), c), e) =>
            apply(a, b, c)(mkSpan(s, e))
        }
