package pl.filebit.gymtracker.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.entity.DietAdjustment
import pl.filebit.gymtracker.data.entity.FastingWindow
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.HydrationLog
import pl.filebit.gymtracker.data.entity.HydrationSource
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.RecoveryLog
import pl.filebit.gymtracker.data.entity.UserDietProfile
import pl.filebit.gymtracker.data.repository.DietPreferences
import pl.filebit.gymtracker.data.repository.UserDietProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DietBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val userDietProfile: UserDietProfileDto? = null,
    val customFoodProducts: List<FoodProductDto> = emptyList(),
    val mealEntries: List<MealEntryDto> = emptyList(),
    val fastingWindows: List<FastingWindowDto> = emptyList(),
    val mealFeedback: List<MealFeedbackDto> = emptyList(),
    val adherenceLogs: List<AdherenceLogDto> = emptyList(),
    val dietAdjustments: List<DietAdjustmentDto> = emptyList(),
    val hydrationLogs: List<HydrationLogDto> = emptyList(),
    val recoveryLogs: List<RecoveryLogDto> = emptyList(),
    val dietPreferences: DietPreferencesDto? = null
)

@Serializable
data class UserDietProfileDto(
    val ageYears: Int, val heightCm: Int,
    val activityLevel: String, val avgStepsPerDay: Int,
    val goalType: String, val paceKgPerWeek: Double,
    val customDeficitKcal: Int?,
    val dietPreference: String,
    val allergies: String, val intolerances: String,
    val dislikedFoods: String, val lovedFoods: String,
    val cookingTimePerMealMin: Int,
    val eatsAtWork: Boolean, val hasMicrowaveAtWork: Boolean,
    val mealPrepInterested: Boolean, val weeklyBudgetPln: Int?,
    val medicalConditions: String, val medicalAwareness: Boolean,
    val onboardingCompletedAt: Long?
)

@Serializable
data class FoodProductDto(
    val name: String, val category: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double, val carbsPer100g: Double, val fatPer100g: Double,
    val fiberPer100g: Double = 0.0, val calciumMgPer100g: Double = 0.0,
    val potassiumMgPer100g: Double = 0.0, val sodiumMgPer100g: Double = 0.0,
    val notes: String = ""
)

@Serializable
data class MealEntryDto(
    val dateMs: Long, val mealType: String,
    val productName: String,  // używamy nazwy, nie ID — przenośność
    val grams: Double, val notes: String = "",
    val createdAt: Long
)

@Serializable
data class FastingWindowDto(
    val eatingStartMs: Long, val eatingEndMs: Long?, val plannedEatingHours: Int
)

@Serializable
data class MealFeedbackDto(
    val dishKey: String, val displayName: String,
    val rating: Int, val tags: String = "", val notes: String = "",
    val timesEaten: Int, val createdAt: Long, val updatedAt: Long
)

@Serializable
data class AdherenceLogDto(
    val dateMs: Long,
    val targetKcal: Int, val actualKcal: Int, val kcalAdherencePct: Int,
    val targetProteinG: Int, val actualProteinG: Int, val proteinAdherencePct: Int,
    val targetCarbsG: Int, val actualCarbsG: Int,
    val targetFatG: Int, val actualFatG: Int,
    // v1.24.5: pola wymagane przez AdherenceCalculator (wcześniej brakowały → "Próba: 0 dni")
    val mealsLoggedCount: Int = 0,
    val mealsPlannedCount: Int = 0,
    val wasTrainingPlanned: Boolean = false,
    val wasTrainingDone: Boolean = false,
    val notes: String = ""
)

@Serializable
data class DietAdjustmentDto(
    val dateMs: Long, val oldKcal: Int, val newKcal: Int,
    val actionCode: String, val reason: String,
    val engineExplanation: String, val aiExplanation: String?,
    val confidence: String, val applied: Boolean, val dismissed: Boolean
)

@Serializable
data class HydrationLogDto(
    val dateMs: Long, val ml: Int, val source: String, val createdAt: Long
)

