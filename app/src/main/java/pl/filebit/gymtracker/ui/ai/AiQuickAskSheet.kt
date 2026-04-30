package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.ai.AiRole

/**
 * Mały popup czat — pytanie o aktualny ekran. ModalBottomSheet z mini-konwersacją.
 * VM ma swój scope (resetuje się przy każdym otwarciu via setScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiQuickAskSheet(
    screenLabel: String,
    onDismiss: () -> Unit,
    vm: AiQuickAskViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(screenLabel) {
        vm.setScreen(screenLabel)
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "✨ Zapytaj o ten ekran",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                screenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.heightIn(min = 8.dp))

            // Lista wiadomości — ograniczona wysokość, scrollable
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            "Wpisz pytanie o ten ekran — np. 'Co tu widzę?', 'Jak interpretować te liczby?', 'Co mogę poprawić?'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.messages.size) { idx ->
                    val m = state.messages[idx]
                    QuickAskBubble(m)
                }
                if (state.isLoading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.heightIn(min = 16.dp, max = 16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.heightIn(min = 0.dp))
                            Text(
                                "  AI myśli…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.heightIn(min = 4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = pl.filebit.gymtracker.ui.theme.ErrorRed.copy(alpha = 0.10f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, pl.filebit.gymtracker.ui.theme.ErrorRed.copy(alpha = 0.40f)
                    )
                ) {
                    Text(
                        "❌ $err",
                        modifier = Modifier.padding(8.dp),
                        color = pl.filebit.gymtracker.ui.theme.ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.heightIn(min = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                    placeholder = { Text("Twoje pytanie…") },
                    minLines = 1,
                    maxLines = 3,
                    enabled = !state.isLoading
                )
                IconButton(
                    onClick = {
                        vm.ask(input.trim())
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                }
            }
            Spacer(Modifier.heightIn(min = 8.dp))
        }
    }
}

@Composable
private fun QuickAskBubble(m: QuickAskMessage) {
    val isUser = m.role == AiRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) pl.filebit.gymtracker.ui.theme.AccentOrange
                else pl.filebit.gymtracker.ui.theme.DarkSurface
            ),
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                1.dp, pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            SelectionContainer {
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) androidx.compose.ui.graphics.Color.Black
                    else pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
