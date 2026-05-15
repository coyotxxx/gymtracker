package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.data.repository.DismissedCardsPrefs

/**
 * v1.27 — FAZA 5.2 + 5.5 — flow aplikacji planu AI i interakcje "banalne".
 *
 * 5.2: symulowana odpowiedź AI z planem → extract → validate → apply → start.
 * 5.5: reprezentatywne drobne interakcje (dismiss kart, ulubione, toggle).
 */
class InteractionFlowTest : TestHarness() {

    @Test
    fun `flow 5_2 - plan z odpowiedzi AI zastosowany i uruchomiony`() = runBlocking {
        loadScenario("smoke")
        val kit = ViewModelKit(db, context)
        val names = db.exerciseDao().getAll().take(3).map { it.name }

        // symulowana odpowiedź AI — blok ```json``` jak z prawdziwego trenera
        val aiText = """
            Oto Twój plan treningowy:
            ```json
            {
              "name": "Plan AI Test",
              "description": "Wygenerowany przez trenera AI",
              "daysOfWeek": [1, 3],
              "exercises": [
                {"dayOfWeek": 1, "exerciseName": "${names[0]}",
                 "sets": [{"reps": 8, "weightKg": 80.0, "restSec": 120}]},
                {"dayOfWeek": 1, "exerciseName": "${names[1]}",
                 "sets": [{"reps": 10, "weightKg": 40.0, "restSec": 90}]},
                {"dayOfWeek": 3, "exerciseName": "${names[2]}",
                 "sets": [{"reps": 5, "weightKg": 100.0, "restSec": 180}]}
              ]
            }
            ```
        """.trimIndent()

        val tr = TraceReport("flow-plan-ai")

        // KROK 1 — wyciągnij propozycję z tekstu AI
        val proposal = kit.aiPlanApplier.extractProposal(aiText)!!
        tr.section("KROK 1 — extract")
            .kv("nazwa planu", proposal.name)
            .kv("ćwiczeń w propozycji", proposal.totalExercises.toString())
        assertEquals("3 ćwiczenia z odpowiedzi AI", 3, proposal.totalExercises)

        // KROK 2 — walidacja względem biblioteki
        val validation = kit.aiPlanApplier.validateProposal(proposal)
        tr.section("KROK 2 — validate")
            .verdict("wszystkie ćwiczenia w bibliotece",
                validation.isFullyValid.toString(),
                "dopasowane ${validation.matchedCount}/${validation.totalExercises}")
        assertTrue("ćwiczenia z biblioteki — propozycja w pełni poprawna",
            validation.isFullyValid)

        // KROK 3 — zastosuj plan
        val applyResult = kit.aiPlanApplier.applyProposal(proposal)
        val planId = applyResult.getOrThrow()
        val plan = kit.planRepo.getPlan(planId)!!
        tr.section("KROK 3 — apply")
            .kv("utworzony plan", "id=$planId, ${plan.name}")
        assertEquals("plan zapisany pod nazwą z AI", "Plan AI Test", plan.name)

        // KROK 4 — rozpocznij trening z planu
        val workout = kit.workoutRepo.startOrResume(fromPlanId = planId, fromDayOfWeek = 1)
        tr.section("KROK 4 — start z planu")
            .verdict("trening powiązany z planem",
                "fromPlanId=${workout.fromPlanId}", "")
            .emit()
        assertEquals("trening startuje z zaaplikowanego planu",
            planId, workout.fromPlanId)
    }

    @Test
    fun `flow 5_5 - dismiss karty Home`() {
        val prefs = DismissedCardsPrefs(context)
        prefs.resetAll()
        assertFalse("karta na starcie widoczna",
            prefs.isDismissedToday(DismissedCardsPrefs.CardKeys.PHASE))

        // user X-uje kartę
        prefs.dismissToday(DismissedCardsPrefs.CardKeys.PHASE)
        assertTrue("po dismiss karta ukryta dziś",
            prefs.isDismissedToday(DismissedCardsPrefs.CardKeys.PHASE))
        assertFalse("dismiss jednej karty nie ukrywa innych",
            prefs.isDismissedToday(DismissedCardsPrefs.CardKeys.LOAD))

        prefs.resetAll()
        assertFalse("resetAll przywraca kartę",
            prefs.isDismissedToday(DismissedCardsPrefs.CardKeys.PHASE))
    }

    @Test
    fun `flow 5_5 - oznaczanie cwiczenia jako ulubione`() = runBlocking {
        loadScenario("smoke")
        // ćwiczenie które jeszcze NIE jest ulubione (seed ma domyślne ulubione)
        val exercise = db.exerciseDao().getAll().first { !it.isFavorite }
        val before = db.exerciseDao().getFavorites().size

        db.exerciseDao().setFavorite(exercise.id, true)
        val afterAdd = db.exerciseDao().getFavorites()
        assertEquals("liczba ulubionych +1", before + 1, afterAdd.size)
        assertTrue("ćwiczenie jest na liście ulubionych",
            afterAdd.any { it.id == exercise.id })

        db.exerciseDao().setFavorite(exercise.id, false)
        val afterRemove = db.exerciseDao().getFavorites()
        assertEquals("liczba ulubionych wraca do baseline", before, afterRemove.size)
        assertFalse("ćwiczenie usunięte z ulubionych",
            afterRemove.any { it.id == exercise.id })
    }

    @Test
    fun `flow 5_5 - toggle logowania AI`() {
        val prefs = AiPreferences(context)
        assertFalse("logowanie AI domyślnie wyłączone (prywatność)",
            prefs.isLoggingEnabled())

        prefs.setLoggingEnabled(true)
        assertTrue("po włączeniu logowanie aktywne", prefs.isLoggingEnabled())

        prefs.setLoggingEnabled(false)
        assertFalse("po wyłączeniu logowanie nieaktywne", prefs.isLoggingEnabled())
    }
}