@Serializable
data class RecoveryLogDto(
    val dateMs: Long,
    val sleepHours: Double?, val sleepQuality: Int?,
    val stressLevel: Int?, val hungerLevel: Int?,
    val energyLevel: Int?, val sorenessLevel: Int?,
    val difficultyAdherence: Int?,
    val notes: String, val createdAt: Long
)

@Serializable
data class DietPreferencesDto(
    val mealsPerDay: Int, val eatingWindowHours: Int,
    val windowStartHour: Int, val mealRemindersEnabled: Boolean,
    val autoCheckAdjustments: Boolean,
    val healthConnectSyncEnabled: Boolean = false,
    val customDeficit: Int?, val manualKcal: Int?
)

/**
 * Eksport / import danych dietetycznych do JSON.
 * Niezależny od głównego BackupManager — można eksportować tylko dietę.
 *
 * Polityka:
 *  - import nadpisuje USERa diet profile, custom products (po nazwie),
 *    posiłki/pomiary/zgodność (po dateMs)
 *  - nie usuwa istniejących seed FoodProduct (isCustom=false)
 *  - po imporcie produktów własnych — przeliczamy productName → productId
 */
@Singleton
class DietBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val dietPrefs: DietPreferences,
    private val dietProfileRepo: UserDietProfileRepository
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; isLenient = true }

    suspend fun exportToUri(uri: Uri): Long {
        val backup = collectAll()
        val text = json.encodeToString(backup)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Nie udało się otworzyć pliku do zapisu")
        return text.toByteArray().size.toLong()
    }

    suspend fun importFromUri(uri: Uri): ImportSummary {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: error("Nie udało się otworzyć pliku do odczytu")
        return importFromText(text)
    }

    /** Import z gotowego JSON-text — używane przy auto-detekcji w BackupViewModel. */
    suspend fun importFromText(text: String): ImportSummary {
        val backup = json.decodeFromString<DietBackup>(text)
        return applyAll(backup)
    }

    private suspend fun collectAll(): DietBackup {
        val foodDao = db.foodProductDao()
        val mealDao = db.mealEntryDao()
        val fastingDao = db.fastingWindowDao()
        val feedbackDao = db.mealFeedbackDao()
        val adherenceDao = db.adherenceLogDao()
        val adjustmentDao = db.dietAdjustmentDao()
        val hydrationDao = db.hydrationLogDao()
        val recoveryDao = db.recoveryLogDao()

        val now = System.currentTimeMillis()
        val allProducts = foodDao.getAll()
        val allCustomProducts = allProducts.filter { it.isCustom }
        val productById = allProducts.associateBy { it.id }

        // MealEntries — ostatnie 90 dni dla rozsądnego rozmiaru
        val sinceMs = now - 90L * 24 * 3600 * 1000
        val meals = mealDao.getForDateRange(0L, Long.MAX_VALUE)

        return DietBackup(
            version = 1,
            exportedAt = now,
            userDietProfile = dietProfileRepo.get()?.toDto(),
            customFoodProducts = allCustomProducts.map { it.toDto() },
            mealEntries = meals.mapNotNull { e ->
                val p = productById[e.productId] ?: return@mapNotNull null
                MealEntryDto(
                    dateMs = e.dateMs, mealType = e.mealType.name,
                    productName = p.name, grams = e.grams,
                    notes = e.notes, createdAt = e.createdAt
                )
            },
            fastingWindows = fastingDao.getRecent(10000).map { it.toDto() },
            mealFeedback = feedbackDao.getRecent(10000).map { it.toDto() },
            adherenceLogs = adherenceDao.getRange(sinceMs, Long.MAX_VALUE).map { it.toDto() },
            dietAdjustments = adjustmentDao.getRecent(1000).map { it.toDto() },
            hydrationLogs = hydrationDao.getSince(sinceMs).map { it.toDto() },
            recoveryLogs = recoveryDao.getSince(sinceMs, limit = 10000).map { it.toDto() },
            dietPreferences = dietPrefs.load().let { c ->
                DietPreferencesDto(
                    mealsPerDay = c.mealsPerDay, eatingWindowHours = c.eatingWindowHours,
                    windowStartHour = c.windowStartHour, mealRemindersEnabled = c.mealRemindersEnabled,
                    autoCheckAdjustments = c.autoCheckAdjustments,
                    healthConnectSyncEnabled = c.healthConnectSyncEnabled,
                    customDeficit = c.customDeficit, manualKcal = c.manualKcal
                )
            }
        )
    }

    /**
     * v1.24.18: mapuj `goalType` z backupu na enum DietGoalType. Akceptuje także
     * wartości z `UserProfile.weightGoalType` (CUT/BULK/MAINTAIN/NONE) — zdarza
     * się w scenariuszach testowych i starszych eksportach gdzie wpisywano
     * niespójną nazwę.
     */
    private fun parseGoalType(raw: String): pl.filebit.gymtracker.data.entity.DietGoalType {
        return runCatching {
            pl.filebit.gymtracker.data.entity.DietGoalType.valueOf(raw)
        }.getOrElse {
            when (raw.uppercase()) {
                "CUT", "LOSE_WEIGHT", "DEFICIT" ->
                    pl.filebit.gymtracker.data.entity.DietGoalType.FAT_LOSS
                "BULK", "GAIN_MASS", "SURPLUS" ->
                    pl.filebit.gymtracker.data.entity.DietGoalType.MUSCLE_GAIN
                "MAINTAIN", "NONE", "" ->
                    pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN
                else ->
                    pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN
            }
        }
    }

    private suspend fun applyAll(backup: DietBackup): ImportSummary {
        var customProductsImported = 0
        var mealsImported = 0
        // v1.27.0: posiłki pominięte przy imporcie (brak produktu o tej nazwie)
        // — wcześniej cichy `continue` gubił dane bez ostrzeżenia.
        var mealsSkipped = 0
        val skippedMealProducts = mutableSetOf<String>()
        var feedbackImported = 0
        var hydrationImported = 0
        var recoveryImported = 0

        // 1. UserDietProfile
        backup.userDietProfile?.let { dto ->
            dietProfileRepo.save(UserDietProfile(
                id = 1,
                ageYears = dto.ageYears, heightCm = dto.heightCm,
                activityLevel = runCatching { pl.filebit.gymtracker.data.entity.ActivityLevel.valueOf(dto.activityLevel) }.getOrDefault(pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE),
                avgStepsPerDay = dto.avgStepsPerDay,
                goalType = parseGoalType(dto.goalType),
                paceKgPerWeek = dto.paceKgPerWeek,
                customDeficitKcal = dto.customDeficitKcal,
                dietPreference = runCatching { pl.filebit.gymtracker.data.entity.DietPreference.valueOf(dto.dietPreference) }.getOrDefault(pl.filebit.gymtracker.data.entity.DietPreference.STANDARD),
                allergies = dto.allergies, intolerances = dto.intolerances,
                dislikedFoods = dto.dislikedFoods, lovedFoods = dto.lovedFoods,
                cookingTimePerMealMin = dto.cookingTimePerMealMin,
                eatsAtWork = dto.eatsAtWork, hasMicrowaveAtWork = dto.hasMicrowaveAtWork,
                mealPrepInterested = dto.mealPrepInterested, weeklyBudgetPln = dto.weeklyBudgetPln,
                medicalConditions = dto.medicalConditions, medicalAwareness = dto.medicalAwareness,
                onboardingCompletedAt = dto.onboardingCompletedAt,
                updatedAt = System.currentTimeMillis()
            ))
        }

        // 2. Custom FoodProducts (zachowuje seed jeśli istnieje)
        val foodDao = db.foodProductDao()
        val existingByName = foodDao.getAll().associateBy { it.name.lowercase() }
        for (dto in backup.customFoodProducts) {
            if (existingByName[dto.name.lowercase()] != null) continue
            foodDao.upsert(FoodProduct(
                name = dto.name,
                category = runCatching { FoodCategory.valueOf(dto.category) }.getOrDefault(FoodCategory.OTHER),
                kcalPer100g = dto.kcalPer100g,
                proteinPer100g = dto.proteinPer100g, carbsPer100g = dto.carbsPer100g, fatPer100g = dto.fatPer100g,
                fiberPer100g = dto.fiberPer100g, calciumMgPer100g = dto.calciumMgPer100g,
                potassiumMgPer100g = dto.potassiumMgPer100g, sodiumMgPer100g = dto.sodiumMgPer100g,
                isCustom = true, notes = dto.notes
            ))
            customProductsImported++
        }

        // 3. MealEntries — przelicz productName → productId
        val productsByNameAfter = foodDao.getAll().associateBy { it.name.lowercase() }
        val mealDao = db.mealEntryDao()
        for (dto in backup.mealEntries) {
            val product = productsByNameAfter[dto.productName.lowercase()]
            if (product == null) {
                // produkt nie istnieje w bazie po imporcie — NIE pomijaj po cichu,
                // zlicz i zaraportuj userowi (inaczej posiłki "znikają" bez śladu)
                mealsSkipped++
                skippedMealProducts += dto.productName
                continue
            }
            mealDao.upsert(MealEntry(
                dateMs = dto.dateMs,
                mealType = runCatching { MealType.valueOf(dto.mealType) }.getOrDefault(MealType.LUNCH),
                productId = product.id, grams = dto.grams,
                notes = dto.notes, createdAt = dto.createdAt
            ))
            mealsImported++
        }

        // 4. MealFeedback
        val feedbackDao = db.mealFeedbackDao()
        for (dto in backup.mealFeedback) {
            val existing = feedbackDao.getByKey(dto.dishKey)
            if (existing != null) continue
            feedbackDao.insert(MealFeedback(
                dishKey = dto.dishKey, displayName = dto.displayName,
                rating = dto.rating, tags = dto.tags, notes = dto.notes,
                timesEaten = dto.timesEaten, createdAt = dto.createdAt, updatedAt = dto.updatedAt
            ))
            feedbackImported++
        }

        // 5. HydrationLogs
        val hydrationDao = db.hydrationLogDao()
        for (dto in backup.hydrationLogs) {
            hydrationDao.insert(HydrationLog(
                dateMs = dto.dateMs, ml = dto.ml,
                source = runCatching { HydrationSource.valueOf(dto.source) }.getOrDefault(HydrationSource.WATER),
                createdAt = dto.createdAt
            ))
            hydrationImported++
        }

        // 6. RecoveryLogs
        val recoveryDao = db.recoveryLogDao()
        for (dto in backup.recoveryLogs) {
            recoveryDao.insert(RecoveryLog(
                dateMs = dto.dateMs,
                sleepHours = dto.sleepHours, sleepQuality = dto.sleepQuality,
                stressLevel = dto.stressLevel, hungerLevel = dto.hungerLevel,
                energyLevel = dto.energyLevel, sorenessLevel = dto.sorenessLevel,
                difficultyAdherence = dto.difficultyAdherence,
                notes = dto.notes, createdAt = dto.createdAt
            ))
            recoveryImported++
        }

        // 7. DietPreferences
        backup.dietPreferences?.let { dto ->
            dietPrefs.save(pl.filebit.gymtracker.data.repository.DietConfig(
                mealsPerDay = dto.mealsPerDay,
                eatingWindowHours = dto.eatingWindowHours,
                windowStartHour = dto.windowStartHour,
                mealRemindersEnabled = dto.mealRemindersEnabled,
                autoCheckAdjustments = dto.autoCheckAdjustments,
                healthConnectSyncEnabled = dto.healthConnectSyncEnabled,
                customDeficit = dto.customDeficit,
                manualKcal = dto.manualKcal
            ))
        }

        // v1.24.4: import adherenceLogs / dietAdjustments / fastingWindows (wcześniej pominięte — utrata danych przy eksport/import)
        var adherenceImported = 0
        var adjustmentsImported = 0
        var fastingImported = 0

        // 8. AdherenceLogs
        val adherenceDao = db.adherenceLogDao()
        for (dto in backup.adherenceLogs) {
            adherenceDao.upsert(
                pl.filebit.gymtracker.data.entity.AdherenceLog(
                    dateMs = dto.dateMs,
                    targetKcal = dto.targetKcal,
                    actualKcal = dto.actualKcal,
                    kcalAdherencePct = dto.kcalAdherencePct,
                    targetProteinG = dto.targetProteinG,
                    actualProteinG = dto.actualProteinG,
                    proteinAdherencePct = dto.proteinAdherencePct,
                    targetCarbsG = dto.targetCarbsG,
                    actualCarbsG = dto.actualCarbsG,
                    carbsAdherencePct = if (dto.targetCarbsG > 0) (dto.actualCarbsG * 100 / dto.targetCarbsG) else 0,
                    targetFatG = dto.targetFatG,
                    actualFatG = dto.actualFatG,
                    fatAdherencePct = if (dto.targetFatG > 0) (dto.actualFatG * 100 / dto.targetFatG) else 0,
                    mealsLoggedCount = dto.mealsLoggedCount,
                    mealsPlannedCount = dto.mealsPlannedCount,
                    wasTrainingPlanned = dto.wasTrainingPlanned,
                    wasTrainingDone = dto.wasTrainingDone,
                    notes = dto.notes
                )
            )
            adherenceImported++
        }

        // 9. DietAdjustments
        val adjustmentDao = db.dietAdjustmentDao()
        for (dto in backup.dietAdjustments) {
            adjustmentDao.insert(
                pl.filebit.gymtracker.data.entity.DietAdjustment(
                    dateMs = dto.dateMs,
                    oldKcal = dto.oldKcal,
                    newKcal = dto.newKcal,
                    actionCode = dto.actionCode,
                    reason = dto.reason,
                    engineExplanation = dto.engineExplanation,
                    aiExplanation = dto.aiExplanation,
                    confidence = dto.confidence,
                    applied = dto.applied,
                    dismissed = dto.dismissed
                )
            )
            adjustmentsImported++
        }

        // 10. FastingWindows
        val fastingDao = db.fastingWindowDao()
        for (dto in backup.fastingWindows) {
            fastingDao.upsert(
                pl.filebit.gymtracker.data.entity.FastingWindow(
                    eatingStartMs = dto.eatingStartMs,
                    eatingEndMs = dto.eatingEndMs,
                    plannedEatingHours = dto.plannedEatingHours
                )
            )
            fastingImported++
        }

        return ImportSummary(
            customProductsImported = customProductsImported,
            mealsImported = mealsImported,
            feedbackImported = feedbackImported,
            hydrationImported = hydrationImported,
            recoveryImported = recoveryImported,
            adherenceImported = adherenceImported,
            adjustmentsImported = adjustmentsImported,
            fastingImported = fastingImported,
            mealsSkipped = mealsSkipped,
            skippedProductNames = skippedMealProducts.toList()
        )
    }

    // Extension to DTO mappers
    private fun UserDietProfile.toDto() = UserDietProfileDto(
        ageYears = ageYears, heightCm = heightCm,
        activityLevel = activityLevel.name, avgStepsPerDay = avgStepsPerDay,
        goalType = goalType.name, paceKgPerWeek = paceKgPerWeek,
        customDeficitKcal = customDeficitKcal,
        dietPreference = dietPreference.name,
        allergies = allergies, intolerances = intolerances,
        dislikedFoods = dislikedFoods, lovedFoods = lovedFoods,
        cookingTimePerMealMin = cookingTimePerMealMin,
        eatsAtWork = eatsAtWork, hasMicrowaveAtWork = hasMicrowaveAtWork,
        mealPrepInterested = mealPrepInterested, weeklyBudgetPln = weeklyBudgetPln,
        medicalConditions = medicalConditions, medicalAwareness = medicalAwareness,
        onboardingCompletedAt = onboardingCompletedAt
    )

    private fun FoodProduct.toDto() = FoodProductDto(
        name = name, category = category.name,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g, carbsPer100g = carbsPer100g, fatPer100g = fatPer100g,
        fiberPer100g = fiberPer100g, calciumMgPer100g = calciumMgPer100g,
        potassiumMgPer100g = potassiumMgPer100g, sodiumMgPer100g = sodiumMgPer100g,
        notes = notes
    )

    private fun FastingWindow.toDto() = FastingWindowDto(eatingStartMs, eatingEndMs, plannedEatingHours)

    private fun MealFeedback.toDto() = MealFeedbackDto(
        dishKey = dishKey, displayName = displayName,
        rating = rating, tags = tags, notes = notes,
        timesEaten = timesEaten, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun AdherenceLog.toDto() = AdherenceLogDto(
        dateMs = dateMs,
        targetKcal = targetKcal, actualKcal = actualKcal, kcalAdherencePct = kcalAdherencePct,
        targetProteinG = targetProteinG, actualProteinG = actualProteinG, proteinAdherencePct = proteinAdherencePct,
        targetCarbsG = targetCarbsG, actualCarbsG = actualCarbsG,
        targetFatG = targetFatG, actualFatG = actualFatG,
        mealsLoggedCount = mealsLoggedCount, mealsPlannedCount = mealsPlannedCount,
        wasTrainingPlanned = wasTrainingPlanned, wasTrainingDone = wasTrainingDone,
        notes = notes
    )

    private fun DietAdjustment.toDto() = DietAdjustmentDto(
        dateMs = dateMs, oldKcal = oldKcal, newKcal = newKcal,
        actionCode = actionCode, reason = reason,
        engineExplanation = engineExplanation, aiExplanation = aiExplanation,
        confidence = confidence, applied = applied, dismissed = dismissed
    )

    private fun HydrationLog.toDto() = HydrationLogDto(
        dateMs = dateMs, ml = ml, source = source.name, createdAt = createdAt
    )

    private fun RecoveryLog.toDto() = RecoveryLogDto(
        dateMs = dateMs,
        sleepHours = sleepHours, sleepQuality = sleepQuality,
        stressLevel = stressLevel, hungerLevel = hungerLevel,
        energyLevel = energyLevel, sorenessLevel = sorenessLevel,
        difficultyAdherence = difficultyAdherence,
        notes = notes, createdAt = createdAt
    )
}

