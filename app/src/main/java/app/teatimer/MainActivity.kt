package app.teatimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.teatimer.model.BrewingSequence
import app.teatimer.model.SampleSequences
import app.teatimer.model.formatSteep
import app.teatimer.ui.theme.TeaTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeaTimerTheme {
                SequenceListScreen(SampleSequences.all)
            }
        }
    }
}

@Composable
fun SequenceListScreen(sequences: List<BrewingSequence>) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            sequences.forEach { sequence ->
                Text(sequence.name, style = MaterialTheme.typography.titleMedium)
                Text(sequence.steepSeconds.joinToString("  ·  ") { formatSteep(it) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SequenceListScreenPreview() {
    TeaTimerTheme {
        SequenceListScreen(SampleSequences.all)
    }
}
