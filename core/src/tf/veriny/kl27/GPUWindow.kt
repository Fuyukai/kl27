package tf.veriny.kl27

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class GPUWindow : ApplicationAdapter() {
    internal lateinit var batch: SpriteBatch

    override fun create() {
        // make the batch
        this.batch = SpriteBatch()
    }

    override fun render() {
        // clear screen black
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
