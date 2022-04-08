package io.finch

import _root_.argonaut._

package object argonaut extends Encoders with Decoders {

  override protected val printer: PrettyParams = PrettyParams.nospace

  object dropNullKeys extends Encoders with Decoders {
    override protected val printer: PrettyParams = PrettyParams.nospace.copy(dropNullKeys = true)
  }

  /** Provides an implicit [[PrettyParams]] that preserves order of the JSON fields.
    */
  object preserveOrder extends Encoders with Decoders {
    override protected val printer: PrettyParams = PrettyParams.nospace.copy(preserveOrder = true)
  }

  /** Provides an implicit [[PrettyParams]] that both preserves order of the JSON fields and drop null keys.
    */
  object preserveOrderAndDropNullKeys extends Encoders with Decoders {
    override protected val printer: PrettyParams = PrettyParams.nospace.copy(preserveOrder = true, dropNullKeys = true)
  }
}
