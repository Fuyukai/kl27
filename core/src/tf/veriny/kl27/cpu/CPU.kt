package tf.veriny.kl27.cpu

/*
Header:
| Name          | Offset | Description                                         |
| ------------- | ------ | --------------------------------------------------- |
| K_MAGIC       | 0x00   | Magic number, always KL27                           |
| K_VERSION     | 0x04   | Version number, currently 1                         |
| K_COMPRESSED  | 0x05   | Compression status, 0 for uncompressed, 1 for LZMA  |
| K_BODY        | 0x06   | 4-byte address of where the the program body starts |
| K_STACKSIZE   | 0x0A   | Maximum stack size. 4 <= n <= 255                   |
| K_CHECKSUM    | 0x0B   | CRC32 checksum of uncompressed body                 |

Label table:
| Name      | Offset       | Description                                       |
| --------- | ------------ | --------------------------------------------------|
| KL_COUNT  | 0x14         | The number of labels in use, up to 65535          |
| KL_LABEL  | 0x16 .. 0xnn | 6 byte labels - 2 for label ID, 4 for offset      |
| KL_END    | 0xnn         | Signifies end of label tabel, marked by 6 x 0xFF  |
 */

enum class CPUState {
    halted,
    running,
    errored
}

class CPU(f: K27File) {
    // The current cycle count of the CPU.
    // This is incremented once for every instruction executed.
    var cycleCount: Long = 0L
    // The current file being executed.
    val exeFile = f
    // The current state of the CPU.
    var state = CPUState.halted
    // The current executed memory system.
    val memory = MMU()
    // The registers for this CPU.
    val registers: Array<Register> = Array<Register>(8, { i -> Register(bittiness = 16) })

    // Special registers:
    // The program counter, which is the current address.
    val programCounter = Register(bittiness = 32)

    init {
        // copy into memory the label table and instructions
        this.exeFile.copyLabelTable(this.memory)
        this.exeFile.copyInstructions(this.memory)

        // set the program counter to the current entry point + offset
        this.programCounter.value = this.exeFile.startOffset + 0x01000
    }

    /**
     * Runs a single cycle of the CPU.
     *
     * Returns the instruction just executed.
     */
    fun runCycle(): Instruction {
        when (this.state) {
            CPUState.halted -> {
                throw RuntimeException("Cannot run cycle on halted CPU")
            }
            CPUState.errored -> {
                throw RuntimeException("Cannot run cycle on errored CPU")
            }
            else -> {
            }
        }

        // increment our cycle count
        this.cycleCount += 1
        // read the next instruction from memory, using the PC value
        val instruction = this.memory.readInstruction(this.programCounter.value)
        this.programCounter.value += 4

        // MAIN INTERPRETER BLOCK
        // This runs the actual code.
        when (instruction.opcode) {
            0x0.toShort() -> {
                // no-op, do nothing
            }
            0x1.toShort() -> {
                // JMPL, jump to label
                // TODO: Implement label lookup
            }
        }

        return instruction

    }
}