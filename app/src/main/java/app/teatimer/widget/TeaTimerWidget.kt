package app.teatimer.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.teatimer.MainActivity
import app.teatimer.R
import app.teatimer.brew.BrewController
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence
import app.teatimer.model.formatRemaining
import app.teatimer.model.formatSteep

/**
 * Home screen widget.
 *
 * - No tea chosen: a tap opens the app to choose one.
 * - Tea chosen: a tap starts, pauses or resumes the infusion; a double tap restarts it.
 *   The ✕ goes back to choosing.
 * - The bell chip cycles the ring duration (3 → 5 → 10 → 15 s), as in the app.
 */
class TeaTimerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = BrewController.get(context)
        provideContent {
            val state by controller.state.collectAsState()
            val library by controller.libraryFlow.collectAsState()
            val ringSeconds by controller.ringSeconds.collectAsState()
            GlanceTheme {
                val sequence = state.sequenceId?.let { library[it] }
                if (sequence == null) ChooseContent(ringSeconds) else BrewContent(sequence, state, ringSeconds)
            }
        }
    }
}

private val widgetModifier: GlanceModifier
    @Composable get() = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(16.dp)
        .padding(12.dp)

@Composable
private fun ChooseContent(ringSeconds: Int) {
    val context = LocalContext.current
    Column(
        modifier = widgetModifier.clickable(
            actionStartActivity<MainActivity>(actionParametersOf(FromWidgetKey to true)),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
        Text(
            text = context.getString(R.string.widget_choose_hint),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.defaultWeight())
        RingChip(ringSeconds)
    }
}

/** Shows the ring duration; a tap switches to the next one. */
@Composable
private fun RingChip(ringSeconds: Int) {
    Row(
        modifier = GlanceModifier
            .cornerRadius(10.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(actionRunCallback<CycleRingAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_ring),
            contentDescription = "Ring duration",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(14.dp),
        )
        Text(
            text = " $ringSeconds s",
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp),
        )
    }
}

@Composable
private fun BrewContent(sequence: BrewingSequence, state: BrewState, ringSeconds: Int) {
    val context = LocalContext.current
    Column(
        modifier = widgetModifier.clickable(actionRunCallback<TapAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sequence.tea,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
            )
            RingChip(ringSeconds)
            Box(
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<ResetAction>()),
            ) {
                Text("✕", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 18.sp))
            }
        }
        val step = sequence.steps[state.step]
        Text(
            text = sequence.detail,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Text(
            text = buildString {
                append("Infusion ${state.step + 1} of ${sequence.steps.size}")
                step.temp?.let { append(" · $it") }
                if (step.optional) append(" · optional")
            },
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        when (state.phase) {
            BrewState.Phase.RUNNING -> AndroidRemoteViews(
                remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                    setChronometer(R.id.countdown, state.endsAt, null, true)
                    setChronometerCountDown(R.id.countdown, true)
                },
                modifier = GlanceModifier.fillMaxWidth(),
            )
            BrewState.Phase.READY -> BigTime(formatSteep(step.seconds))
            BrewState.Phase.PAUSED -> BigTime(formatRemaining(state.remainingMs))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = when (state.phase) {
                BrewState.Phase.READY -> "Tap to start"
                BrewState.Phase.RUNNING -> "Tap to pause · double-tap to restart"
                BrewState.Phase.PAUSED -> "Paused · tap to resume"
            },
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(2.dp))
    }
}

@Composable
private fun BigTime(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    )
}

/** Extra the app reads to close itself again after a tea is chosen from the widget. */
val FromWidgetKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_FROM_WIDGET)

class TapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).tap()
    }
}

class CycleRingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).cycleRingSeconds()
    }
}

class ResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).stop()
    }
}

class TeaTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TeaTimerWidget()
}
