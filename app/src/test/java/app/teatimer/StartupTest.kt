package app.teatimer

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.teatimer.brew.BrewController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Smoke tests: the app and its controller start on a simulated Android (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StartupTest {
    @Test
    fun `controller loads the bundled tea list`() {
        val controller = BrewController.get(ApplicationProvider.getApplicationContext())
        assertTrue(controller.library.sequences.isNotEmpty())
    }

    @Test
    fun `main activity starts`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertTrue(!activity.isFinishing) }
        }
    }
}
