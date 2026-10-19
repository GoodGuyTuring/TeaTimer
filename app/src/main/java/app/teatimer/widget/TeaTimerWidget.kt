package app.teatimer.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
 */
class TeaTimerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = BrewController.get(context)
        provideContent {
            val state by controller.state.collectAsState()
            GlanceTheme {
                val sequence = controller.sequenceOf(state)
                if (sequence == null) ChooseContent() else BrewContent(sequence, state)
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
private fun ChooseContent() {
    val context = LocalContext.current
    Column(
        modifier = widgetModifier.clickable(
            actionStartActivity<MainActivity>(actionParametersOf(FromWidgetKey to true)),
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
        Text(
            text = context.getString(R.string.widget_choose_hint),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun BrewContent(sequence: BrewingSequence, state: BrewState) {
    val context = LocalContext.current
    Column(
        modifier = widgetModifier.clickable(actionRunCallback<TapAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sequence.name,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
            )
            Box(
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<ResetAction>()),
            ) {
                Text("✕", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 18.sp))
            }
        }
        Text(
            text = "Infusion ${state.step + 1} of ${sequence.steepSeconds.size}",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.defaultWeight())
        when (state.phase) {
            BrewState.Phase.RUNNING -> AndroidRemoteViews(
                RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                    setChronometer(R.id.countdown, state.endsAt, null, true)
                    setChronometerCountDown(R.id.countdown, true)
                },
            )
            BrewState.Phase.READY -> BigTime(formatSteep(sequence.steepSeconds[state.step]))
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

class ResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).stop()
    }
}

class TeaTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TeaTimerWidget()
}
