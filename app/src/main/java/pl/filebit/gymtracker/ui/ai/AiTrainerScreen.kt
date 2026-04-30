package pl.filebit.gymtracker.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.R
import pl.filebit.gymtracker.ai.AiRole
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTrainerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlanApplied: (Long) -> Unit,
    vm: AiTrainerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val toastText = stringResource(R.string.ai_plan_applied_toast)

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(state.planAppliedId) {
        state.planAppliedId?.let {
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            onPlanApplied(it)
            vm.consumePlanAppliedNav()
        }
    }

    LaunchedEffect(Unit) { vm.refreshConnection() }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "PLAN NA TYDZIEŃ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            ),
                            color = AccentOrange
                        )
                        Text(
                            stringResource(R.string.ai_trainer_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = DarkOnSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = vm::clearChat) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = DarkOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = DarkOnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = DarkOnSurface,
                    navigationIconContentColor = DarkOnSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.isConnected) {
                NotConnectedBanner(onOpenSettings = onOpenSettings)
            }

            // Quick actions
            LazyRow(
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QuickAction.entries.toList()) { action ->
                    QuickActionChip(
                        action = action,
                        enabled = state.isConnected && !state.isLoading,
                        onClick = { vm.runQuickAction(action) }
                    )
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.ai_chat_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(state.messages.size) { idx ->
                    val m = state.messages[idx]
                    MessageBubble(message = m)
                }
                if (state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✨✨✨",
                                color = AccentOrange.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                stringResource(R.string.ai_thinking),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "❌ $err",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = vm::clearError) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            }

            // Pasek "Dodaj plan" — widoczny gdy ostatnia odpowiedź AI ma plan
            // do zastosowania. Poza LazyColumn → na pewno reaguje na klik.
            state.pendingProposalMessage?.let { msg ->
                val p = msg.proposal!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(enabled = !state.isApplying) { vm.applyProposal(msg) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  ${stringResource(R.string.ai_applying_plan)}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.height(0.dp))
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    "✨ ${stringResource(R.string.ai_apply_plan)}: ${p.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    "${p.days.size} dni · ${p.totalExercises} ćwiczeń",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.ai_input_placeholder),
                            color = DarkOnSurfaceVariant
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    enabled = state.isConnected && !state.isLoading,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange.copy(alpha = 0.6f),
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = AccentOrange,
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )
                Spacer(Modifier.size(8.dp))
                val sendEnabled = input.isNotBlank() && state.isConnected && !state.isLoading
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (sendEnabled) AccentOrange else DarkSurfaceVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = sendEnabled) {
                            vm.sendMessage(input)
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (sendEnabled) Color.Black else DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConnectedBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { onOpenSettings() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚠️ ${stringResource(R.string.ai_not_connected)}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Icon(Icons.Default.Settings, contentDescription = null)
        }
    }
}

@Composable
private fun QuickActionChip(
    action: QuickAction,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = when (action) {
        QuickAction.PROPOSE_PLAN -> stringResource(R.string.ai_action_plan)
        QuickAction.TODAY -> stringResource(R.string.ai_action_today)
        QuickAction.ANALYZE_PROGRESS -> stringResource(R.string.ai_action_progress)
        QuickAction.DELOAD -> stringResource(R.string.ai_action_deload)
        QuickAction.FULL_STATS -> stringResource(R.string.ai_action_stats)
        QuickAction.WEEKLY_SUMMARY -> stringResource(R.string.ai_action_weekly)
        QuickAction.GOAL_PROGRESS -> stringResource(R.string.ai_action_goal)
    }
    val borderColor = if (enabled) AccentOrange.copy(alpha = 0.45f) else DarkSurfaceVariant
    val textColor = if (enabled) AccentOrange else DarkOnSurfaceVariant
    Box(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(Color.Transparent, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = textColor
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage
) {
    val isUser = message.role == AiRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Awatar AI — żółty kwadracik z gwiazdką
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AccentOrange, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.size(6.dp))
        }
        // Bańki user: pełna żółć + czarny tekst; AI: ciemna z lekką obwódką
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) AccentOrange else DarkSurface
            ),
            border = if (isUser) null else BorderStroke(1.dp, DarkOutlineSoft),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SelectionContainer {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.Black else DarkOnSurface
                    )
                }
                message.proposal?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = AccentOrange.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.40f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "SUGESTIA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.4.sp
                                    ),
                                    color = AccentOrange
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = DarkOnSurface
                            )
                            Text(
                                "${p.days.size} dni · ${p.totalExercises} ćwiczeń",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkOnSurfaceVariant
                            )
                            if (message.applied) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "✅ Plan zapisany w bibliotece",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentOrange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
