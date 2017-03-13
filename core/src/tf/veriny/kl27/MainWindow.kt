package tf.veriny.kl27

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import tf.veriny.kl27.cpu.CPU
import tf.veriny.kl27.cpu.CPUState
import tf.veriny.kl27.cpu.K27File
import tf.veriny.kl27.cpu.opcodeMap

// If the GPU window should be created.
// This is false currently until the GPU code is written.
const val MAKE_GPU = false


// the KL27 window doesnt interact much
// so we dont need *that* much logic here
// just keep track of errors and draw appropriately
// its just the tty frontend basically
class MainWindow(assembledFile: String) : ApplicationAdapter() {
    // libgdx shit
    internal lateinit var batch: SpriteBatch
    // the generated font
    internal lateinit var mainFont: BitmapFont
    internal lateinit var regFont: BitmapFont
    internal lateinit var jumpFont: BitmapFont
    internal lateinit var dbgFont: BitmapFont
    internal lateinit var errFont: BitmapFont
    // we want to translate the view to the top left
    // so we use a camera view
    internal lateinit var camera: OrthographicCamera

    // KL27 internals
    var mainFile: K27File = K27File(assembledFile)
    lateinit var cpu: CPU

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG
        // create the camera
        this.camera = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        this.camera.setToOrtho(true, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        this.camera.update()

        // make the CPU
        this.cpu = CPU(this.mainFile)
        this.cpu.state = CPUState.running

        this.batch = SpriteBatch()

        // create the fonts required
        // we use a size 8 DejaVu Sans Mono for the info
        val fntGenerator = FreeTypeFontGenerator(Gdx.files.internal("dejavu.ttf"))
        val fntParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        // flip b/c camera is flipped
        fntParam.flip = true
        fntParam.size = 13
        // create the coloured fonts
        this.mainFont = fntGenerator.generateFont(fntParam)
        this.regFont = fntGenerator.generateFont(fntParam)
        this.regFont.color = Color.CHARTREUSE
        this.jumpFont = fntGenerator.generateFont(fntParam)
        this.jumpFont.color = Color.ORANGE

        val dbgFntParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        dbgFntParam.flip = true
        dbgFntParam.size = 16
        this.dbgFont = fntGenerator.generateFont(dbgFntParam)
        this.errFont = fntGenerator.generateFont(dbgFntParam)
        this.errFont.color = Color.RED

        // set the window title
        Gdx.graphics.setTitle("KL27 - ${this.cpu.exeFile.filePath}")

        // spawn a second window for gpu
        if (MAKE_GPU) {
            val app = Gdx.app as Lwjgl3Application
            val config = Lwjgl3WindowConfiguration()
            config.setTitle("KL27 GPU")
            app.newWindow(GPUWindow(), config)
        }

    }

    override fun render() {
        // clear screen black
        // as is appropriate for SPOOKY TTY
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        // use the camera defined above for the batch
        this.batch.projectionMatrix = this.camera.combined
        this.batch.begin()
        // draw the status text
        this.mainFont.draw(batch, "KL27 - CPU ${this.cpu.state} - FPS ${Gdx.graphics.framesPerSecond}", 10f, 15f)

        // draw the registers
        this.cpu.registers.forEachIndexed {
            index, register ->
            this.regFont.draw(this.batch, "R$index - 0x${register.value.toString(16)}",
                    10f, (30 + index * 15).toFloat())
        }

        // draw the program counter
        this.mainFont.draw(batch, "PC - 0x${this.cpu.programCounter.value.toString(16)}",
                10f, 180f)

        // draw the cycle count
        this.mainFont.draw(batch, "Cycle - 0x${this.cpu.cycleCount.toString(16)}", 10f, 195f)

        // draw info about the exe
        this.mainFont.draw(batch, "K27 version: 0x${this.cpu.exeFile.version.toString(16)}", 10f, 225f)
        this.mainFont.draw(batch, "Max stack size: ${this.cpu.exeFile.stackSize}", 10f, 240f)
        this.mainFont.draw(batch, "Cur stack size: ${this.cpu.stack.size}", 10f, 255f)

        // run the CPU if it hasn't crashed
        if (this.cpu.state == CPUState.running) this.cpu.runCycle()

        // draw the most recent instructions
        this.cpu.instructionQueue.forEachIndexed {
            i, ins ->
            run {
                if (ins.opcode.toInt() != -1) {
                    val default = "0x" + ins.opcode.toString(16)
                    this.dbgFont.draw(this.batch,
                            "E: ${opcodeMap.getOrDefault(ins.opcode.toInt(), default)}, " +
                                    "0x${ins.opval.toString(16)} at 0x${ins.address.toString(16)}",
                            180f, (40 + (20 * i)).toFloat())
                } else {
                    this.errFont.draw(this.batch,
                            "X: ${cpu.lastError}",
                            180f, (40 + (20 * i)).toFloat())
                }
            }
        }

        // draw the most recent actions
        this.cpu.recentActions.forEachIndexed {
            index, action ->
            run {
                val where = (15 + index * 15).toFloat()
                when (action.type) {
                // jump
                    0 -> this.jumpFont.draw(this.batch,
                            "J: 0x${action.first.toString(16)} --> 0x${action.second!!.toString(16)}",
                            435f, where)
                // stack push
                    1 -> this.jumpFont.draw(this.batch,
                            "S: PUSH 0x${action.first.toString(16)}",
                            435f, where)
                    // stack pop
                    2 -> this.jumpFont.draw(this.batch,
                            "S: POP #${action.first.toString()}",
                            435f, where)
                    else -> {
                    }
                }
            }
        }


        this.batch.end()
    }

    override fun dispose() {
        this.batch.dispose()
    }
}