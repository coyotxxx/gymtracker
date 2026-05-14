package pl.filebit.gymtracker.ui.diet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.repository.MealEntryWithMacros
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkBg
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.DarkOutlineSoft
import pl.filebit.gymtracker.ui.theme.DarkSurface
import pl.filebit.gymtracker.ui.theme.DarkSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ScreenHeader
import pl.filebit.gymtracker.ui.theme.SuccessGreen
import kotlin.math.roundToInt

@Composable
fun DietScreen(
    onBack: () -> Unit,
    onNeedsOnboarding: () -> Unit,
    onOpenAdherenceReport: () -> Unit = {},
    onOpenAdjustmentHistory: () -> Unit = {},
    onOpenMealPreferences: () -> Unit = {},
    onOpenShoppingList: () -> Unit = {},
    onEditDietProfile: () -> Unit = {},
    onOpenMealPrep: () -> Unit = {},
    onOpenBarcodeScanner: () -> Unit = {},
    onOpenFoodImageAnalyzer: () -> Unit = {},
    onOpenRecipeBrowser: () -> Unit = {},
    vm: DietViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val aiState by vm.aiPlanState.collectAsStateWithLifecycle()
    val adjustmentPreview by vm.adjustmentPreview.collectAsStateWithLifecycle()
    val substitutePrompt by vm.substitutePrompt.collectAsStateWithLifecycle()
    val slotAlternatives by vm.slotAlternatives.collectAsStateWithLifecycle()
    val hydrationToday by vm.hydrationToday.collectAsStateWithLifecycle()
    val hydrationGoal by vm.hydrationGoal.collectAsStateWithLifecycle()
    val hydrationLogs by vm.hydrationLogs.collectAsStateWithLifecycle()
    val showHydrationDialog by vm.showHydrationDialog.collectAsStateWithLifecycle()
    val showRecoveryDialog by vm.showRecoveryDialog.collectAsStateWithLifecycle()
    val stepsToday by vm.stepsToday.collectAsStateWithLifecycle()
    val showStepsDialog by vm.showStepsDialog.collectAsStateWithLifecycle()
    val hcAvailability by vm.hcAvailability.collectAsStateWithLifecycle()
    val hcHasPermission by vm.hcHasPermission.collectAsStateWithLifecycle()
    val hcPermissionRequest by vm.hcPermissionRequest.collectAsStateWithLifecycle()
    val hcInstallNeeded by vm.hcInstallNeeded.collectAsStateWithLifecycle()
    val hcSyncMessage by vm.hcSyncMessage.collectAsStateWithLifecycle()
    val consumptions by vm.consumptions.collectAsStateWithLifecycle()
    val currentPhase by vm.currentPhase.collectAsStateWithLifecycle()
    val phaseSuggestion by vm.phaseSuggestion.collectAsStateWithLifecycle()
    val phaseCheckMessage by vm.phaseCheckMessage.collectAsStateWithLifecycle()
    val volatilityReport by vm.volatilityReport.collectAsStateWithLifecycle()
    val volatilityDismissed by vm.volatilityDismissed.collectAsStateWithLifecycle()
    val showEmergencyDialog by vm.showEmergencyDialog.collectAsStateWithLifecycle()
    val showDamageControlDialog by vm.showDamageControlDialog.collectAsStateWithLifecycle()
    val damageControlResult by vm.damageControlResult.collectAsStateWithLifecycle()
    val slotRecipes by vm.slotRecipes.collectAsStateWithLifecycle()
    val shownRecipeFor by vm.shownRecipeFor.collectAsStateWithLifecycle()
    val needsOnboarding by vm.needsOnboarding.collectAsStateWithLifecycle()
    var showAlternativesFor by remember { mutableStateOf<MealType?>(null) }
    androidx.compose.runtime.LaunchedEffect(needsOnboarding) {
        if (needsOnboarding) onNeedsOnboarding()
    }
    var addMealForType by remember { mutableStateOf<MealType?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showGoalBreakdown by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showQuickCompose by remember { mutableStateOf(false) }
    var showWeeklyKcal by remember { mutableStateOf(false) }
    // Stan rozwinięcia sekcji
    val expandedSlots = remember { mutableStateMapOf<MealType, Boolean>() }
    var toolsExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.config.mealRemindersEnabled, state.config.mealsPerDay) {
        // Reschedule notyfikacji przy każdej zmianie config-u (oraz przy pierwszym wejściu)
        vm.rescheduleReminders()
    }

    // Health Connect permission launcher
    val hcPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = vm.healthConnectPermissionContract()
    ) { _: Set<String> ->
        vm.onHealthConnectPermissionResult(true)
    }
    androidx.compose.runtime.LaunchedEffect(hcPermissionRequest) {
        if (hcPermissionRequest) {
            hcPermissionLauncher.launch(vm.healthConnectPermissions())
            vm.consumeHcPermissionRequest()
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (hcInstallNeeded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.consumeHcInstallNeeded() },
            title = { Text("Health Connect niezainstalowany", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Aby auto-sync kroków zadziałał, potrzebna jest aplikacja Google Health Connect z Play Store. " +
                        "Po instalacji wróć do GymTracker i włącz toggle ponownie.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.openHealthConnectInstall(ctx) }) {
                    Text("Otwórz Play Store", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.consumeHcInstallNeeded() }) {
                    Text("Anuluj", color = DarkOnSurfaceVariant)
                }
            }
        )
    }

    hcSyncMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.consumeHcSyncMessage() },
            title = { Text("🔗 Health Connect", fontWeight = FontWeight.Bold) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.consumeHcSyncMessage() }) {
                    Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Dieta", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Hero — kcal goal + makro pierścienie + settings + AI generator
                item {
                    // v1.24.30: liczba PLANNED slotów = posiłki do końca dnia
                    // (CONSUMED/SKIPPED nie liczymy).
                    val mealsRemainingTotal = state.groups.count { group ->
                        val st = consumptions[group.type]
                            ?: pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED
                        st == pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED
                    }
                    DayHeroCard(
                        state = state,
                        aiLoading = aiState is pl.filebit.gymtracker.ui.diet.AiPlanState.Loading,
                        onSettings = { showSettings = true },
                        onShowBreakdown = { showGoalBreakdown = true },
                        onGenerateAi = { showStylePicker = true },
                        onQuickCompose = { showQuickCompose = true },
                        mealsRemainingTotal = mealsRemainingTotal
                    )
                }

                // 2. 3 mini kafelki: WODA / KROKI / REGEN (compact, jeden wiersz)
                item {
                    MiniTilesRow(
                        hydrationToday = hydrationToday,
                        hydrationGoal = hydrationGoal,
                        onAddHydration = { ml -> vm.addHydration(ml) },
                        onOpenHydration = { vm.openHydrationLogDialog() },
                        stepsToday = stepsToday,
                        hcConnected = hcHasPermission && state.config.healthConnectSyncEnabled,
                        onOpenSteps = { vm.openStepsDialog() },
                        onOpenRecovery = { vm.openRecoveryDialog() }
                    )
                }

                // 3. Faza diety — kompaktowy ribbon
                item {
                    PhaseRibbon(
                        currentPhase = currentPhase,
                        onCheck = { vm.checkPhaseSuggestion() }
                    )
                }

                // 4. Header "Posiłki" + 3 ikony akcji w prawym rogu
                item {
                    PosilkiHeader(
                        onEmergency = { vm.openEmergencyDialog() },
                        onScanner = onOpenBarcodeScanner,
                        onFotoAi = onOpenFoodImageAnalyzer
                    )
                }

                // v1.24.6: alert wahań kcal (cheat day + niedojadanie w 7 dni)
                volatilityReport?.let { report ->
                    if (!volatilityDismissed) {
                        item {
                            DietVolatilityCard(
                                report = report,
                                onDismiss = { vm.dismissVolatility() }
                            )
                        }
                    }
                }

                // 5. Sekcje posiłków — auto-expand wg stanu (v1.24.27).
                // Maciej: 'posiłek który oczekuje na zjedzenie zawsze otwarty,
                // zjedzony zamknięty, nie zjedzony zamknięty'.
                items(state.groups.size) { idx ->
                    val group = state.groups[idx]
                    val targetKcal = state.perMealKcal
                    val status = consumptions[group.type]
                        ?: pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED
                    val isCurrent = isCurrentSlot(group.timeLabel) &&
                        status == pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED
                    // Domyślny stan ekspand wg statusu (zasada Macieja v1.24.32):
                    // - CONSUMED → collapsed (historia)
                    // - SKIPPED → collapsed (świadoma decyzja)
                    // - PLANNED → expanded (zarówno bieżące jak i przyszłe sloty)
                    val defaultExpanded = when (status) {
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.CONSUMED -> false
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.SKIPPED -> false
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED -> true
                    }
                    val expanded = expandedSlots[group.type] ?: defaultExpanded
                    MealGroupCard(
                        group = group,
                        targetKcalPerMeal = targetKcal,
                        expanded = expanded,
                        onToggleExpanded = { expandedSlots[group.type] = !expanded },
                        consumptionStatus = status,
                        onCycleStatus = { vm.cycleConsumption(group.type) },
                        isCurrent = isCurrent,
                        onAdd = { addMealForType = group.type },
                        onDelete = { id -> vm.deleteMeal(id) },
                        onSwap = { e -> vm.openSubstitutes(e.entry, e.product) },
                        alternativesCount = slotAlternatives[group.type]?.size ?: 0,
                        onShowAlternatives = { showAlternativesFor = group.type },
                        hasRecipe = slotRecipes.containsKey(group.type),
                        onShowRecipe = { vm.showRecipeFor(group.type) }
                    )
                }

                // 6. Collapsible "🛠 Narzędzia (7)"
                item {
                    NarzedziaSection(
                        expanded = toolsExpanded,
                        onToggle = { toolsExpanded = !toolsExpanded },
                        onRaport = onOpenAdherenceReport,
                        onSprawdzKorekte = { vm.checkForAdjustment() },
                        onHistoria = onOpenAdjustmentHistory,
                        onPreferencje = onOpenMealPreferences,
                        onZakupy = onOpenShoppingList,
                        onMealPrep = onOpenMealPrep,
                        onRecipes = onOpenRecipeBrowser
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showSettings) {
        val currentDietProfile by vm.dietProfileFlow.collectAsStateWithLifecycle()
        DietSettingsDialog(
            initial = state.config,
            onSave = { vm.saveConfig(it) },
            onEditProfile = onEditDietProfile,
            onEditWeeklyKcal = { showWeeklyKcal = true },
            onHealthConnectEnableRequest = { vm.startHealthConnectEnableFlow() },
            onHealthConnectDisable = { vm.toggleHealthConnectSync(false) },
            // v1.24.25: szybka edycja profilu (cel + tempo + aktywność)
            currentDietProfile = currentDietProfile,
            onSaveDietProfile = { goalType, pace, activity ->
                vm.saveDietProfileQuick(goalType, pace, activity)
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showWeeklyKcal) {
        WeeklyKcalDialog(
            initial = state.config.weeklyKcalOverrides,
            defaultKcal = state.goal.kcal,
            onSave = { newMap ->
                showWeeklyKcal = false
                vm.saveConfig(state.config.copy(weeklyKcalOverrides = newMap))
            },
            onDismiss = { showWeeklyKcal = false }
        )
    }

    if (showGoalBreakdown) {
        GoalBreakdownDialog(
            goal = state.goal,
            config = state.config,
            onSave = { vm.saveConfig(it) },
            onDismiss = { showGoalBreakdown = false }
        )
    }

    if (showStylePicker) {
        GeneratePlanPreferencesDialog(
            mealsCount = state.config.mealsPerDay,
            onGenerate = { stylePrefs ->
                showStylePicker = false
                vm.generateAiDayPlan(stylePrefs)
            },
            onDismiss = { showStylePicker = false }
        )
    }

    if (showQuickCompose) {
        // Targety per slot — proste 30/40/30 dla 3 posiłków, lub równo
        val mealsCount = state.config.mealsPerDay.coerceAtLeast(1)
        val perMealKcal = state.goal.kcal / mealsCount
        val perMealProt = state.goal.proteinG / mealsCount
        val perMealFat = state.goal.fatG / mealsCount
        QuickComposeDialog(
            products = state.productsAll,
            targetKcalPerSlot = perMealKcal,
            targetProteinPerSlot = perMealProt,
            targetFatPerSlot = perMealFat,
            composeService = vm.quickComposeService,
            onAccept = { mealType, picks ->
                showQuickCompose = false
                vm.quickComposeAdd(mealType, picks)
            },
            onDismiss = { showQuickCompose = false }
        )
    }

substitutePrompt?.let { sp ->
        SubstituteDialog(
            original = sp.original,
            originalGrams = sp.entry.grams,
            substitutes = sp.substitutes,
            tolerance = sp.tolerance,
            onToleranceChange = { vm.changeSubstituteTolerance(it) },
            onSelect = { product, grams -> vm.applySubstitute(product, grams) },
            onDismiss = { vm.dismissSubstitute() }
        )
    }

    showAlternativesFor?.let { mt ->
        AlternativesDialog(
            mealType = mt,
            alternatives = slotAlternatives[mt].orEmpty(),
            onSelect = { alt -> vm.selectAlternative(mt, alt) },
            onDismiss = { showAlternativesFor = null }
        )
    }

    if (showRecoveryDialog) {
        RecoveryDialog(
            onSave = { sleep, sleepQ, stress, hunger, energy, soreness, difficulty ->
                vm.saveRecoveryLog(sleep, sleepQ, stress, hunger, energy, soreness, difficulty)
            },
            onDismiss = { vm.dismissRecoveryDialog() }
        )
    }

    if (showHydrationDialog) {
        HydrationLogDialog(
            logs = hydrationLogs,
            consumedToday = hydrationToday,
            goal = hydrationGoal,
            onDelete = { id -> vm.deleteHydration(id) },
            onAdd = { ml, source -> vm.addHydration(ml, source) },
            onDismiss = { vm.dismissHydrationDialog() }
        )
    }

    if (showStepsDialog) {
        StepsDialog(
            currentSteps = stepsToday,
            onSave = { steps -> vm.setSteps(steps) },
            onDismiss = { vm.dismissStepsDialog() }
        )
    }

    phaseSuggestion?.let { s ->
        DietPhaseDialog(
            suggestion = s,
            onAccept = { vm.acceptPhaseSuggestion() },
            onDismiss = { vm.dismissPhaseSuggestion() }
        )
    }

    // v1.24.47 fix E2E Bug #4: feedback po SPRAWDŹ gdy brak sugestii fazy diety.
    phaseCheckMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.consumePhaseCheckMessage() },
            title = { Text("🎯 Sprawdzenie fazy diety", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.consumePhaseCheckMessage() }) {
                    Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showEmergencyDialog) {
        EmergencyMealDialog(
            onSelectMode = { mode -> vm.generateEmergencyMeal(mode) },
            onOpenDamageControl = { vm.openDamageControlDialog() },
            onDismiss = { vm.dismissEmergencyDialog() }
        )
    }

    // Emergency meal — loading / success / error / saved
    val emergencyMealState by vm.emergencyMealState.collectAsStateWithLifecycle()
    when (val s = emergencyMealState) {
        is DietViewModel.EmergencyMealState.Idle -> { /* nothing */ }
        is DietViewModel.EmergencyMealState.Loading -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { /* nie pozwalamy zamknąć */ },
                title = { Text("⏳ AI generuje posiłek...", fontWeight = FontWeight.Bold) },
                text = { Text("To może potrwać 5-10 sek.") },
                confirmButton = {}
            )
        }
        is DietViewModel.EmergencyMealState.Error -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.dismissEmergencyMeal() },
                title = { Text("❌ Błąd AI", fontWeight = FontWeight.Bold) },
                text = { Text(s.message) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { vm.dismissEmergencyMeal() }) {
                        Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        is DietViewModel.EmergencyMealState.Success -> {
            ScrollableDialogShell(
                title = "${s.modeLabel}: ${s.recipe.name}",
                onDismiss = { vm.dismissEmergencyMeal() },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { vm.dismissEmergencyMeal() }) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = { vm.acceptEmergencyMeal() }) {
                        Text("Dodaj do diety", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                },
                bodyArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "${s.recipe.kcal} kcal · B${s.recipe.proteinG} W${s.recipe.carbsG} T${s.recipe.fatG} · ⏱ ${s.recipe.prepMinutes} min",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                        ),
                        color = AccentOrange
                    )
                }
                Text("SKŁADNIKI", style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp
                ), color = DarkOnSurfaceVariant)
                s.recipe.ingredients.forEach { ing ->
                    Text("• ${ing.productName} — ${ing.grams} g",
                        style = MaterialTheme.typography.bodySmall, color = DarkOnSurface)
                }
                if (s.recipe.instructions.isNotBlank()) {
                    Text("PRZEPIS", style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp
                    ), color = DarkOnSurfaceVariant)
                    Text(s.recipe.instructions,
                        style = MaterialTheme.typography.bodySmall, color = DarkOnSurface)
                }
            }
        }
        is DietViewModel.EmergencyMealState.Saved -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.dismissEmergencyMeal() },
                title = { Text("✓ Dodano", fontWeight = FontWeight.Bold) },
                text = { Text("Posiłek '${s.mealName}' został dodany do dziennika dnia.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { vm.dismissEmergencyMeal() }) {
                        Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

    if (showDamageControlDialog) {
        DamageControlDialog(
            onPickEstimate = { est -> vm.applyDamageControl(est.kcal) },
            onCustomKcal = { kcal -> vm.applyDamageControl(kcal) },
            onDismiss = { vm.dismissDamageControlDialog() }
        )
    }

    damageControlResult?.let { res ->
        DamageControlResultDialog(
            result = res,
            onDismiss = { vm.dismissDamageControlResult() }
        )
    }

    shownRecipeFor?.let { type ->
        slotRecipes[type]?.let { recipe ->
            RecipeDialog(
                recipe = recipe,
                onDismiss = { vm.dismissRecipe() }
            )
        }
    }

    adjustmentPreview?.let { preview ->
        val decision = preview.decision
        val titleText = when (decision.action) {
            pl.filebit.gymtracker.util.AdjustmentAction.HOLD -> "✓ Trzymaj plan"
            pl.filebit.gymtracker.util.AdjustmentAction.DECREASE_KCAL -> "↓ Sugestia: obniż kcal"
            pl.filebit.gymtracker.util.AdjustmentAction.INCREASE_KCAL -> "↑ Sugestia: dodaj kcal"
            pl.filebit.gymtracker.util.AdjustmentAction.DELOAD -> "🔄 Sugestia: deload"
            pl.filebit.gymtracker.util.AdjustmentAction.SIMPLIFY_PLAN -> "🛠 Uprość plan"
            pl.filebit.gymtracker.util.AdjustmentAction.REFEED_DAY -> "🍝 Refeed day (+kcal)"
            pl.filebit.gymtracker.util.AdjustmentAction.NEEDS_MORE_DATA -> "📊 Brak danych"
        }
        ScrollableDialogShell(
            title = titleText,
            onDismiss = { vm.dismissAdjustmentPreview() },
            actions = {
                if (decision.kcalDeltaProposed != 0) {
                    androidx.compose.material3.TextButton(onClick = { vm.dismissAdjustmentPreview() }) {
                        Text("Anuluj", color = DarkOnSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = { vm.applyAdjustment() }) {
                        Text("✓ Zastosuj", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                } else {
                    androidx.compose.material3.TextButton(onClick = { vm.dismissAdjustmentPreview() }) {
                        Text("Rozumiem", color = AccentOrange)
                    }
                }
            },
            bodyArrangement = Arrangement.spacedBy(10.dp)
        ) {
                    if (decision.kcalDeltaProposed != 0) {
                        Text(
                            "${if (decision.kcalDeltaProposed > 0) "+" else ""}${decision.kcalDeltaProposed} kcal → ${decision.newKcal} kcal/dziennie",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = AccentOrange
                        )
                    }
                    // AI explanation jeśli dostępne — bardziej "po ludzku"
                    preview.aiExplanation?.let { aiText ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    "✨ AI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.4.sp
                                    ),
                                    color = AccentOrange
                                )
                                Spacer(Modifier.height(2.dp))
                                pl.filebit.gymtracker.ui.components.AiMarkdown(text = aiText)
                            }
                        }
                    }
                    // Engine explanation (zawsze)
                    Text(
                        decision.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                    if (decision.warnings.isNotEmpty()) {
                        decision.warnings.forEach { w ->
                            Text(
                                "⚠ $w",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentOrange
                            )
                        }
                    }
                    Text(
                        "Pewność: ${decision.confidence}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
        }
    }

    when (val s = aiState) {
        is pl.filebit.gymtracker.ui.diet.AiPlanState.Success -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.consumeAiPlanState() },
                title = { Text("✨ Plan dnia gotowy", fontWeight = FontWeight.Bold) },
                text = { Text(s.message) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { vm.consumeAiPlanState() }) {
                        Text("OK", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        is pl.filebit.gymtracker.ui.diet.AiPlanState.Error -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.consumeAiPlanState() },
                title = { Text("Błąd AI", fontWeight = FontWeight.Bold) },
                text = { Text(s.message) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { vm.consumeAiPlanState() }) {
                        Text("OK")
                    }
                }
            )
        }
        else -> Unit
    }

    // Dialog dodawania posiłku
    addMealForType?.let { mealType ->
        AddMealDialog(
            mealType = mealType,
            allProducts = state.productsAll,
            onDismiss = { addMealForType = null },
            onAdd = { productId, grams ->
                vm.addMeal(productId, grams, mealType)
                addMealForType = null
            },
            onSearchQueryChange = vm::setSearchQuery,
            onCategoryFilterChange = vm::setCategoryFilter,
            searchQuery = state.searchQuery,
            categoryFilter = state.categoryFilter,
            filteredProducts = state.filteredProducts,
            onToggleFavorite = { p -> vm.toggleProductFavorite(p.id, !p.isFavorite) }
        )
    }
}

