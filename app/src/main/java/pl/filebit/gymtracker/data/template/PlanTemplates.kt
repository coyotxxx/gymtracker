package pl.filebit.gymtracker.data.template

/**
 * Predefiniowane szablony planów. Po wyborze user dostaje plan w PlanEdit do dopracowania.
 * Nazwy ćwiczeń muszą zgadzać się z bazą (assets/exercises.json) — wyszukiwane po nazwie.
 */
data class PlanTemplate(
    val id: String,
    val name: String,
    val description: String,
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

object PlanTemplates {

    val pushPullLegs = PlanTemplate(
        id = "ppl",
        name = "Push / Pull / Legs (6×/tydzień)",
        description = "Klasyczny 6-dniowy split: Push (klatka/barki/triceps), Pull (plecy/biceps), Legs (nogi). Dla średnio-zaawansowanych.",
        days = listOf(
            TemplateDay(1, listOf( // Pon — Push A
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 6, 120),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 3, 10, 90),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 120),
                TemplateExercise("Wznosy bokiem (lateral raise)", 3, 12, 60),
                TemplateExercise("Wyciskanie francuskie", 3, 10, 90)
            )),
            TemplateDay(2, listOf( // Wt — Pull A
                TemplateExercise("Martwy ciąg klasyczny", 3, 5, 180),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 3, 8, 120),
                TemplateExercise("Face pull", 3, 15, 60),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 90)
            )),
            TemplateDay(3, listOf( // Śr — Legs A
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 6, 180),
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Suwnica (leg press)", 3, 10, 120),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc", 4, 15, 45)
            )),
            TemplateDay(4, listOf( // Czw — Push B
                TemplateExercise("Wyciskanie sztangi - skos dodatni", 4, 8, 120),
                TemplateExercise("Pompki na poręczach (dipy)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 3, 10, 90),
                TemplateExercise("Pec deck", 3, 12, 60),
                TemplateExercise("Prostowanie ramion na wyciągu", 3, 12, 60)
            )),
            TemplateDay(5, listOf( // Pt — Pull B
                TemplateExercise("Podciąganie podchwytem (chin-up)", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 12, 60),
                TemplateExercise("Szrugsy ze sztangielkami", 3, 12, 60),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60)
            )),
            TemplateDay(6, listOf( // Sb — Legs B
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
        days = listOf(
            TemplateDay(1, listOf( // Pon — Upper A
                TemplateExercise("Wyciskanie sztangi leżąc", 4, 6, 120),
                TemplateExercise("Wiosłowanie sztangą", 4, 8, 120),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 8, 120),
                TemplateExercise("Uginanie ramion ze sztangą", 3, 10, 60),
                TemplateExercise("Wyciskanie francuskie", 3, 10, 60)
            )),
            TemplateDay(2, listOf( // Wt — Lower A
                TemplateExercise("Przysiad ze sztangą (back squat)", 4, 6, 180),
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Suwnica (leg press)", 3, 10, 90),
                TemplateExercise("Uginanie nóg leżąc (leg curl)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce stojąc", 4, 15, 45)
            )),
            TemplateDay(4, listOf( // Czw — Upper B
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 4, 8, 120),
                TemplateExercise("Wiosłowanie sztangielką (jednorącz)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek nad głowę", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 10, 90),
                TemplateExercise("Uginanie młotkowe", 3, 12, 60),
                TemplateExercise("Prostowanie ramion na wyciągu", 3, 12, 60)
            )),
            TemplateDay(5, listOf( // Pt — Lower B
                TemplateExercise("Martwy ciąg klasyczny", 3, 5, 180),
                TemplateExercise("Przysiad przedni (front squat)", 3, 8, 120),
                TemplateExercise("Hip thrust", 3, 10, 90),
                TemplateExercise("Prostowanie nóg (leg extension)", 3, 12, 60),
                TemplateExercise("Wspięcia na palce siedząc", 4, 15, 45)
            ))
        )
    )

    val stronglifts5x5 = PlanTemplate(
        id = "stronglifts_5x5",
        name = "Stronglifts 5×5 (3×/tydzień)",
        description = "Klasyczny program siłowy dla początkujących. 3 ćwiczenia × 5 serii × 5 powtórzeń, 2 naprzemienne treningi.",
        days = listOf(
            TemplateDay(1, listOf( // Pon — Workout A
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wiosłowanie sztangą", 5, 5, 120)
            )),
            TemplateDay(3, listOf( // Śr — Workout B
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 5, 5, 180),
                TemplateExercise("Martwy ciąg klasyczny", 1, 5, 240)
            )),
            TemplateDay(5, listOf( // Pt — Workout A znowu
                TemplateExercise("Przysiad ze sztangą (back squat)", 5, 5, 180),
                TemplateExercise("Wyciskanie sztangi leżąc", 5, 5, 180),
                TemplateExercise("Wiosłowanie sztangą", 5, 5, 120)
            ))
        )
    )

    val fullBody3x = PlanTemplate(
        id = "full_body_3x",
        name = "Full Body 3×/tydzień",
        description = "Cały trening pełnego ciała 3 razy w tygodniu. Dla początkujących i wracających po przerwie.",
        days = listOf(
            TemplateDay(1, listOf( // Pon
                TemplateExercise("Przysiad ze sztangą (back squat)", 3, 8, 120),
                TemplateExercise("Wyciskanie sztangi leżąc", 3, 8, 120),
                TemplateExercise("Wiosłowanie sztangą", 3, 8, 120),
                TemplateExercise("Plank (deska)", 3, 60, 30)
            )),
            TemplateDay(3, listOf( // Śr
                TemplateExercise("Martwy ciąg rumuński", 3, 8, 120),
                TemplateExercise("Wyciskanie żołnierskie (OHP)", 3, 8, 90),
                TemplateExercise("Podciąganie nachwytem", 3, 6, 120),
                TemplateExercise("Brzuszki", 3, 15, 30)
            )),
            TemplateDay(5, listOf( // Pt
                TemplateExercise("Suwnica (leg press)", 3, 10, 90),
                TemplateExercise("Wyciskanie sztangielek - skos dodatni", 3, 10, 90),
                TemplateExercise("Ściąganie drążka wyciągu (lat pulldown)", 3, 10, 90),
                TemplateExercise("Wznosy nóg w zwisie", 3, 10, 60)
            ))
        )
    )

    val all: List<PlanTemplate> = listOf(
        fullBody3x,
        stronglifts5x5,
        upperLower,
        pushPullLegs
    )

    fun byId(id: String): PlanTemplate? = all.firstOrNull { it.id == id }
}
