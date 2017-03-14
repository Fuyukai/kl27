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
        0x00 to "nop",
        0x01 to "hlt",
        // stack
        0x02 to "sl",
        0x03 to "spop",
        // register
        0x10 to "rgw",
        0x11 to "rgr",
        // jumps
        0x20 to "jmpl",
        0x21 to "jmpr",
        0x22 to "ret",
        0x23 to "jmpa"
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
    // The memory address register.
    val MAR = Register(bittiness = 32)
    // The memory value register.
    val MVR = Register(bittiness = 16)

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
     * Sets the CPU state to errored.
     */
    fun error(message: String) {
        this.state = CPUState.errored
        this.instructionQueue.add(Instruction(this.programCounter.value, -1, 0))
        this.lastError = message
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
     * Pops from the stack.
     */
    fun popStack(): Int {
        if (this.stack.size <= 0)
            throw RuntimeException("Stack underflow")

        return this.stack.remove()
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
            0x00 -> {
                // no-op, do nothing
            }
            0x01 -> {
                // HLT, halt
                this.state = CPUState.halted
            }
            // stack ops
            0x02 -> {
                // SL, stack l(oad|literal)
                // loads a literal onto the stack
                try { this.pushStack(instruction.opval.toInt()) }
                catch (err: StackOverflowError) {
                    this.error("Stack overflow")
                }
                // add
                this.recentActions.add(Action(1, instruction.opval.toInt()))
            }
            0x03 -> {
                // SPOP, stack pop
                // pops <x> items from the top of the stack
                try { (0..instruction.opval - 1).forEach { this.popStack() } }
                catch (err: RuntimeException) {
                    this.error("Stack underflow")
                }
                this.recentActions.add(Action(2, instruction.opval.toInt()))

            }
            0x10 -> {
                // RGW, register write
                // pops TOS and writes it to register
                this.recentActions.add(Action(2, 1))
                try {
                    val TOS = this.popStack(); this.registers[instruction.opval.toInt()].value = TOS
                    this.recentActions.add(Action(6, instruction.opval.toInt(), TOS))
                }
                catch (err: RuntimeException) {
                    this.error("Stack underflow")
                }
            }
            0x11 -> {
                // RGR, register read
                // reads from the register and copies it to the stack
                val toPush = this.registers[instruction.opval.toInt()].value
                try { this.pushStack(toPush) }
                catch (err: StackOverflowError) {
                    this.error("Stack overflow")
                }
                this.recentActions.add(Action(5, instruction.opval.toInt()))
                this.recentActions.add(Action(1, toPush))
            }
            0x20 -> {
                // JMPL, jump to label
                val offset = this.memory.getLabelOffset(instruction.opcode)
                // we need to set it to 0x01000 + offset
                // otherwise it tries to execute the label table
                val newOffset = 0x01000 + offset
                this.recentActions.add(Action(0, this.programCounter.value - 4, newOffset))
                this.programCounter.value = newOffset
            }
            0x21 -> {
                // JMPR, jump return
                val offset = this.memory.getLabelOffset(instruction.opcode)
                val newOffset = 0x01000 + offset
                // copy the current PC onto R7
                this.registers[0x7].value = this.programCounter.value
                this.recentActions.add(Action(6, 0x7, this.programCounter.value))
                // update the PC value to the place we want to jump
                this.recentActions.add(Action(0, this.programCounter.value - 4, newOffset))
                this.programCounter.value = newOffset
            }
            0x22 -> {
                // RET, return from JMPR
                // this will jump to the location specified in R7
                // it is a shorthand instruction for:
                //  rgr R7
                //  jmpa
                val pcVal = this.registers[0x7].value
                val final = if (pcVal < 0x1000) pcVal + 0x1000 else pcVal
                this.recentActions.add(Action(0, this.programCounter.value -4, this.registers[0x7].value))
                this.programCounter.value = final
            }
            0x23 -> {
                // JMPA, jump absolute
                this.recentActions.add(Action(2, 1))
                try {
                    var TOS = this.popStack()
                    // make sure it's above 0x01000
                    val offset = if (TOS < 0x01000) 0x01000 + TOS else TOS
                    this.recentActions.add(Action(0, this.programCounter.value - 4, offset))
                    this.programCounter.value = offset
                }
                catch (err: StackOverflowError) { this.error("Stack overflow") }
            }
            else -> {
                // unknown opcode
                this.state = CPUState.errored
                this.instructionQueue.add(Instruction(address = this.programCounter.value, opcode = -1, opval = 0))
                this.lastError = "Unknown opcode 0x${instruction.opcode.toString(16)}"
            }
        }

        return instruction

    }
}