@Composable
private fun DayHeroCard(
    state: DietUiState,
    aiLoading: Boolean,
    onSettings: () -> Unit,
    onShowBreakdown: () -> Unit,
    onGenerateAi: () -> Unit,
    onQuickCompose: () -> Unit = {},
    mealsRemainingTotal: Int = 0
) {
    val kcalNow = state.totals.kcal.roundToInt()
    val kcalGoal = state.goal.kcal
    val progress = if (kcalGoal > 0) (kcalNow.toFloat() / kcalGoal).coerceIn(0f, 1f) else 0f
    val kcalRemaining = (kcalGoal - kcalNow).coerceAtLeast(0)
    // v1.24.30: mealsLeft = PLANNED sloty do zjedzenia (mockup: "1 posiłek do końca dnia").
    val mealsLeft = mealsRemainingTotal
    val windowStart = state.config.windowStartHour
    val windowEnd = state.config.windowEndHour()
    // v1.24.27 redesign: kcal accentowy (żółty) gdy zaczął jeść; szary gdy 0.
    val kcalColor = if (kcalNow > 0) AccentOrange else DarkOnSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // v1.24.27: nagłówek "DZIŚ · 12:00-20:00" + zębatka po prawej
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DZIŚ · %02d:00–%02d:00".format(windowStart, windowEnd),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    ),
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceVariant, androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ustawienia diety",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Liczba kcal — duża, kolor akcentowy gdy >0
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clickable(onClick = onShowBreakdown)
            ) {
                Text(
                    "$kcalNow",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = kcalColor,
                    letterSpacing = (-1).sp
                )
                Text(
                    " / $kcalGoal kcal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ⓘ",
                    fontSize = 18.sp,
                    color = AccentOrange,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // v1.24.27: kontekstowa info pod liczbą — "Zostało X kcal · N posiłków do końca dnia"
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("Zostało: $kcalRemaining kcal")
                    if (mealsLeft > 0) {
                        append(" · $mealsLeft ")
                        append(if (mealsLeft == 1) "posiłek do końca dnia" else "posiłki do końca dnia")
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = DarkOnSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // Pasek postępu kcal
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentOrange,
                trackColor = DarkSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // v1.24.27: Makro w prostokątach z mini-paskiem (zamiast pierścieni)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MacroBar(
                    label = "BIAŁKO",
                    current = state.totals.protein,
                    goal = state.goal.proteinG.toDouble(),
                    color = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                MacroBar(
                    label = "WĘGLE",
                    current = state.totals.carbs,
                    goal = state.goal.carbsG.toDouble(),
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                MacroBar(
                    label = "TŁUSZCZ",
                    current = state.totals.fat,
                    goal = state.goal.fatG.toDouble(),
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // v1.24.27: krótszy "Plan dnia AI" + "Kompozytor" zgodnie z mockupem
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Plan dnia AI (żółty wypełniony)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            AccentOrange.copy(alpha = if (aiLoading) 0.05f else 1.0f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !aiLoading, onClick = onGenerateAi),
                    contentAlignment = Alignment.Center
                ) {
                    if (aiLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentOrange,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI układa…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AccentOrange,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Plan dnia AI",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color.Black
                            )
                        }
                    }
                }
                // Kompozytor (zielony outline)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(SuccessGreen.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onQuickCompose),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🥗", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Kompozytor",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

/**
 * v1.24.27: nowy MacroBar zastępujący MacroRing (donut).
 * Prostokąt z labelem, wartością i poziomym paskiem postępu.
 * Layout: "LABEL    /goal" (header) + "94 g" (value) + cienki poziomy pasek.
 */
@Composable
private fun MacroBar(
    label: String,
    current: Double,
    goal: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val currentRounded = current.roundToInt()
    val goalRounded = goal.roundToInt()
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.0).toFloat() else 0f

    Box(
        modifier = modifier
            .background(DarkSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    ),
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "/${goalRounded}g",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$currentRounded",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkOnSurface,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    "g",
                    fontSize = 11.sp,
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

@Composable
private fun MacroRing(label: String, current: Double, goal: Double, color: Color) {
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val sz = Size(size.width - stroke, size.height - stroke)
                val ofs = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = DarkSurfaceVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = ofs,
                    size = sz,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = ofs,
                    size = sz,
                    style = Stroke(width = stroke)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${current.roundToInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnSurface
                )
                Text(
                    "/${goal.roundToInt()}g",
                    fontSize = 9.sp,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun MealGroupCard(
    group: MealGroup,
    targetKcalPerMeal: Int,
    expanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    consumptionStatus: pl.filebit.gymtracker.data.entity.MealConsumptionStatus =
        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED,
    onCycleStatus: () -> Unit = {},
    isCurrent: Boolean = false,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onSwap: (MealEntryWithMacros) -> Unit,
    alternativesCount: Int = 0,
    onShowAlternatives: () -> Unit = {},
    hasRecipe: Boolean = false,
    onShowRecipe: () -> Unit = {}
) {
    val isConsumed = consumptionStatus == pl.filebit.gymtracker.data.entity.MealConsumptionStatus.CONSUMED
    val isSkipped = consumptionStatus == pl.filebit.gymtracker.data.entity.MealConsumptionStatus.SKIPPED
    // Border per status (mockup Macieja):
    // CONSUMED → zielony (zjedzone), isCurrent → pomarańczowy (TERAZ),
    // SKIPPED → bez ramki / wyciszone, PLANNED → DarkOutlineSoft.
    val borderColor = when {
        isConsumed -> SuccessGreen.copy(alpha = 0.5f)
        isCurrent -> AccentOrange.copy(alpha = 0.55f)
        isSkipped -> DarkOutlineSoft.copy(alpha = 0.4f)
        else -> DarkOutlineSoft
    }
    val borderWidth = if (isConsumed || isCurrent) 1.5.dp else 1.dp
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header — klikalny dla toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge — clickable cycle PLANNED → CONSUMED → SKIPPED → PLANNED
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onCycleStatus),
                    contentAlignment = Alignment.Center
                ) {
                    when (consumptionStatus) {
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.CONSUMED -> Text(
                            "✓",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = SuccessGreen
                        )
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.SKIPPED -> Text(
                            "✗",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = DarkOnSurfaceVariant
                        )
                        pl.filebit.gymtracker.data.entity.MealConsumptionStatus.PLANNED -> Text(
                            "○",
                            style = MaterialTheme.typography.titleLarge,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.customLabel.ifBlank { mealTypeLabel(group.type) },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = DarkOnSurface
                        )
                        if (group.timeLabel.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    group.timeLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = AccentOrange
                                )
                            }
                        }
                        if (isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "● TERAZ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val kcal = group.totals.kcal.roundToInt()
                    val target = targetKcalPerMeal
                    // Kolor kcal per status: CONSUMED zielony (sukces),
                    // SKIPPED wyciszony (świadoma decyzja), PLANNED pomarańczowy (cel).
                    val kcalColor = when {
                        isConsumed -> SuccessGreen
                        isSkipped -> DarkOnSurfaceVariant
                        else -> AccentOrange
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$kcal",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = kcalColor
                        )
                        if (target > 0) {
                            Text(
                                " / $target kcal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                                ),
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                    Text(
                        if (expanded) "ᐱ" else "ᐯ",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            // Subtitle ZAWSZE widoczne (mockup Macieja):
            //   - z entries → "X produktów · B57 · W105 · T16"
            //   - bez entries + PLANNED → "Planowana · cel X kcal"
            //   - bez entries + SKIPPED → "Pominięto"
            //   - bez entries + CONSUMED (edge) → ukryj
            val hasEntries = group.entries.isNotEmpty()
            val showSubtitle = hasEntries || !isConsumed
            if (showSubtitle) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = DarkOutlineSoft.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                if (hasEntries) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${group.entries.size} ${if (group.entries.size == 1) "produkt" else "produktów"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = DarkOnSurface
                        )
                        Text(
                            "B ${group.totals.protein.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                            ),
                            color = AccentOrange
                        )
                        Text("·", color = DarkOnSurfaceVariant)
                        Text(
                            "W ${group.totals.carbs.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                            ),
                            color = SuccessGreen
                        )
                        Text("·", color = DarkOnSurfaceVariant)
                        Text(
                            "T ${group.totals.fat.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFFFB74D)
                        )
                    }
                } else {
                    // Empty meal — pokaż status: "Planowana · cel X kcal" lub "Pominięto"
                    val subtitleText = if (isSkipped) {
                        "Pominięto"
                    } else if (targetKcalPerMeal > 0) {
                        "Planowana · cel $targetKcalPerMeal kcal"
                    } else {
                        "Planowana"
                    }
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold
                        ),
                        color = DarkOnSurfaceVariant
                    )
                }
            }
            // === EXPANDED: szczegóły + akcje ===
            if (expanded) {
            if (group.entries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                group.entries.forEach { e ->
                    MealEntryRow(
                        e,
                        onDelete = { onDelete(e.entry.id) },
                        onSwap = { onSwap(e) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Akcje w karcie: dodaj produkt + (opcjonalnie) inna opcja AI
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(AccentOrange.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Dodaj produkt",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = AccentOrange
                        )
                    }
                }
                if (alternativesCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .clickable(onClick = onShowAlternatives),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🔁 Inna opcja ($alternativesCount)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = DarkOnSurface
                        )
                    }
                }
                if (hasRecipe) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .clickable(onClick = onShowRecipe),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📝 Przepis",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = DarkOnSurface
                        )
                    }
                }
            }
            } // end if (expanded)
        }
    }
}

