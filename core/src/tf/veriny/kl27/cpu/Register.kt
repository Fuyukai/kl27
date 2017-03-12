package tf.veriny.kl27.cpu

/**
 * Represents a register.
 */
class Register(bittiness: Int = 16) {
    // The internal value for the register.
    // This is stored as a 32-bit integer.
    private var internalValue: Int = 0

    val bittiness: Int

    init {
        if (bittiness > 32) {
            throw ArithmeticException("Bittiness must be less than 32")
        }
        this.bittiness = bittiness
    }

    // An accessible version of the internal value.
    var value: Int
        get() = (this.internalValue shl this.bittiness) shr this.bittiness
        set(v) {
            this.internalValue = v
        }

}
