package pl.filebit.gymtracker.data.repository

/**
 * Definicje wszystkich odznak. Każda ma: kod, kategorię, poziom (Bronze/Silver/Gold/Platinum),
 * tytuł, opis, próg (target) oraz aktualną wartość (currentValue) wyliczoną przez engine.
 *
 * Engine (StatsRepository.unlockedAchievements) podaje aktualne metryki użytkownika, a tu
 * mapujemy je na konkretne odznaki. Persystencję samego momentu odblokowania trzyma DB
 * (UnlockedAchievement) — definicje są stateless.
 */
data class AchievementDef(
    val code: String,
    val emoji: String,
    val category: AchievementCategory,
    val level: AchievementLevel,
    val title: String,
    val description: String,
    val targetValue: Long,
    val currentValue: Long
)

object AchievementDefinitions {
    @Suppress("LongParameterList")
    fun all(
        workoutsCount: Long,
        totalVolume: Long,
        streakBestWeeks: Long,
        distinctExercises: Long,
        musclesTrained: Long,
        customExercises: Long,
        prCount: Long,
        bodyMeasurementsCount: Long,
        achievedGoalsCount: Long,
        bodyweightDelta: Double,
        weightGoalType: String,           // "GAIN_MASS" / "LOSE_WEIGHT" / "MAINTAIN" / "NONE"
        chestDelta: Double,
        armDelta: Double,
        thighDelta: Double,
        waistDrop: Double,                // dodatnie gdy zmalał
        bodyFatDrop: Double,              // dodatnie gdy zmalał
        benchMaxKg: Double,
        squatMaxKg: Double,
        deadliftMaxKg: Double,
        ohpMaxKg: Double,
        bodyweightKg: Double,
        // v1.18.0 — PERIODIZATION
        completedCycles: Long = 0,
        deloadsExecuted: Long = 0,
        phasesCompleted: Long = 0
    ): List<AchievementDef> {
        val list = mutableListOf<AchievementDef>()

        // ===== WYTRWAŁOŚĆ =====
        list += listOf(
            AchievementDef("workouts_5",   "👟", AchievementCategory.CONSISTENCY, AchievementLevel.BRONZE,
                "Pierwsze kroki", "5 ukończonych treningów", 5, workoutsCount),
            AchievementDef("workouts_25",  "💪", AchievementCategory.CONSISTENCY, AchievementLevel.BRONZE,
                "Stała rutyna", "25 ukończonych treningów", 25, workoutsCount),
            AchievementDef("workouts_50",  "🏆", AchievementCategory.CONSISTENCY, AchievementLevel.SILVER,
                "Zawodowiec", "50 treningów", 50, workoutsCount),
            AchievementDef("workouts_100", "👑", AchievementCategory.CONSISTENCY, AchievementLevel.GOLD,
                "Setka", "100 treningów", 100, workoutsCount),
            AchievementDef("workouts_250", "🌟", AchievementCategory.CONSISTENCY, AchievementLevel.GOLD,
                "Wojownik", "250 treningów", 250, workoutsCount),
            AchievementDef("workouts_500", "💎", AchievementCategory.CONSISTENCY, AchievementLevel.PLATINUM,
                "Legenda", "500 treningów", 500, workoutsCount),
            AchievementDef("streak_2",     "🔥", AchievementCategory.CONSISTENCY, AchievementLevel.BRONZE,
                "Dwa tygodnie", "2 tygodnie z rzędu", 2, streakBestWeeks),
            AchievementDef("streak_4",     "🔥🔥", AchievementCategory.CONSISTENCY, AchievementLevel.SILVER,
                "Miesiąc mocy", "4 tygodnie z rzędu", 4, streakBestWeeks),
            AchievementDef("streak_12",    "🌪️", AchievementCategory.CONSISTENCY, AchievementLevel.GOLD,
                "Kwartał", "12 tygodni z rzędu", 12, streakBestWeeks),
            AchievementDef("streak_26",    "⚡", AchievementCategory.CONSISTENCY, AchievementLevel.GOLD,
                "Pół roku", "26 tygodni z rzędu", 26, streakBestWeeks),
            AchievementDef("streak_52",    "🌟", AchievementCategory.CONSISTENCY, AchievementLevel.PLATINUM,
                "Rok mocy", "52 tygodnie z rzędu", 52, streakBestWeeks)
        )

        // ===== OBJĘTOŚĆ =====
        list += listOf(
            AchievementDef("volume_10k",   "🏋️", AchievementCategory.VOLUME, AchievementLevel.BRONZE,
                "Pierwsza dziesiątka", "10 000 kg łącznej objętości", 10_000, totalVolume),
            AchievementDef("volume_50k",   "🏋️‍♂️", AchievementCategory.VOLUME, AchievementLevel.BRONZE,
                "Solidnie", "50 000 kg łącznej objętości", 50_000, totalVolume),
            AchievementDef("volume_100k",  "💯", AchievementCategory.VOLUME, AchievementLevel.SILVER,
                "Stuka", "100 000 kg łącznej objętości", 100_000, totalVolume),
            AchievementDef("volume_500k",  "⚡", AchievementCategory.VOLUME, AchievementLevel.GOLD,
                "Pół megatony", "500 000 kg łącznej objętości", 500_000, totalVolume),
            AchievementDef("volume_1m",    "💎", AchievementCategory.VOLUME, AchievementLevel.PLATINUM,
                "Megatona", "1 000 000 kg łącznej objętości", 1_000_000, totalVolume)
        )

        // ===== EKSPLORACJA =====
        list += listOf(
            AchievementDef("exercises_10",  "🧭", AchievementCategory.EXPLORATION, AchievementLevel.BRONZE,
                "Odkrywca", "10 unikalnych ćwiczeń", 10, distinctExercises),
            AchievementDef("exercises_25",  "🗺️", AchievementCategory.EXPLORATION, AchievementLevel.SILVER,
                "Dziwny smak", "25 unikalnych ćwiczeń", 25, distinctExercises),
            AchievementDef("exercises_50",  "🌍", AchievementCategory.EXPLORATION, AchievementLevel.GOLD,
                "Globtroter", "50 unikalnych ćwiczeń", 50, distinctExercises),
            AchievementDef("exercises_100", "🚀", AchievementCategory.EXPLORATION, AchievementLevel.PLATINUM,
                "Eksplorator", "100 unikalnych ćwiczeń", 100, distinctExercises),
            AchievementDef("muscles_5",     "💪", AchievementCategory.EXPLORATION, AchievementLevel.BRONZE,
                "Nie tylko klatka", "5 różnych grup mięśniowych", 5, musclesTrained),
            AchievementDef("muscles_all",   "🏅", AchievementCategory.EXPLORATION, AchievementLevel.GOLD,
                "Pełen skład", "Wszystkie 10 głównych grup", 10, musclesTrained),
            AchievementDef("custom_first",  "✏️", AchievementCategory.EXPLORATION, AchievementLevel.BRONZE,
                "Własne ćwiczenie", "Dodaj własne ćwiczenie", 1, customExercises)
        )

        // ===== SIŁA — ogólne PR =====
        list += listOf(
            AchievementDef("pr_first", "🥇", AchievementCategory.STRENGTH, AchievementLevel.BRONZE,
                "Pierwszy rekord", "Ustanów PR w jednym ćwiczeniu", 1, prCount),
            AchievementDef("pr_5",     "🏅", AchievementCategory.STRENGTH, AchievementLevel.BRONZE,
                "Łowca rekordów", "PR-y w 5 ćwiczeniach", 5, prCount),
            AchievementDef("pr_15",    "🥈", AchievementCategory.STRENGTH, AchievementLevel.SILVER,
                "Wszechstronny", "PR-y w 15 ćwiczeniach", 15, prCount),
            AchievementDef("pr_30",    "🥇", AchievementCategory.STRENGTH, AchievementLevel.GOLD,
                "Maszyna PR", "PR-y w 30 ćwiczeniach", 30, prCount)
        )

        // ===== SIŁA — boje względem masy ciała =====
        if (bodyweightKg > 30) {
            // Bench: 0.75 / 1.0 / 1.5 × bw
            list += strengthMilestones("bench", "Wyciskanie leżąc", "🛋️", benchMaxKg, bodyweightKg,
                listOf(0.75 to AchievementLevel.BRONZE, 1.0 to AchievementLevel.SILVER,
                    1.25 to AchievementLevel.GOLD, 1.5 to AchievementLevel.PLATINUM))
            list += strengthMilestones("squat", "Przysiad", "🦵", squatMaxKg, bodyweightKg,
                listOf(1.0 to AchievementLevel.BRONZE, 1.5 to AchievementLevel.SILVER,
                    1.75 to AchievementLevel.GOLD, 2.0 to AchievementLevel.PLATINUM))
            list += strengthMilestones("dl", "Martwy ciąg", "💀", deadliftMaxKg, bodyweightKg,
                listOf(1.5 to AchievementLevel.BRONZE, 2.0 to AchievementLevel.SILVER,
                    2.25 to AchievementLevel.GOLD, 2.5 to AchievementLevel.PLATINUM))
            list += strengthMilestones("ohp", "Wyciskanie nad głowę", "🪖", ohpMaxKg, bodyweightKg,
                listOf(0.5 to AchievementLevel.BRONZE, 0.75 to AchievementLevel.SILVER,
                    1.0 to AchievementLevel.GOLD, 1.25 to AchievementLevel.PLATINUM))
        }

        // ===== SYLWETKA =====
        list += listOf(
            AchievementDef("body_first",   "📏", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Pierwszy pomiar", "Zapisz pomiar ciała", 1, bodyMeasurementsCount),
            AchievementDef("body_5",       "📐", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Mierzysz progres", "5 zapisanych pomiarów", 5, bodyMeasurementsCount),
            AchievementDef("body_20",      "📊", AchievementCategory.BODY, AchievementLevel.SILVER,
                "Konsekwentny", "20 pomiarów", 20, bodyMeasurementsCount),
            AchievementDef("body_50",      "🧪", AchievementCategory.BODY, AchievementLevel.GOLD,
                "Naukowiec", "50 pomiarów", 50, bodyMeasurementsCount)
        )

        // Cel masy ciała (gain / lose) — odznaki za faktyczny progres
        when (weightGoalType) {
            "GAIN_MASS" -> {
                val gainKg = (bodyweightDelta * 10).toLong().coerceAtLeast(0)  // *10 = decigrams jako Long
                list += listOf(
                    AchievementDef("mass_2kg",  "💪", AchievementCategory.BODY, AchievementLevel.BRONZE,
                        "Pierwsze 2 kg masy", "Przybierz 2 kg w trakcie celu", 20, gainKg),
                    AchievementDef("mass_5kg",  "🥩", AchievementCategory.BODY, AchievementLevel.SILVER,
                        "Bulk", "Przybierz 5 kg masy", 50, gainKg),
                    AchievementDef("mass_10kg", "🥇", AchievementCategory.BODY, AchievementLevel.GOLD,
                        "Wielki bulk", "Przybierz 10 kg masy", 100, gainKg)
                )
            }
            "LOSE_WEIGHT" -> {
                val lostKg = ((-bodyweightDelta) * 10).toLong().coerceAtLeast(0)
                list += listOf(
                    AchievementDef("cut_2kg",  "🥗", AchievementCategory.BODY, AchievementLevel.BRONZE,
                        "Pierwsze 2 kg", "Zrzuć 2 kg w trakcie redukcji", 20, lostKg),
                    AchievementDef("cut_5kg",  "🔥", AchievementCategory.BODY, AchievementLevel.SILVER,
                        "Cięcie", "Zrzuć 5 kg", 50, lostKg),
                    AchievementDef("cut_10kg", "✂️", AchievementCategory.BODY, AchievementLevel.GOLD,
                        "Wielka redukcja", "Zrzuć 10 kg", 100, lostKg)
                )
            }
        }

        // Obwody — wzrost (mases) / spadek (cut)
        val chestGain = (chestDelta * 10).toLong().coerceAtLeast(0)
        val armGain = (armDelta * 10).toLong().coerceAtLeast(0)
        val thighGain = (thighDelta * 10).toLong().coerceAtLeast(0)
        val waistDropX10 = (waistDrop * 10).toLong().coerceAtLeast(0)
        val bfDropX10 = (bodyFatDrop * 10).toLong().coerceAtLeast(0)

        list += listOf(
            AchievementDef("chest_2",  "🫁", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Klatka rośnie", "+2 cm w obwodzie klatki", 20, chestGain),
            AchievementDef("chest_5",  "🦾", AchievementCategory.BODY, AchievementLevel.SILVER,
                "Klatka jak beczka", "+5 cm w klatce", 50, chestGain),
            AchievementDef("arm_2",    "💪", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Ramiona puchną", "+2 cm w ramieniu", 20, armGain),
            AchievementDef("arm_5",    "🔫", AchievementCategory.BODY, AchievementLevel.SILVER,
                "Działka", "+5 cm w ramieniu", 50, armGain),
            AchievementDef("thigh_3",  "🦵", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Uda na fali", "+3 cm w udzie", 30, thighGain),
            AchievementDef("waist_3",  "📉", AchievementCategory.BODY, AchievementLevel.BRONZE,
                "Mniejszy pas", "−3 cm w pasie", 30, waistDropX10),
            AchievementDef("waist_8",  "⚡", AchievementCategory.BODY, AchievementLevel.SILVER,
                "Wcięcie", "−8 cm w pasie", 80, waistDropX10),
            AchievementDef("bf_2pp",   "🥇", AchievementCategory.BODY, AchievementLevel.SILVER,
                "Niżej tłuszcz", "−2pp body-fat %", 20, bfDropX10),
            AchievementDef("bf_5pp",   "💎", AchievementCategory.BODY, AchievementLevel.GOLD,
                "Definicja", "−5pp body-fat %", 50, bfDropX10)
        )

        // ===== CELE =====
        list += listOf(
            AchievementDef("goal_first", "🎯", AchievementCategory.GOALS, AchievementLevel.BRONZE,
                "Pierwszy cel", "Zrealizuj pierwszy cel", 1, achievedGoalsCount),
            AchievementDef("goal_3",     "🏹", AchievementCategory.GOALS, AchievementLevel.SILVER,
                "Strzelec", "Zrealizuj 3 cele", 3, achievedGoalsCount),
            AchievementDef("goal_10",    "🎖️", AchievementCategory.GOALS, AchievementLevel.GOLD,
                "Mistrz celów", "Zrealizuj 10 celów", 10, achievedGoalsCount)
        )

        // ===== v1.18.0 — PERIODYZACJA =====
        list += listOf(
            AchievementDef("cycle_complete_1", "🔄", AchievementCategory.PERIODIZATION, AchievementLevel.BRONZE,
                "Pierwszy cykl", "Ukończ pierwszy mesocykl", 1, completedCycles),
            AchievementDef("cycle_complete_5", "🎯", AchievementCategory.PERIODIZATION, AchievementLevel.SILVER,
                "Periodyzator", "Ukończ 5 mesocykli", 5, completedCycles),
            AchievementDef("deload_master_5", "🛌", AchievementCategory.PERIODIZATION, AchievementLevel.SILVER,
                "Mistrz regeneracji", "Wykonaj 5 zaplanowanych deloadów", 5, deloadsExecuted),
            AchievementDef("phases_all_4", "🌟", AchievementCategory.PERIODIZATION, AchievementLevel.GOLD,
                "Cztery fazy", "Zalicz 4 różne fazy mesocyklu", 4, phasesCompleted)
        )

        return list
    }

    private fun strengthMilestones(
        prefix: String,
        liftName: String,
        emoji: String,
        currentMaxKg: Double,
        bodyweightKg: Double,
        thresholds: List<Pair<Double, AchievementLevel>>
    ): List<AchievementDef> {
        if (bodyweightKg <= 0) return emptyList()
        return thresholds.map { (mult, lvl) ->
            val targetKg = (bodyweightKg * mult).toLong()
            // Mnożymy currentMaxKg przez 10 dla Long granularności (1 decimal)
            AchievementDef(
                code = "${prefix}_${(mult * 100).toInt()}",
                emoji = emoji,
                category = AchievementCategory.STRENGTH,
                level = lvl,
                title = "$liftName ${"%.2f".format(mult).trimEnd('0').trimEnd('.')}× masa",
                description = "$liftName co najmniej ${targetKg} kg (przy bw ${bodyweightKg.toInt()} kg)",
                targetValue = targetKg,
                currentValue = currentMaxKg.toLong()
            )
        }
    }
}
