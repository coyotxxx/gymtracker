package pl.filebit.gymtracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.Workout

/**
 * v1.11.58 — Pure functions dla nowych sekcji kontekstu AI.
 * Testy bez DI / bez kafki bazy.
 */
class AiContextBuilderHelpersTest {

    private val now = 1715000000000L
    private val msPerDay = 86_400_000L

    private fun bm(daysAgo: Long, weight: Double?) = BodyMeasurement(
        date = now - daysAgo * msPerDay,
        weightKg = weight
    )

    private fun workout(daysAgo: Long, painArea: String? = null, finished: Boolean = true) = Workout(
        startedAt = now - daysAgo * msPerDay,
        finishedAt = if (finished) now - daysAgo * msPerDay + 60 * 60 * 1000L else null,
        painArea = painArea
    )

    // ============== computeBodyInflections ==============

    @Test
    fun `bodyInflections - pusta lista zwraca nulle`() {
        val result = computeBodyInflections(emptyList())
        assertNull(result.startWeightKg)
        assertNull(result.minWeightKg)
        assertNull(result.maxWeightKg)
        assertNull(result.currentWeightKg)
        assertNull(result.totalChangeKg)
    }

    @Test
    fun `bodyInflections - jeden pomiar = wszystkie 4 wartosci to ten sam`() {
        val result = computeBodyInflections(listOf(bm(0, 76.5)))
        assertEquals(76.5, result.startWeightKg!!, 0.001)
        assertEquals(76.5, result.minWeightKg!!, 0.001)
        assertEquals(76.5, result.maxWeightKg!!, 0.001)
        assertEquals(76.5, result.currentWeightKg!!, 0.001)
        assertEquals(0.0, result.totalChangeKg!!, 0.001)
    }

    @Test
    fun `bodyInflections - 4 rozne punkty start min max current`() {
        // Scenariusz testujacy wszystkie 4 wartosci jako rozne:
        // 100 dni temu (start 75) -> 50 dni temu (max 80) -> 20 dni temu (78) -> teraz (current 73, min)
        val result = computeBodyInflections(listOf(
            bm(100, 75.0),
            bm(50, 80.0),
            bm(20, 78.0),
            bm(0, 73.0)
        ))
        assertEquals(75.0, result.startWeightKg!!, 0.001)
        assertEquals(73.0, result.minWeightKg!!, 0.001)
        assertEquals(80.0, result.maxWeightKg!!, 0.001)
        assertEquals(73.0, result.currentWeightKg!!, 0.001)
        assertEquals(-2.0, result.totalChangeKg!!, 0.001)  // 73 - 75 = -2
    }

    @Test
    fun `bodyInflections - ignoruje pomiary bez wagi`() {
        val result = computeBodyInflections(listOf(
            bm(10, null),  // tylko obwody, brak wagi
            bm(5, 75.0),
            bm(0, 76.0)
        ))
        assertEquals(75.0, result.startWeightKg!!, 0.001)
        assertEquals(76.0, result.currentWeightKg!!, 0.001)
    }

    // ============== computePainLog90d ==============

    @Test
    fun `painLog - pusta lista`() {
        val result = computePainLog90d(emptyList(), nowMs = now)
        assertEquals(0, result.totalWorkoutsAnalyzed)
        assertEquals(0, result.totalWorkoutsWithPain)
        assertTrue(result.areas.isEmpty())
    }

    @Test
    fun `painLog - tylko zakonczone treningi sa liczone`() {
        val result = computePainLog90d(listOf(
            workout(daysAgo = 5, painArea = "kolano", finished = true),
            workout(daysAgo = 3, painArea = "bark", finished = false),  // niedokonczony - skip
            workout(daysAgo = 1, finished = true)  // bez bolu
        ), nowMs = now)
        assertEquals(2, result.totalWorkoutsAnalyzed)  // tylko zakonczone
        assertEquals(1, result.totalWorkoutsWithPain)
        assertEquals(1, result.areas.size)
        assertEquals("kolano", result.areas[0].area)
    }

