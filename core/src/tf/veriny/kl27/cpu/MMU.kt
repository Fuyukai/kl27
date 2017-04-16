package tf.veriny.kl27.cpu

import java.nio.ByteBuffer

/*
| Section         | Offset             | Description                                    |
| --------------- | ------------------ | -----------------------------------------------|
| Reserved        | 0x00000 - 0x00100  | Reserved space of 256 bytes.                   |
| Label table     | 0x00100 - 0x01000  | 3840 bytes for the label table, 640 labels max |
| Program section | 0x01000 - 0x40000  | Reserved section for program space.            |
| Main memory     | 0x40000 - 0x100000 | Section for main memory.                       |
 */

data class Instruction(val address: Int, val opcode: Short, val opval: Short)

/**
 * Represents the memory for this CPU.
 */
class MMU {
    // allocate a 2MiB ByteArray for main memory
    private var mainMemory = ByteArray(0x1000000)

    /**
     * Reads a single 8-bit integer from memory.
     *
     * Note: This will return a short.
     */
    fun read8(offset: Int): Short {
        return ByteBuffer.wrap(this.mainMemory.copyOfRange(offset, offset + 1)).short
    }

    /**
     * Reads a single 16-bit integer from memory.
     */
    fun read16(offset: Int): Short {
        // probably fast enough lol
        return ByteBuffer.wrap(this.mainMemory.copyOfRange(offset, offset + 2)).short
    }

    /**
     * Reads a single 32-bit integer from memory.
     */
    fun read32(offset: Int) : Int {
        return ByteBuffer.wrap(this.mainMemory.copyOfRange(offset, offset + 5)).int
    }

    /**
     * Reads an instruction from memory, starting at the specified offset.
     */
    fun readInstruction(location: Int): Instruction {
        val first = this.read16(location)
        val second = this.read16(location + 2)
        return Instruction(address = location, opcode = first, opval = second)
    }

    /**
     * Gets the offset for a label in the label table by ID.
     */
    fun getLabelOffset(labelId: Short): Int {
        // since labels are written sequentially by ID, we can just calculate the offset
        // it's 0x00100 + (6 * labelId) + 2 to get the address
        // then just unwrap that
        val offset = 0x00100 + (6 * labelId) + 2
        // read 4 bytes, [offset, offset + 5)
        val ba = this.mainMemory.copyOfRange(offset, offset + 5)
        return ByteBuffer.wrap(ba).int
    }

    /**
     * Write an 8-bit integer into memory.
     */
    fun write8(offset: Int, value: Int) {
        this.mainMemory[offset] = value.toByte()
    }

    fun write8(offset: Int, value: Byte) {
        this.mainMemory[offset] = value
    }

    /**
     * Write a 16-bit integer into memory
     */
    fun write16(offset: Int, value: Int) {
        this.mainMemory[offset] = (value shr 8).toByte()
        this.mainMemory[offset + 1] = value.toByte()
    }

    fun write32(offset: Int, value: Int) {
        val buf = ByteBuffer.allocate(4).putInt(value).array()
        buf.forEachIndexed { index, byte -> this.mainMemory[offset + index] = byte }
    }

}