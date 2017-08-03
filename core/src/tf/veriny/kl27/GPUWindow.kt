package tf.veriny.kl27

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import tf.veriny.kl27.cpu.CPU

class GPUWindow(val cpu: CPU) : ApplicationAdapter() {
    internal lateinit var batch: SpriteBatch
    internal lateinit var mainFont: BitmapFont

    // we want to translate the view to the top left
    // so we use a camera view
    internal lateinit var camera: OrthographicCamera

    override fun create() {
        // make the batch
        this.batch = SpriteBatch()
        val fntGenerator = FreeTypeFontGenerator(Gdx.files.internal("dejavu.ttf"))
        val fntParam = FreeTypeFontGenerator.FreeTypeFontParameter()
        // flip b/c camera is flipped
        fntParam.flip = true
        fntParam.size = 13
        // create the coloured fonts
        this.mainFont = fntGenerator.generateFont(fntParam)

        this.camera = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        this.camera.setToOrtho(true, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        this.camera.update()

    }

    override fun render() {
        // clear screen black
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // use the camera defined above for the batch
        this.batch.projectionMatrix = this.camera.combined
        this.batch.begin()

        this.mainFont.draw(this.batch, "KL27 TTY output", 10f, 15f)

        this.batch.end()
    }
}
