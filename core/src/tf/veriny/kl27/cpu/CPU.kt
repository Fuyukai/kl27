package tf.veriny.kl27.cpu

import org.apache.commons.collections4.queue.CircularFifoQueue
import java.util.*


enum class CPUState {
    halted,
    running,
    errored,
    debugging
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
        0x23 to "jmpa",
        // math
        0x30 to "add",
        0x31 to "sub",
        0x32 to "mul",
        0x33 to "div"
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

class CPUSignal : Exception {
    constructor(message: String) : super(message)
    constructor() : super()
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
    val registers: Array<Register> = Array(8, { Register(bittiness = 16) })

    // Special registers:
    // The program counter, which is the current address.
    val programCounter = Register(bittiness = 32)
    // The memory address register.
    val MAR = Register(bittiness = 32)
    // The memory value register.
    val MVR = Register(bittiness = 16)

    // The stack.
    val stack: ArrayDeque<Int>
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
     * Halts the CPU.
     *
     * This will not run if the CPU is errored.
     */
    fun setHalted() =
        if (this.state == CPUState.errored) {} else this.state = CPUState.halted


    /**
     * Sets the state of the CPU to running.
     *
     * This will not run if the CPU is errored.
     */
    fun setRunning() {
        if (this.state == CPUState.errored) return
        this.state = CPUState.running
    }

    /**
     * Toggles the state of the CPU.
     */
    fun toggleState() =
        when(this.state) {
            CPUState.running,
            CPUState.debugging ->
                this.setHalted()
            CPUState.halted -> this.setRunning()
            else -> {}
        }


    /**
     * Sets the CPU state to errored.
     */
    fun error(message: String, raise: Boolean = true) {
        this.state = CPUState.errored
        this.instructionQueue.add(Instruction(this.programCounter.value, -1, 0))
        this.lastError = message

        if (raise)
            throw CPUSignal(message)
    }

    /**
     * Pushes onto the stack.
     */
    fun pushStack(i: Int) {
        if (this.stack.size >= this.exeFile.stackSize)
            this.error("Stack overflow")

        this.stack.add(i)
        this.recentActions.add(Action(1, i))
    }

    /**
     * Pops from the stack.
     */
    fun popStack(): Int {
        if (this.stack.size <= 0)
            this.error("Stack underflow")

        this.recentActions.add(Action(2, 1))
        // because java (tm!)
        return this.stack.removeLast()
    }

    fun readFromReg(regIndex: Int): Int {
        val reg = when(regIndex) {
            in 0..7 -> this.registers[regIndex]
            8 -> this.MAR
            9 -> this.MVR
            10 -> this.programCounter
            else -> { this.error("Unknown register") }
        } as Register

        this.recentActions.add(Action(5, regIndex))

        return reg.value
    }

    /**
     * Writes to the specified register, by index.
     */
    fun writeToReg(regIndex: Int, value: Int) {
        val reg = when(regIndex) {
            in 0..7 -> this.registers[regIndex]
            8 -> this.MAR
            9 -> this.MVR
            10 -> { this.error("Cannot write to PC") }
            else -> { this.error("Unknown register") }
        } as Register

        this.recentActions.add(Action(6, regIndex, value))

        reg.value = value
    }

    /**
     * Runs a single cycle of the CPU.
     *
     * Returns the instruction just executed.
     */
    private fun _runCycle(): Instruction {
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
                catch (err: CPUSignal) {}
            }
            0x03 -> {
                // SPOP, stack pop
                // pops <x> items from the top of the stack
                try { (0..instruction.opval - 1).forEach { this.popStack() } }
                catch (err: CPUSignal) {}
            }
            0x10 -> {
                // RGW, register write
                // pops TOS and writes it to register
                try {
                    val TOS = this.popStack()
                    this.writeToReg(instruction.opval.toInt(), TOS)
                }
                catch (err: CPUSignal) {}
            }
            0x11 -> {
                // RGR, register read
                // reads from the register and copies it to the stack
                try {
                    val toPush = this.readFromReg(instruction.opval.toInt())
                    this.pushStack(toPush)
                }
                catch (err: CPUSignal) {}
            }
            0x20 -> {
                // JMPL, jump to label
                val offset = this.memory.getLabelOffset(instruction.opval)
                // we need to set it to 0x01000 + offset
                // otherwise it tries to execute the label table
                val newOffset = 0x01000 + offset
                this.recentActions.add(Action(0, this.programCounter.value - 4, newOffset))
                this.programCounter.value = newOffset
            }
            0x21 -> {
                // JMPR, jump return
                val offset = this.memory.getLabelOffset(instruction.opval)
                val newOffset = 0x01000 + offset
                // copy the current PC onto R7
                this.writeToReg(7, this.programCounter.value)
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
                    val TOS = this.popStack()
                    // make sure it 's above 0x01000
                    val offset = if (TOS < 0x01000) 0x01000 + TOS else TOS
                    this.programCounter.value = offset
                }
                catch (err: CPUSignal) {}
            }
            // math
            0x30 -> {
                // ADD, addition
                var toAdd: Int
                if (instruction.opval.toInt() == 0) {
                    // pop from stack
                    toAdd = this.popStack()
                } else {
                    toAdd = instruction.opval.toInt()
                }
                println(toAdd)
                val final = this.popStack() + toAdd
                this.pushStack(final)
            }
            else ->
                // unknown opcode
                this.error("Unknown opcode 0x${instruction.opcode.toString(16)}", raise = false)
        }

        return instruction
    }

    fun runCycle(){
        try {
            this._runCycle()
        }
        catch (err: CPUSignal) {}
    }
}