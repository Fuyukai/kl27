package tf.veriny.kl27.cpu

import org.apache.commons.collections4.queue.CircularFifoQueue
import java.util.*


enum class CPUState {
    halted,
    running,
    errored
}


val opcodeMap: Map<Int, String> = mapOf(
        -1 to "err",
        0x0 to "nop",
        0x1 to "jmpl",
        0x2 to "hlt"
)

// ACTION MAPPING:
// 0 - jump
// 1 - stack push
// 2 - stack pop
// 3 - memory read
// 4 - memory write
// 5 - register read
// 6 - register write
data class Action(val type: Int, val first: Int, val second: Int? = null)

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
    val registers: Array<Register> = Array(8, { i -> Register(bittiness = 16) })
    // Special registers:
    // The program counter, which is the current address.
    val programCounter = Register(bittiness = 32)
    // The stack.
    val stack: Queue<Int>
    // The last error.
    var lastError: String = ""

    // Mostly used for diagnostics.
    // The recent instruction queue.
    val instructionQueue: Queue<Instruction> = CircularFifoQueue<Instruction>(20)
    // The recent action queue.
    val recentActions: Queue<Action> = CircularFifoQueue<Action>(24)

    init {
        // copy into memory the label table and instructions
        this.exeFile.copyLabelTable(this.memory)
        this.exeFile.copyInstructions(this.memory)

        // set the program counter to the current entry point + offset
        this.programCounter.value = this.exeFile.startOffset + 0x01000
        // create the stack
        this.stack = ArrayDeque<Int>(this.exeFile.stackSize.toInt())
    }

    /**
     * Pushes onto the stack.
     */
    fun pushStack(i: Int) {
        if (this.stack.size >= this.exeFile.stackSize)
            throw StackOverflowError()

        this.stack.add(i)
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
        // add to the end of the queue for the main app to poll off of
        this.instructionQueue.add(instruction)

        // MAIN INTERPRETER BLOCK
        // This runs the actual code.
        //println(instruction.opcode.toString())
        when (instruction.opcode.toInt()) {
            0x0 -> {
                // no-op, do nothing
            }
            0x1 -> {
                // JMPL, jump to label
                val offset = this.memory.getLabelOffset(instruction.opcode)
                // we need to set it to 0x01000 + offset
                // otherwise it tries to execute the label table
                val newOffset = 0x01000 + offset
                this.recentActions.add(Action(0, this.programCounter.value - 4, newOffset))
                this.programCounter.value = newOffset
            }
            0x2 -> {
                // HLT, halt
                this.state = CPUState.halted
            }
        // stack ops
            0x3 -> {
                // SL, stack l(oad|literal)
                // loads a literal onto the stack
                try {
                    this.pushStack(instruction.opval.toInt())
                } catch (err: StackOverflowError) {
                    this.state = CPUState.errored
                    this.instructionQueue.add(Instruction(address = this.programCounter.value, opcode = -1, opval = 0))
                    this.lastError = "Stack overflow"
                }
                // add
                this.recentActions.add(Action(1, instruction.opval.toInt()))
            }
            else -> {
                // unknown opcode
                this.state = CPUState.errored
                this.instructionQueue.add(Instruction(address = this.programCounter.value, opcode = -1, opval = 0))
                this.lastError = "Unknown opcode"
            }
        }

        return instruction

    }
}