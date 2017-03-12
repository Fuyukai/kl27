package tf.veriny.kl27.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import tf.veriny.kl27.KL27

object DesktopLauncher {
    @JvmStatic fun main(arg: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration()
        // we need to disable vsync so that the CPU can run a lot faster
        config.useVsync(false)
        config.setIdleFPS(0)
        Lwjgl3Application(KL27("/home/laura/dev/kl27/compiler/testfile.k27"), config)
    }
}
