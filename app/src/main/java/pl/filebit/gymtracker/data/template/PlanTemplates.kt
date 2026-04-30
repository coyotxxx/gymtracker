package pl.filebit.gymtracker.data.template

/**
 * Predefiniowane szablony planów. Po wyborze user dostaje plan w PlanEdit do dopracowania.
 * Nazwy ćwiczeń muszą zgadzać się z bazą (assets/exercises.json) — wyszukiwane po nazwie.
 */
data class PlanTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: PlanGoalCategory,
    val days: List<TemplateDay>
)

data class TemplateDay(
    val dayOfWeek: Int,             // 1=Pon..7=Nd
    val exercises: List<TemplateExercise>
)

data class TemplateExercise(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int? = null
)

enum class PlanGoalCategory(val labelPl: String, val emoji: String) {
    BEGINNER("Początkujący", "🌱"),
    HYPERTROPHY("Masa / Hipertrofia", "💪"),
    STRENGTH("Siła", "🏋️"),
    POWERLIFTING("Trójbój / Powerlifting", "🦾"),
    ATHLETIC_CUT("Redukcja / Sport", "⚡"),
    GLUTE_FOCUS("Pośladki / Lower-body", "🍑")
}

object PlanTemplates {

    // ========== POCZĄTKUJĄCY ==========