@Composable
private fun MealEntryRow(
    m: MealEntryWithMacros,
    onDelete: () -> Unit,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.product.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface,
                maxLines = 1
            )
            Text(
                "${m.entry.grams.roundToInt()} g · " +
                    "B ${m.protein.roundToInt()}g  W ${m.carbs.roundToInt()}g  T ${m.fat.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = DarkOnSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            "${m.kcal.roundToInt()} kcal",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            color = DarkOnSurface
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onSwap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Zamień",
                tint = AccentOrange,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Usuń",
                tint = DarkOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

internal fun mealTypeLabel(t: MealType): String = when (t) {
    MealType.BREAKFAST -> "Śniadanie"
    MealType.LUNCH -> "Obiad"
    MealType.DINNER -> "Kolacja"
    MealType.SNACK -> "Przekąska"
}

// === NOWE KOMPONENTY (v0.99 reorganizacja) ===

// v1.24.30: helpery formatowania kompaktowego (mockup Macieja: "1.3k", "2.6L", "5.2k", "8k").
private fun formatHydrationCompact(ml: Int): String {
    if (ml < 1000) return "$ml"
    val l = ml / 1000.0
    return if (l >= 10) "${l.toInt()}k" else "%.1fk".format(l).replace(",", ".")
}

private fun formatHydrationGoalCompact(ml: Int): String {
    if (ml < 1000) return "${ml}ml"
    val l = ml / 1000.0
    return if (l == l.toInt().toDouble()) "${l.toInt()}L" else "%.1fL".format(l).replace(",", ".")
}

private fun formatStepsCompact(steps: Int): String {
    if (steps < 1000) return "$steps"
    val k = steps / 1000.0
    return when {
        k >= 10 -> "${k.toInt()}k"
        k == k.toInt().toDouble() -> "${k.toInt()}k"  // całkowite bez '.0' (mockup: '8k')
        else -> "%.1fk".format(k).replace(",", ".")
    }
}

/**
 * 3 mini kafelki w jednym wierszu: Woda / Kroki / Regeneracja.
 * Compact format — minimum miejsca, max info.
 */
@Composable
private fun MiniTilesRow(
    hydrationToday: Int,
    hydrationGoal: Int,
    onAddHydration: (Int) -> Unit,
    onOpenHydration: () -> Unit,
    stepsToday: Int,
    hcConnected: Boolean,
    onOpenSteps: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Woda
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DarkSurface, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenHydration)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    "💧 WODA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                    ),
                    color = AccentOrange
                )
                // v1.24.30: format kompaktowy "1.3k / 2.6L" wg mockupu.
                Text(
                    "${formatHydrationCompact(hydrationToday)} / ${formatHydrationGoalCompact(hydrationGoal)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    ),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(250, 500).forEach { ml ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                                .clickable { onAddHydration(ml) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+$ml",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                                ),
                                color = AccentOrange
                            )
                        }
                    }
                }
            }
        }
        // Kroki
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DarkSurface, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSteps)
                .padding(8.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🚶 KROKI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                        ),
                        color = AccentOrange
                    )
                    if (hcConnected) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "🔗",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = AccentOrange
                        )
                    }
                }
                // v1.24.30: format kompaktowy "5.2k / 8k" wg mockupu.
                val stepsGoal = 8000
                Text(
                    if (stepsToday > 0) "${formatStepsCompact(stepsToday)} / ${formatStepsCompact(stepsGoal)}"
                    else "— / ${formatStepsCompact(stepsGoal)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    ),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                        .clickable(onClick = onOpenSteps),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Wpisz",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        ),
                        color = AccentOrange
                    )
                }
            }
        }
        // Regeneracja
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DarkSurface, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenRecovery)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    "🩺 REGEN.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                    ),
                    color = AccentOrange
                )
                Text(
                    "Oceń dziś",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold, fontSize = 11.sp
                    ),
                    color = DarkOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sen-Stres",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

