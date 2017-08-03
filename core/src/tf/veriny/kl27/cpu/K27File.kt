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


import ktx.log.logger
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

class K27File(path: String) {
    companion object {
        val logger = logger<K27File>()
    }

    // The path to the K27 file.
    val filePath = path

    // KL27 fields
    var version: Int = -1
    var compressionMode: Short = -1
    var startOffset: Int = -1
    var stackSize: Short = -1
    var checkSum: ByteArray = ByteArray(4)

    var labelCount: Short = 0
    lateinit var labelTable: ByteArray

    lateinit var instructionTable: ByteArray

    init {
        // has the side effect of re-opening and re-reading
        this.reset()
    }

    fun reset() {
        val stream = this.open()
        // parse the header
        this.parseHeader(stream)
        this.parseLabelTable(stream)
        this.readInstructions(stream)
        stream.close()
    }

    /**
     * Opens and reads the K27 file.
     */
    fun open(): FileInputStream {
        return File(this.filePath).inputStream()
    }

    /**
     * Parses a K27 file's header.
     */
    fun parseHeader(stream: FileInputStream) {
        // this is bad(tm)
        val magicNumber = CharArray(4)
        // scan in the magic number
        (0..3).forEach {
            magicNumber[it] = stream.read().toChar()
        }

        val joined = magicNumber.joinToString("")
        if (joined != "KL27") {
            throw RuntimeException("Invalid magic number: " + joined)
        }
        // read in the version
        this.version = stream.read()

        // read the compressed bit
        // 0 - uncompressed, 1 - lzma
        this.compressionMode = stream.read().toShort()

        val j = ByteArray(4)
        // read 4 bytes from the byte stream onto the byte array
        stream.read(j)
        this.startOffset = ByteBuffer.wrap(j).int

        // read the stack size
        val ss = ByteArray(2)
        stream.read(ss)
        this.stackSize = ByteBuffer.wrap(ss).short
        // read the checksum
        stream.read(this.checkSum)
    }

    /**
     * Copies the label table from the file.
     */
    fun parseLabelTable(stream: FileInputStream) {
        this.labelCount = run {
            val ib = ByteArray(2)
            stream.read(ib)

            return@run ByteBuffer.wrap(ib).short
        }

        // copy the labels into the stream
        this.labelTable = ByteArray(size=this.labelCount * 4)
        stream.read(this.labelTable)

        // read the 4 bytes of padding
        (0..4).forEach { stream.read() }
    }

    /**
     * Copies the label table into memory.
     */
    fun copyLabelTable(mem: MMU) {
        // start here to write into memory.
        var offset = 0x00100
        // read each byte from the label table into memory
        this.labelTable.forEach {
            mem.write8(offset, it)
            offset++
        }
    }

    /**
     * Reads the instructions from the file.
     */
    fun readInstructions(stream: FileInputStream) {
        this.instructionTable = stream.readBytes(stream.available())
    }

    /**
     * Copies the instructions into memory.
     */
    fun copyInstructions(mem: MMU) {
        var offset = 0x01000

        // iterate over the bytes in the file and copy them to memory
        this.instructionTable.forEach { b ->
            // copy into memory
            mem.write8(offset, b)
            offset += 1
        }
    }
}