    val fullBody3x = PlanTemplate(
        id = "full_body_3x",
        name = "Full Body 3×/tydzień",
        description = "Cały trening pełnego ciała 3 razy w tygodniu. Dla początkujących i wracających po przerwie.",
        category = PlanGoalCategory.BEGINNER,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 3, 8, 120),
                TemplateExercise("Wyciskanie sztangi leżąc", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 3, 8, 120),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 6, 120),
                TemplateExercise("Brzuszki", 3, 15, 30)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Suwnica (leg press)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 10, 90),
                TemplateExercise("Wznosy nóg w zwisie", 3, 10, 60)
            ))
        )
    )

    val beginnerBodyweight = PlanTemplate(
        id = "beginner_bw",
        name = "Bodyweight 3×/tydzień",
        description = "Bez sprzętu — wszystko własną masą ciała. Idealne na początek lub gdy nie ma siłowni.",
        category = PlanGoalCategory.BEGINNER,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Pompki", 3, 10, 60),
                TemplateExercise("Australian pull-up (inverted row)", 3, 8, 90),
                TemplateExercise("Goblet squat", 3, 12, 60),
                TemplateExercise("Plank (deska)", 3, 45, 30)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Pompki diamentowe", 3, 8, 60),
                TemplateExercise("Podciąganie nachwytem", 3, 5, 120),
                TemplateExercise("Wykrok kroczący (walking lunge)", 3, 12, 60),
                TemplateExercise("Bird dog", 3, 10, 30)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Pompki na poręczach (dipy)", 3, 8, 90),
                TemplateExercise("Australian pull-up (inverted row)", 3, 10, 60),
                TemplateExercise("Bułgarski przysiad", 3, 10, 60),
                TemplateExercise("Hollow body hold", 3, 30, 30)
            ))
        )
    )

    // ========== HIPERTROFIA / MASA ==========

    val pushPullLegs = PlanTemplate(
        id = "ppl",
        name = "Push / Pull / Legs (6×/tydzień)",
        description = "Klasyczny 6-dniowy split: Push (klatka/barki/triceps), Pull (plecy/biceps), Legs (nogi). Dla średnio-zaawansowanych.",
        category = PlanGoalCategory.HYPERTROPHY,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 6, 120),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 3, 10, 90),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 120),
                TemplateExercise("Wznosy bokiem (lateral raise)", 3, 12, 60),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 3, 10, 90)
            )),
            TemplateDay(2, listOf(
                TemplateExercise("Martwy ciąg klasyczny", 3, 5, 180),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 3, 8, 120),
                TemplateExercise("Face pull", 3, 15, 60),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 90)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 6, 180),
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Suwnica (leg press)", 3, 10, 120),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 4, 15, 45)
            )),
            TemplateDay(4, listOf(
                TemplateExercise("Wyciskanie sztangi - skos dodatni", 4, 8, 120),
                TemplateExercise("Pompki na poręczach (dipy)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 3, 10, 90),
                TemplateExercise("Pec deck (rozpiętki maszyną)", 3, 12, 60),
                TemplateExercise("Prostowanie ramion na wyciągu drążkiem", 3, 12, 60)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Podciąganie podchwytem (chin-up)", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 12, 60),
                TemplateExercise("Szrugsy ze sztangielkami", 3, 12, 60),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60)
            )),
            TemplateDay(6, listOf(
                TemplateExercise("Przysiad przedni (front squat)", 3, 6, 180),
                TemplateExercise("Hip thrust", 3, 10, 120),
                TemplateExercise("Wykrok ze sztangielkami", 3, 10, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce siedząc", 4, 15, 45)
            ))
        )
    )

    val upperLower = PlanTemplate(
        id = "upper_lower",
        name = "Upper / Lower (4×/tydzień)",
        description = "Górna / dolna część ciała na zmianę. Dobry kompromis między częstotliwością a regeneracją.",
        category = PlanGoalCategory.HYPERTROPHY,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 6, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 60),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 3, 10, 60)
            )),
            TemplateDay(2, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 6, 180),
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Suwnica (leg press)", 3, 10, 90),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 4, 15, 45)
            )),
            TemplateDay(4, listOf(
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 4, 8, 120),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 10, 90),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60),
                TemplateExercise("Prostowanie ramion na wyciągu drążkiem", 3, 12, 60)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Martwy ciąg klasyczny", 3, 5, 180),
                TemplateExercise("Przysiad przedni (front squat)", 3, 8, 120),
                TemplateExercise("Hip thrust", 3, 10, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce siedząc", 4, 15, 45)
            ))
        )
    )

    val broSplit = PlanTemplate(
        id = "bro_split",
        name = "Bro Split (5×/tydzień, partia dziennie)",
        description = "Klasyk siłowni: poniedziałek klatka, wtorek plecy itd. Mnóstwo objętości na partię, dłuższa regeneracja.",
        category = PlanGoalCategory.HYPERTROPHY,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Klatka
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 8, 120),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 4, 10, 90),
                TemplateExercise("Pompki na poręczach (dipy)", 3, 10, 90),
                TemplateExercise("Pec deck (rozpiętki maszyną)", 3, 12, 60),
                TemplateExercise("Rozpiętki na bramie - środkowe", 3, 15, 60)
            )),
            TemplateDay(2, listOf( // Wt — Plecy
                TemplateExercise("Martwy ciąg klasyczny", 3, 5, 180),
                TemplateExercise("Podciąganie nachwytem", 4, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 12, 60),
                TemplateExercise("Wiosłowanie na wyciągu siedząc", 3, 12, 60),
                TemplateExercise("Szrugsy ze sztangielkami", 3, 15, 45)
            )),
            TemplateDay(3, listOf( // Śr — Nogi
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 8, 180),
                TemplateExercise("Suwnica (leg press)", 4, 12, 120),
                TemplateExercise("Wykrok kroczący (walking lunge)", 3, 12, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 15, 60),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            )),
            TemplateDay(4, listOf( // Czw — Barki
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 4, 8, 120),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 4, 10, 90),
                TemplateExercise("Wznosy bokiem (lateral raise)", 4, 12, 60),
                TemplateExercise("Odwrotne rozpiętki (rear delt fly)", 3, 15, 60),
                TemplateExercise("Face pull", 3, 15, 60)
            )),
            TemplateDay(5, listOf( // Pt — Ręce
                TemplateExercise("Uginanie ramion ze sztangą", 4, 8, 90),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 4, 8, 90),
                TemplateExercise("Uginanie na modlitewniku ze sztangą", 3, 10, 60),
                TemplateExercise("Skull crusher EZ", 3, 10, 60),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60),
                TemplateExercise("Prostowanie ramion na wyciągu drążkiem", 3, 12, 60)
            ))
        )
    )

    val germanVolume = PlanTemplate(
        id = "gvt",
        name = "German Volume Training (10×10)",
        description = "Klasyczny GVT: 10 serii × 10 powtórzeń na ćwiczenie główne. Brutalna objętość pod masę. 4×/tyg.",
        category = PlanGoalCategory.HYPERTROPHY,
        days = listOf(
            TemplateDay(1, listOf( // Klatka + Plecy
                TemplateExercise("Wyciskanie sztangi leżąc", 10, 10, 90),
                TemplateExercise("Wiosłowanie sztangą", 10, 10, 90),
                TemplateExercise("Pec deck (rozpiętki maszyną)", 3, 12, 60),
                TemplateExercise("Wiosłowanie na wyciągu siedząc", 3, 12, 60)
            )),
            TemplateDay(2, listOf( // Nogi + Brzuch
                TemplateExercise("Przysiad ze sztangą (back squat)", 10, 10, 90),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 10, 10, 90),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(4, listOf( // Barki + Ręce
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 10, 10, 90),
                TemplateExercise("Uginanie ramion ze sztangą", 10, 10, 90),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 10, 10, 90),
                TemplateExercise("Wznosy bokiem (lateral raise)", 3, 15, 45)
            )),
            TemplateDay(5, listOf( // Klatka skos + Plecy szerokość
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 10, 10, 90),
                TemplateExercise("Podciąganie nachwytem", 10, 10, 90),
                TemplateExercise("Rozpiętki sztangielkami", 3, 12, 60),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 12, 60)
            ))
        )
    )

    val arnoldSplit = PlanTemplate(
        id = "arnold_split",
        name = "Arnold Split (6×, klatka+plecy razem)",
        description = "Wzorowane na treningu Schwarzeneggera: klatka+plecy, barki+ręce, nogi — każde 2× w tygodniu.",
        category = PlanGoalCategory.HYPERTROPHY,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Klatka + Plecy A
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 3, 10, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Pompki na poręczach (dipy)", 3, 10, 90)
            )),
            TemplateDay(2, listOf( // Wt — Barki + Ręce A
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 4, 8, 120),
                TemplateExercise("Wznosy bokiem (lateral raise)", 4, 12, 60),
                TemplateExercise("Uginanie ramion ze sztangą", 4, 10, 90),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 4, 10, 90),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60)
            )),
            TemplateDay(3, listOf( // Śr — Nogi A
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 8, 180),
                TemplateExercise("Suwnica (leg press)", 4, 12, 120),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 4, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            )),
            TemplateDay(4, listOf( // Czw — Klatka + Plecy B
                TemplateExercise("Wyciskanie sztangi - skos dodatni", 4, 10, 120),
                TemplateExercise("Wiosłowanie T-bar", 4, 10, 120),
                TemplateExercise("Pec deck (rozpiętki maszyną)", 3, 12, 60),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 12, 60),
                TemplateExercise("Pull-over sztangielką", 3, 12, 60)
            )),
            TemplateDay(5, listOf( // Pt — Barki + Ręce B
                TemplateExercise("Wyciskanie sztangielek nad głowę", 4, 10, 90),
                TemplateExercise("Reverse pec deck", 4, 12, 60),
                TemplateExercise("Uginanie na modlitewniku ze sztangą", 3, 10, 90),
                TemplateExercise("Skull crusher EZ", 3, 10, 90),
                TemplateExercise("Uginanie na wyciągu drążkiem prostym", 3, 12, 60)
            )),
            TemplateDay(6, listOf( // Sb — Nogi B
                TemplateExercise("Martwy ciąg rumuński", 4, 8, 150),
                TemplateExercise("Hack squat (maszyna hack)", 4, 10, 120),
                TemplateExercise("Wykrok ze sztangielkami", 3, 10, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 15, 60),
                TemplateExercise("Wspięcia na palce siedząc", 5, 15, 45)
            ))
        )
    )

    // ========== SIŁA ==========

    val stronglifts5x5 = PlanTemplate(
        id = "stronglifts_5x5",
        name = "Stronglifts 5×5 (3×/tydzień)",
        description = "Klasyczny program siłowy dla początkujących. 5 serii × 5 powtórzeń, 2 naprzemienne treningi.",
        category = PlanGoalCategory.STRENGTH,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wiosłowanie sztangą", 5, 5, 120)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 5, 5, 180),
                TemplateExercise("Martwy ciąg klasyczny", 1, 5, 240)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wiosłowanie sztangą", 5, 5, 120)
            ))
        )
    )

    val greyskullLP = PlanTemplate(
        id = "greyskull_lp",
        name = "Greyskull LP (3×/tydzień)",
        description = "Liniowa progresja dla wczesnych etapów + dodatki na ramiona. AMRAP w ostatniej serii każdego ćwiczenia.",
        category = PlanGoalCategory.STRENGTH,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Wyciskanie sztangi leżąc", 3, 5, 180),
                TemplateExercise("Przysiad ze sztangą (back squat)", 3, 5, 180),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 8, 90),
                TemplateExercise("Prostowanie ramion na wyciągu drążkiem", 3, 10, 60)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 5, 180),
                TemplateExercise("Martwy ciąg klasyczny", 1, 5, 240),
                TemplateExercise("Podciąganie nachwytem", 3, 6, 120)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Wyciskanie sztangi leżąc", 3, 5, 180),
                TemplateExercise("Przysiad ze sztangą (back squat)", 3, 5, 180),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 8, 90),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 3, 10, 90)
            ))
        )
    )

    val fiveThreeOneBBB = PlanTemplate(
        id = "531_bbb",
        name = "5/3/1 Boring But Big (4×/tydzień)",
        description = "Wendlerski cykl siłowy + 5×10 jako BBB na masę. 4 dni — 1 wielki bój dziennie.",
        category = PlanGoalCategory.STRENGTH,
        days = listOf(
            TemplateDay(1, listOf( // Pon — OHP
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 5, 10, 90),
                TemplateExercise("Podciąganie nachwytem", 5, 8, 90)
            )),
            TemplateDay(2, listOf( // Wt — Deadlift
                TemplateExercise("Martwy ciąg klasyczny", 5, 5, 240),
                TemplateExercise("Martwy ciąg rumuński", 5, 10, 120),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(4, listOf( // Czw — Bench
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangielek leżąc", 5, 10, 90),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 5, 10, 90)
            )),
            TemplateDay(5, listOf( // Pt — Squat
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Suwnica (leg press)", 5, 10, 120),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            ))
        )
    )

    // ========== POWERLIFTING ==========

    val powerbuilding4 = PlanTemplate(
        id = "powerbuilding_4",
        name = "Powerbuilding 4× (siła + masa)",
        description = "Pierwsze ćwiczenie siłowe w stylu 5×5, dalej hipertrofia (4×8-12). Połączenie powerliftingu i bodybuildingu.",
        category = PlanGoalCategory.POWERLIFTING,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Bench focus
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 4, 10, 90),
                TemplateExercise("Pec deck (rozpiętki maszyną)", 3, 12, 60),
                TemplateExercise("Wyciskanie francuskie ze sztangą", 4, 10, 90),
                TemplateExercise("Wznosy bokiem (lateral raise)", 3, 15, 60)
            )),
            TemplateDay(2, listOf( // Wt — Squat focus
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Suwnica (leg press)", 4, 10, 120),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 4, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            )),
            TemplateDay(4, listOf( // Czw — Deadlift focus
                TemplateExercise("Martwy ciąg klasyczny", 5, 5, 240),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Podciąganie nachwytem", 4, 8, 120),
                TemplateExercise("Uginanie ramion ze sztangą", 4, 10, 90),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60)
            )),
            TemplateDay(5, listOf( // Pt — OHP focus
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 4, 10, 90),
                TemplateExercise("Wznosy bokiem (lateral raise)", 4, 12, 60),
                TemplateExercise("Reverse pec deck", 3, 15, 60),
                TemplateExercise("Face pull", 3, 15, 60)
            ))
        )
    )

    val sheikoBeginner = PlanTemplate(
        id = "sheiko_beg",
        name = "Sheiko Beginner (3×/tydzień)",
        description = "Rosyjska szkoła trójboju. Wysoka tonaż, średnie ciężary (60-75% 1RM), wiele serii — przygotowanie do mocnej siły.",
        category = PlanGoalCategory.POWERLIFTING,
        days = listOf(
            TemplateDay(1, listOf(
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 5, 180),
                TemplateExercise("Wyciskanie sztangi - skos dodatni", 4, 6, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120)
            )),
            TemplateDay(3, listOf(
                TemplateExercise("Martwy ciąg klasyczny", 4, 5, 240),
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 5, 180),
                TemplateExercise("Pause squat (przysiad z pauzą)", 4, 5, 180),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(5, listOf(
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Martwy ciąg rumuński", 4, 6, 150),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120)
            ))
        )
    )

    // ========== REDUKCJA / SPORT ==========

    val conditioning4 = PlanTemplate(
        id = "conditioning_4",
        name = "Conditioning + Cardio (4×/tydzień)",
        description = "Krótkie odpoczynki, wysokie powt., 2× cardio HIIT — pod redukcję tłuszczu z zachowaniem masy.",
        category = PlanGoalCategory.ATHLETIC_CUT,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Full body strength
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 12, 60),
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 12, 60),
                TemplateExercise("Wiosłowanie sztangą", 4, 12, 60),
                TemplateExercise("Plank (deska)", 3, 45, 30),
                TemplateExercise("Bieżnia interwały (HIIT)", 1, 15, 0)
            )),
            TemplateDay(2, listOf( // Wt — HIIT
                TemplateExercise("Burpees", 5, 15, 45),
                TemplateExercise("Box jumps (skoki na skrzynię)", 5, 10, 60),
                TemplateExercise("Mountain climbers", 5, 30, 30),
                TemplateExercise("Wioślarz (rowing)", 1, 20, 0)
            )),
            TemplateDay(4, listOf( // Czw — Upper + abs
                TemplateExercise("Wyciskanie sztangielek nad głowę", 4, 12, 60),
                TemplateExercise("Podciąganie nachwytem", 4, 8, 90),
                TemplateExercise("Pompki na poręczach (dipy)", 4, 10, 60),
                TemplateExercise("Russian twist", 4, 20, 30),
                TemplateExercise("Skakanka", 1, 10, 0)
            )),
            TemplateDay(5, listOf( // Pt — Lower + cardio
                TemplateExercise("Martwy ciąg rumuński", 4, 10, 90),
                TemplateExercise("Wykrok kroczący (walking lunge)", 4, 15, 60),
                TemplateExercise("Hip thrust", 4, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 20, 30),
                TemplateExercise("Bieżnia (bieg)", 1, 25, 0)
            ))
        )
    )

    val calisthenics4 = PlanTemplate(
        id = "calisthenics_4",
        name = "Calisthenics 4× (bez sprzętu)",
        description = "Trening kalisteniczny — siła własną masą ciała. Drążek + poręcze + podłoga. Idealne na dwór lub w domu.",
        category = PlanGoalCategory.ATHLETIC_CUT,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Push
                TemplateExercise("Pompki", 4, 15, 60),
                TemplateExercise("Pompki na poręczach (dipy)", 4, 10, 90),
                TemplateExercise("Pompki diamentowe", 3, 10, 60),
                TemplateExercise("Pompki plyometryczne", 3, 8, 60),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(2, listOf( // Wt — Pull
                TemplateExercise("Podciąganie nachwytem", 5, 8, 120),
                TemplateExercise("Podciąganie podchwytem (chin-up)", 4, 8, 90),
                TemplateExercise("Australian pull-up (inverted row)", 3, 12, 60),
                TemplateExercise("Hollow body hold", 3, 30, 30)
            )),
            TemplateDay(4, listOf( // Czw — Legs
                TemplateExercise("Bułgarski przysiad", 4, 12, 60),
                TemplateExercise("Wykrok kroczący (walking lunge)", 4, 15, 60),
                TemplateExercise("Pistol squat", 3, 6, 90),
                TemplateExercise("Wspięcia na palce jednonóż", 4, 15, 45),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(5, listOf( // Pt — Core + skills
                TemplateExercise("Wznosy nóg w zwisie", 4, 10, 60),
                TemplateExercise("L-sit", 4, 20, 60),
                TemplateExercise("Dragon flag", 3, 5, 90),
                TemplateExercise("Hollow body hold", 3, 45, 30),
                TemplateExercise("Burpees", 3, 15, 60)
            ))
        )
    )

    // ========== POŚLADKI / LOWER-BODY ==========

    val gluteBuilder = PlanTemplate(
        id = "glute_builder",
        name = "Glute Builder (4×/tydzień)",
        description = "Dwa dni czysto pod pośladki + dwa dni full-body z akcentem na lower. Maksymalna częstotliwość pośladków.",
        category = PlanGoalCategory.GLUTE_FOCUS,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Glutes A (heavy)
                TemplateExercise("Hip thrust", 4, 8, 120),
                TemplateExercise("Martwy ciąg rumuński", 4, 8, 120),
                TemplateExercise("Bułgarski przysiad", 4, 10, 90),
                TemplateExercise("Glute kickback wyciągiem", 4, 12, 60),
                TemplateExercise("Hip abduction (odwodzenie)", 4, 15, 45)
            )),
            TemplateDay(2, listOf( // Wt — Upper
                TemplateExercise("Wyciskanie sztangielek leżąc", 3, 10, 90),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 3, 10, 90),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 60),
                TemplateExercise("Prostowanie ramion na wyciągu drążkiem", 3, 12, 60)
            )),
            TemplateDay(4, listOf( // Czw — Glutes B (volume)
                TemplateExercise("Hip thrust jednonóż", 4, 12, 90),
                TemplateExercise("Cable pull-through (przeciąganie z wyciągu)", 4, 15, 60),
                TemplateExercise("Wykrok kroczący (walking lunge)", 4, 12, 60),
                TemplateExercise("Glute bridge", 4, 15, 60),
                TemplateExercise("Frog pump", 3, 20, 30)
            )),
            TemplateDay(5, listOf( // Pt — Full body lower-bias
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 8, 120),
                TemplateExercise("Suwnica szeroko (sumo leg press)", 4, 12, 90),
                TemplateExercise("Hip abduction (odwodzenie)", 4, 15, 45),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            ))
        )
    )

    val lowerBodyFocus = PlanTemplate(
        id = "lower_focus",
        name = "Lower Body Focus (3 nogi + 1 góra)",
        description = "3 dni dolnej połowy ciała, 1 dzień góry — gdy główny cel to nogi/pośladki, ale nie chcesz całkiem zaniedbać klatki/pleców.",
        category = PlanGoalCategory.GLUTE_FOCUS,
        days = listOf(
            TemplateDay(1, listOf( // Pon — Quad-dom
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 8, 180),
                TemplateExercise("Hack squat (maszyna hack)", 4, 10, 120),
                TemplateExercise("Wykrok ze sztangielkami", 3, 10, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 15, 60),
                TemplateExercise("Wspięcia na palce stojąc (calf raise)", 5, 15, 45)
            )),
            TemplateDay(3, listOf( // Śr — Glute-dom
                TemplateExercise("Hip thrust", 5, 8, 120),
                TemplateExercise("Martwy ciąg rumuński", 4, 10, 120),
                TemplateExercise("Bułgarski przysiad", 3, 10, 90),
                TemplateExercise("Glute kickback wyciągiem", 3, 12, 60),
                TemplateExercise("Cable pull-through (przeciąganie z wyciągu)", 3, 15, 60)
            )),
            TemplateDay(5, listOf( // Pt — Hamstring/posterior
                TemplateExercise("Martwy ciąg klasyczny", 4, 5, 240),
                TemplateExercise("Good morning sztangą", 4, 10, 90),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 4, 12, 60),
                TemplateExercise("Nordic curl (uginanie nordyckie)", 3, 8, 90),
                TemplateExercise("Wspięcia na palce siedząc", 5, 15, 45)
            )),
            TemplateDay(6, listOf( // Sb — Upper body
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 60)
            ))
        )
    )

    val all: List<PlanTemplate> = listOf(
        // Beginner
        fullBody3x,
        beginnerBodyweight,
        // Hypertrophy
        upperLower,
        pushPullLegs,
        broSplit,
        arnoldSplit,
        germanVolume,
        // Strength
        stronglifts5x5,
        greyskullLP,
        fiveThreeOneBBB,
        // Powerlifting
        powerbuilding4,
        sheikoBeginner,
        // Athletic / Cut
        conditioning4,
        calisthenics4,
        // Glute focus
        gluteBuilder,
        lowerBodyFocus
    )

    fun byId(id: String): PlanTemplate? = all.firstOrNull { it.id == id }

    fun byCategory(): Map<PlanGoalCategory, List<PlanTemplate>> =
        all.groupBy { it.category }
}