/**
 * Kompaktowy ribbon "Faza diety" — single row "Brak fazy → SPRAWDŹ" lub "↘️ CUT · 35 dni → SPRAWDŹ".
 */
@Composable
private fun PhaseRibbon(
    currentPhase: pl.filebit.gymtracker.data.entity.DietPhase?,
    onCheck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheck)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPhase == null) {
            Text(
                "🎯 Brak aktywnej fazy",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else {
            val emoji = when (currentPhase.type) {
                pl.filebit.gymtracker.data.entity.DietPhaseType.CUT -> "↘️"
                pl.filebit.gymtracker.data.entity.DietPhaseType.MAINTENANCE -> "⏸"
                pl.filebit.gymtracker.data.entity.DietPhaseType.BULK -> "↗️"
                pl.filebit.gymtracker.data.entity.DietPhaseType.REFEED_DAY -> "🍝"
                pl.filebit.gymtracker.data.entity.DietPhaseType.DIET_BREAK -> "🛑"
            }
            Text(
                "$emoji ${currentPhase.type.name} · ${currentPhase.durationDays()} dni",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "SPRAWDŹ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp
            ),
            color = AccentOrange
        )
    }
}

/**
 * Header sekcji "Posiłki" + wiersz 3 szerokich tile narzędzi pod tytułem (mockup Macieja):
 *  - 🚨 Awaryjny (czerwony outline)
 *  - 🔍 Skaner
 *  - 📷 Foto AI
 */