    @Test
    fun `painLog - ignoruje treningi starsze niz 90 dni`() {
        val result = computePainLog90d(listOf(
            workout(daysAgo = 100, painArea = "kolano"),  // za stare
            workout(daysAgo = 30, painArea = "bark"),
            workout(daysAgo = 10, painArea = "bark")
        ), nowMs = now)
        assertEquals(2, result.totalWorkoutsAnalyzed)
        assertEquals(2, result.totalWorkoutsWithPain)
        assertEquals(1, result.areas.size)  // tylko bark, kolano jest >90 dni temu
        assertEquals("bark", result.areas[0].area)
        assertEquals(2, result.areas[0].count)
    }

    @Test
    fun `painLog - sortuje po liczbie wystapien malejaco`() {
        val result = computePainLog90d(listOf(
            workout(daysAgo = 80, painArea = "bark"),
            workout(daysAgo = 60, painArea = "kolano"),
            workout(daysAgo = 40, painArea = "kolano"),
            workout(daysAgo = 30, painArea = "kolano"),
            workout(daysAgo = 20, painArea = "lokiec"),
            workout(daysAgo = 10, painArea = "kolano")
        ), nowMs = now)
        assertEquals(3, result.areas.size)
        assertEquals("kolano", result.areas[0].area)  // 4 razy - najczesciej
        assertEquals(4, result.areas[0].count)
        assertEquals("bark", result.areas[1].area)  // 1x
        assertEquals("lokiec", result.areas[2].area)  // 1x
    }

    // ============== isPlanRelatedPrompt ==============

    @Test
    fun `isPlan - pytania analizujace progres = false`() {
        val prompts = listOf(
            "zrób analizę progresu z ostatnich 8 tygodni",
            "jak idzie mój bench press",
            "czy są dysbalanse mięśniowe",
            "jak wygląda moja objętość treningowa",
            "ile zrobiłem w tym tygodniu",
            "co o mnie myślisz jako trener",
            "kiedy ostatnio pobiłem PR",
            "dlaczego boli mnie kolano",
            "jaki jest mój e1RM na bench"
        )
        prompts.forEach { p ->
            assertTrue("'$p' nie powinien byc plan-related", !isPlanRelatedPrompt(p))
        }
    }

    @Test
    fun `isPlan - pytania o modyfikacje planu = true`() {
        val prompts = listOf(
            "zmień mi plan",
            "modyfikuj plan PPL",
            "dodaj ćwiczenie do poniedziałku",
            "zamień przysiad na suwnicę",
            "wymień ćwiczenie",
            "usuń wykrok z planu",
            "zaproponuj nowy plan",
            "wygeneruj plan na masę",
            "stwórz plan 4-dniowy",
            "popraw plan żeby było mniej nóg"
        )
        prompts.forEach { p ->
            assertTrue("'$p' powinien byc plan-related", isPlanRelatedPrompt(p))
        }
    }

    @Test
    fun `isPlan - case insensitive`() {
        assertTrue(isPlanRelatedPrompt("ZMIEŃ MI PLAN"))
        assertTrue(isPlanRelatedPrompt("Zaproponuj Plan"))
        assertTrue(isPlanRelatedPrompt("Wygeneruj NOWY PLAN"))
    }

    @Test
    fun `isPlan - pytania ogolne bez slow planu = false`() {
        // np. quick ask z ekranu statystyk
        assertTrue(!isPlanRelatedPrompt("co tu widzę"))
        assertTrue(!isPlanRelatedPrompt("jak interpretować te liczby"))
        assertTrue(!isPlanRelatedPrompt("co mogę poprawić w technice"))
        assertTrue(!isPlanRelatedPrompt("ile białka dziennie"))
        assertTrue(!isPlanRelatedPrompt("kiedy zrobić deload"))
    }

    @Test
    fun `painLog - lastOccurrence to najnowszy trening dla danego area`() {
        val result = computePainLog90d(listOf(
            workout(daysAgo = 50, painArea = "kolano"),
            workout(daysAgo = 30, painArea = "kolano"),
            workout(daysAgo = 5, painArea = "kolano")  // <- najnowszy
        ), nowMs = now)
        val kolano = result.areas.first { it.area == "kolano" }
        assertEquals(now - 5 * msPerDay, kolano.lastOccurrenceMs)
    }
}