data class ImportSummary(
    val customProductsImported: Int,
    val mealsImported: Int,
    val feedbackImported: Int,
    val hydrationImported: Int,
    val recoveryImported: Int,
    // v1.24.4 — wcześniej pomijane (utrata danych)
    val adherenceImported: Int = 0,
    val adjustmentsImported: Int = 0,
    val fastingImported: Int = 0,
    // v1.27.0 — posiłki pominięte bo brak produktu o danej nazwie
    val mealsSkipped: Int = 0,
    val skippedProductNames: List<String> = emptyList()
) {
    fun toUserMessage(): String = buildString {
        append("Zaimportowano: $customProductsImported produktów własnych, $mealsImported posiłków")
        if (feedbackImported > 0) append(", $feedbackImported ocen")
        if (hydrationImported > 0) append(", $hydrationImported wpisów wody")
        if (recoveryImported > 0) append(", $recoveryImported wpisów regeneracji")
        if (adherenceImported > 0) append(", $adherenceImported zapisów adherence")
        if (adjustmentsImported > 0) append(", $adjustmentsImported korekt diety")
        if (fastingImported > 0) append(", $fastingImported okien IF")
        append(".")
        if (mealsSkipped > 0) {
            append("\n\n⚠ Pominięto $mealsSkipped posiłków — w bazie brakuje produktów: ")
            append(skippedProductNames.take(5).joinToString(", "))
            if (skippedProductNames.size > 5) {
                append(" i ${skippedProductNames.size - 5} innych")
            }
            append(". Dodaj te produkty i zaimportuj backup ponownie, aby odzyskać posiłki.")
        }
    }
}
