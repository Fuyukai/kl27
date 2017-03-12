package tf.veriny.kl27.desktop

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import tf.veriny.kl27.KL27

object DesktopLauncher {
    @JvmStatic fun main(arg: Array<String>) {
        val config = LwjglApplicationConfiguration()
        // we need to disable vsync so that the CPU can run a lot faster
        config.vSyncEnabled = false
        config.foregroundFPS = 0
        config.backgroundFPS = 0
        LwjglApplication(KL27("/home/laura/dev/kl27/compiler/testfile.k27"), config)
    }
}