@Composable
private fun PosilkiHeader(
    onEmergency: () -> Unit,
    onScanner: () -> Unit,
    onFotoAi: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Posiłki",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PosilkiToolTile(
                emoji = "🚨",
                label = "Awaryjny",
                emphasized = true,
                onClick = onEmergency,
                modifier = Modifier.weight(1f)
            )
            PosilkiToolTile(
                emoji = "🔍",
                label = "Skaner",
                emphasized = false,
                onClick = onScanner,
                modifier = Modifier.weight(1f)
            )
            PosilkiToolTile(
                emoji = "📷",
                label = "Foto AI",
                emphasized = false,
                onClick = onFotoAi,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PosilkiToolTile(
    emoji: String,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emergencyRed = Color(0xFFE53935)
    val bgColor = if (emphasized) emergencyRed.copy(alpha = 0.10f) else DarkSurface
    val borderColor = if (emphasized) emergencyRed.copy(alpha = 0.55f) else DarkOutlineSoft
    val textColor = if (emphasized) emergencyRed else DarkOnSurface
    Box(
        modifier = modifier
            .height(52.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = textColor
            )
        }
    }
}

/**
 * Collapsible sekcja "Narzędzia (6)" z grid 2x3 — Raport / Sprawdź / Historia / Preferencje / Zakupy / Meal prep.
 */
