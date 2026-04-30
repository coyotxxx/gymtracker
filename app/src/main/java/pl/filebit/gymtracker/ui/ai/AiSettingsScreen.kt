package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ai.AiConfig
import pl.filebit.gymtracker.ai.AiProvider

private val ANTHROPIC_MODELS = listOf("claude-opus-4-7", "claude-sonnet-4-6", "claude-haiku-4-5")
private val OPENAI_MODELS = listOf("gpt-4o", "o1", "o3-mini", "gpt-4.1")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cfg = state.config
    val uriHandler = LocalUriHandler.current

    var showInstructions by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Krok 1 — provider
            item {
                StepCard(stepNumber = 1, title = stringResource(R.string.ai_step1_title)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AiProvider.entries.toList()) { p ->
                            FilterChip(
                                selected = cfg.provider == p,
                                onClick = { vm.setProvider(p) },
                                label = {
                                    Text(when (p) {
                                        AiProvider.ANTHROPIC -> "Anthropic Claude"
                                        AiProvider.OPENAI -> "OpenAI GPT"
                                    })
                                }
                            )
                        }
                    }
                }
            }

            // Krok 2 — klucz API
            item {
                StepCard(stepNumber = 2, title = stringResource(R.string.ai_step2_title)) {
                    Text(
                        stringResource(R.string.ai_step2_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showInstructions = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text(" ${stringResource(R.string.ai_show_instructions)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cfg.apiKey,
                        onValueChange = vm::setApiKey,
                        label = { Text(stringResource(R.string.ai_api_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Ukryj" else "Pokaż")
                            }
                        }
                    )
                }
            }

            // Krok 3 — model
            item {
                StepCard(stepNumber = 3, title = stringResource(R.string.ai_step3_title)) {
                    val presets = if (cfg.provider == AiProvider.ANTHROPIC) ANTHROPIC_MODELS
                    else OPENAI_MODELS
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { m ->
                            FilterChip(
                                selected = cfg.model == m,
                                onClick = { vm.setModel(m) },
                                label = { Text(m) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cfg.model,
                        onValueChange = vm::setModel,
                        label = { Text(stringResource(R.string.ai_model_custom_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ai_model_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Test + zapis
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = vm::testConnection,
                        enabled = !state.testing && cfg.apiKey.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.ai_test_button))
                        }
                    }
                    Button(
                        onClick = vm::save,
                        enabled = cfg.apiKey.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }

            // Status testu
            state.testResult?.let { result ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.isEmpty())
                                MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (result.isEmpty())
                                "✅ ${stringResource(R.string.ai_test_success)}"
                            else "❌ $result",
                            modifier = Modifier.padding(16.dp),
                            color = if (result.isEmpty())
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (state.saved) {
                item {
                    Text(
                        stringResource(R.string.ai_saved),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Zaawansowane: system prompt
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.ai_system_prompt_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cfg.systemPrompt,
                            onValueChange = vm::setSystemPrompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            minLines = 6,
                            maxLines = 20
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = vm::resetSystemPrompt) {
                            Text(stringResource(R.string.ai_system_prompt_reset))
                        }
                    }
                }
            }

            // Reset wszystkiego
            if (cfg.apiKey.isNotBlank()) {
                item {
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ai_clear_key))
                    }
                }
            }

            // Bezpieczeństwo
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔒 ${stringResource(R.string.ai_security_title)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.ai_security_text),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showInstructions) {
        InstructionsDialog(
            provider = cfg.provider,
            onOpenLink = { uriHandler.openUri(it) },
            onDismiss = { showInstructions = false }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.ai_clear_confirm_title)) },
            text = { Text(stringResource(R.string.ai_clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearKey()
                    showResetConfirm = false
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "$stepNumber. $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InstructionsDialog(
    provider: AiProvider,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val (title, link, steps) = when (provider) {
        AiProvider.ANTHROPIC -> Triple(
            "Anthropic Claude",
            "https://console.anthropic.com/settings/keys",
            listOf(
                "Otwórz konsolę Anthropic (przycisk poniżej)",
                "Zaloguj się lub utwórz konto",
                "W menu wybierz 'Settings' → 'API Keys'",
                "Kliknij 'Create Key', nadaj nazwę (np. 'GymTracker')",
                "Skopiuj klucz (zaczyna się od 'sk-ant-…')",
                "Wklej go tutaj w polu 'Klucz API' poniżej",
                "Naciśnij 'Test połączenia' aby sprawdzić"
            )
        )
        AiProvider.OPENAI -> Triple(
            "OpenAI ChatGPT",
            "https://platform.openai.com/api-keys",
            listOf(
                "Otwórz panel OpenAI (przycisk poniżej)",
                "Zaloguj się (musisz mieć aktywny billing)",
                "Kliknij 'Create new secret key'",
                "Nadaj nazwę (np. 'GymTracker'), pozostaw uprawnienia 'All'",
                "Skopiuj klucz (zaczyna się od 'sk-…')",
                "Wklej go tutaj w polu 'Klucz API' poniżej",
                "Naciśnij 'Test połączenia' aby sprawdzić"
            )
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title — instrukcja") },
        text = {
            Column {
                steps.forEachIndexed { i, step ->
                    Row {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onOpenLink(link)
                onDismiss()
            }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text(" Otwórz panel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
