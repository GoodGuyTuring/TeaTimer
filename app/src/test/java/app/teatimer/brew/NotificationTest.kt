package app.teatimer.brew

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.teatimer.model.BrewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The active-timer notification: lock screen, buttons, and that the buttons work. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NotificationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val controller = BrewController.get(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Robolectric gives each test a fresh app, but BrewController (which creates the
        // channels) lives for the whole test process.
        Notifications.createChannels(context)
    }

    @After
    fun tearDown() = runBlocking { controller.stop() }

    private fun timerNotification(): Notification? = shadowOf(manager).getNotification(1)

    private fun actions(notification: Notification) = notification.actions.map { it.title.toString() }

    @Test
    fun `running timer shows on the lock screen with Pause and Stop`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        assertNull("no notification before starting", timerNotification())

        controller.toggle()
        val notification = timerNotification()
        assertNotNull("notification while running", notification)
        notification!!
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(listOf("Pause", "Stop"), actions(notification))

        val channel = manager.getNotificationChannel(notification.channelId)
        assertEquals("shown on the lock screen", NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertNull("no sound", channel.sound)
    }

    @Test
    fun `paused timer keeps its notification with Resume`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        controller.toggle()
        controller.toggle()
        assertEquals(listOf("Resume", "Stop"), actions(timerNotification()!!))
    }

    @Test
    fun `notification buttons pause and stop the timer`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        controller.toggle()

        sendAction(NotificationActionReceiver.ACTION_TOGGLE)
        awaitPhase { it.phase == BrewState.Phase.PAUSED }

        sendAction(NotificationActionReceiver.ACTION_STOP)
        awaitPhase { it.isChoosing }
        assertNull("notification removed", timerNotification())
    }

    private fun sendAction(action: String) {
        context.sendBroadcast(Intent(context, NotificationActionReceiver::class.java).setAction(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun awaitPhase(condition: (BrewState) -> Boolean) = withTimeout(5_000) {
        while (!condition(controller.state.value)) delay(20)
    }
}
