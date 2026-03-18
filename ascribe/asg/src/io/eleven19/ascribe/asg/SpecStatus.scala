package io.eleven19.ascribe.asg

/** Indicates the specification provenance of an ASG type.
  *   - `TCK`: Validated against official AsciiDoc TCK test cases
  *   - `SpecDerived`: Designed from the AsciiDoc Language spec, not yet in TCK
  *   - `Custom`: Ascribe-specific extension not in the spec
  */
enum SpecStatus:
    case TCK, SpecDerived, Custom

/** Annotation documenting an ASG type's specification provenance. This is purely documentary and does not affect
  * runtime behavior, codecs, or serialization.
  */
class specStatus(val status: SpecStatus, val note: String = "") extends scala.annotation.StaticAnnotation
