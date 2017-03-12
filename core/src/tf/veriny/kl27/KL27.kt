package tf.veriny.kl27

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import tf.veriny.kl27.cpu.CPU
import tf.veriny.kl27.cpu.CPUState
import tf.veriny.kl27.cpu.K27File


// the KL27 window doesnt interact much
// so we dont need *that* much logic here
// just keep track of errors and draw appropriately
// its just the tty frontend basically
class KL27(assembledFile: String) : ApplicationAdapter() {
    // libgdx shit
    internal lateinit var batch: SpriteBatch
    // the generated font
    internal lateinit var font: BitmapFont
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

        // create the new font we need
        this.font = fntGenerator.generateFont(fntParam)
        // set the window title
        Gdx.graphics.setTitle("KL27 - ${this.cpu.exeFile.filePath}")
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
        this.font.draw(batch, "KL27 - CPU ${this.cpu.state} - FPS ${Gdx.graphics.framesPerSecond}", 10f, 15f)

        // draw the registers
        this.cpu.registers.forEachIndexed {
            index, register ->
            this.font.draw(this.batch, "R$index - 0x${register.value.toString(16)}",
                    10f, (30 + index * 15).toFloat())
        }

        // draw the program counter
        this.font.draw(batch, "PC - 0x${this.cpu.programCounter.value.toString(16)}",
                10f, 165f)

        // run the CPU if it hasn't crashed
        if (this.cpu.state == CPUState.running) this.cpu.runCycle()

        this.batch.end()
    }

    override fun dispose() {
        this.batch.dispose()
    }
}