@Composable
private fun NarzedziaSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    onRaport: () -> Unit,
    onSprawdzKorekte: () -> Unit,
    onHistoria: () -> Unit,
    onPreferencje: () -> Unit,
    onZakupy: () -> Unit,
    onMealPrep: () -> Unit,
    onRecipes: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkOutlineSoft),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚙ Narzędzia",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkOnSurface
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(AccentOrange.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "7",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                        ),
                        color = AccentOrange
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "ᐱ" else "ᐯ",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkOnSurfaceVariant
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("📊", "Raport", Modifier.weight(1f), onRaport)
                        ToolButton("🔍", "Sprawdź korektę", Modifier.weight(1f), onSprawdzKorekte)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("📋", "Historia zmian", Modifier.weight(1f), onHistoria)
                        ToolButton("⭐", "Preferencje", Modifier.weight(1f), onPreferencje)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("🛒", "Zakupy", Modifier.weight(1f), onZakupy)
                        ToolButton("🍱", "Meal prep", Modifier.weight(1f), onMealPrep)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("📖", "Przepisy (79)", Modifier.weight(1f), onRecipes)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DarkOnSurface
            )
        }
    }
}

/** Sprawdza czy dany slot (godzina HH:MM) jest "TERAZ" — w oknie [-30min, +90min] od aktualnej godziny. */
internal fun isCurrentSlot(timeLabel: String): Boolean {
    if (timeLabel.isBlank()) return false
    val parts = timeLabel.split(":")
    val slotHour = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val slotMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val cal = java.util.Calendar.getInstance()
    val nowHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val nowMinute = cal.get(java.util.Calendar.MINUTE)
    val slotMinutes = slotHour * 60 + slotMinute
    val nowMinutes = nowHour * 60 + nowMinute
    val diff = nowMinutes - slotMinutes
    return diff in -30..90
}

/**
 * v1.24.6: alert wahań kcal (cheat day + niedojadanie w 7 dniach).
 *
 * Pomarańczowa karta na DietScreen (przed sekcjami posiłków).
 * Pokazuje konkretne daty + procent celu + edukacyjny komunikat.
 * X w prawym górnym rogu — dismiss na 7 dni.
 */
@Composable
private fun DietVolatilityCard(
    report: pl.filebit.gymtracker.data.repository.DietVolatilityReport,
    onDismiss: () -> Unit
) {
    val color = pl.filebit.gymtracker.ui.theme.AccentOrange
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "MOCNE WAHANIA KCAL",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = color
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij",
                        tint = pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                report.toUserMessage(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = pl.filebit.gymtracker.ui.theme.DarkOnSurface,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
