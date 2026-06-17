package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.coach.CoachAction
import pl.filebit.gymtracker.data.coach.CoachActionType
import pl.filebit.gymtracker.data.coach.CoachDomain
import pl.filebit.gymtracker.data.coach.CoachPriority
import pl.filebit.gymtracker.data.coach.CoachReaction
import pl.filebit.gymtracker.data.coach.arbitrateCoach

/**
 * v2.63.0 („ciągnij dalej") — MATRYCA arbitra Coacha. arbitrateCoach łączy WIELE jednoczesnych
 * sygnałów (zdrowie/regeneracja/powrót/systematyczność/optymalizacja) w JEDNĄ dominującą reakcję.
 * To węzeł decyzyjny „jednego głosu" — testuje WSZYSTKIE podzbiory puli reakcji (2^N) + niezmienniki:
 *
 *  - dominująca = najniższy rank priorytetu (zdrowie>regeneracja>powrót>systematyczność>optymalizacja),
 *  - ANTI-KONFLIKT: gdy jest kontuzja (HEALTH), znika sugestia deloadu treningu (OPTIMIZATION),
 *  - DEDUP: co najwyżej JEDNA reakcja „deload-podobna" (APPLY_DELOAD/APPLY_REFEED) w wyniku,
 *  - żadna reakcja nie duplikuje się, wynik ⊆ wejście, pusty→pusty.
 */
class CoachArbiterMatrixTest {

    private fun r(id: String, domain: CoachDomain, prio: CoachPriority, vararg acts: CoachActionType) =
        CoachReaction(id = id, domain = domain, priority = prio, title = id, message = id,
            actions = acts.map { CoachAction(it, it.name) }, source = "test")

    // Pula reprezentatywnych reakcji (różne domeny/priorytety/deload-like).
    private val pool = listOf(
        r("injury", CoachDomain.HEALTH, CoachPriority.HEALTH, CoachActionType.REST_INJURY),
        r("refeed", CoachDomain.RECOVERY, CoachPriority.RECOVERY, CoachActionType.APPLY_REFEED),
        r("return", CoachDomain.TRAINING, CoachPriority.RETURN, CoachActionType.RETURN_LIGHT),
        r("missed", CoachDomain.CONSISTENCY, CoachPriority.CONSISTENCY, CoachActionType.START_WORKOUT),
        r("deload", CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_DELOAD),
        r("kcal", CoachDomain.DIET, CoachPriority.OPTIMIZATION, CoachActionType.APPLY_KCAL_ADJUST),
        r("deload2", CoachDomain.RECOVERY, CoachPriority.RECOVERY, CoachActionType.APPLY_DELOAD),
        r("periodization", CoachDomain.TRAINING, CoachPriority.OPTIMIZATION, CoachActionType.OPEN_PERIODIZATION)
    )

    private fun CoachReaction.isDeloadLike() =
        actions.any { it.type == CoachActionType.APPLY_DELOAD || it.type == CoachActionType.APPLY_REFEED }

    @Test
    fun `matryca arbitra - wszystkie podzbiory puli reakcji`() {
        val violations = mutableListOf<String>()
        val n = pool.size
        var count = 0
        for (mask in 0 until (1 shl n)) {
            count++
            val input = (0 until n).filter { (mask shr it) and 1 == 1 }.map { pool[it] }
            val v = arbitrateCoach(input)
            val ctx = "input=${input.map { it.id }}"

            if (input.isEmpty()) {
                if (!v.isEmpty) violations += "PUSTE_WEJSCIE_NIEPUSTY: $ctx"
                continue
            }
            if (v.primary == null) violations += "BRAK_PRIMARY: $ctx"

            val out = v.all
            // wynik ⊆ wejście
            if (out.any { o -> input.none { it.id == o.id } }) violations += "WYNIK_SPOZA_WEJSCIA: $ctx → ${out.map { it.id }}"
            // brak duplikatów
            if (out.map { it.id }.toSet().size != out.size) violations += "DUPLIKAT: $ctx → ${out.map { it.id }}"

            // dedup deload-like ≤1
            if (out.count { it.isDeloadLike() } > 1) violations += "WIELE_DELOAD: $ctx → ${out.filter { it.isDeloadLike() }.map { it.id }}"

            // anti-konflikt: HEALTH obecny → brak TRAINING+OPTIMIZATION
            if (out.any { it.priority == CoachPriority.HEALTH } &&
                out.any { it.domain == CoachDomain.TRAINING && it.priority == CoachPriority.OPTIMIZATION })
                violations += "KONTUZJA_NIE_USUNELA_DELOADU: $ctx → ${out.map { it.id }}"

            // primary = najniższy rank wśród WIDOCZNYCH w wyniku
            v.primary?.let { p ->
                val minRank = out.minOf { it.priority.rank }
                if (p.priority.rank != minRank) violations += "PRIMARY_NIE_NAJWAZNIEJSZY: $ctx primary=${p.id}(${p.priority})"
            }
        }
        println("=== ARBITER: $count podzbiorów, naruszeń: ${violations.size} ===")
        violations.take(40).forEach { println("  $it") }
        assertTrue("Naruszenia (${violations.size}):\n" + violations.take(25).joinToString("\n"), violations.isEmpty())
    }
}
