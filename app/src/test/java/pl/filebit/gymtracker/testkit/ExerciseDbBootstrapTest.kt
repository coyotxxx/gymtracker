package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.repository.ExerciseDbBootstrap
import pl.filebit.gymtracker.data.seed.ExerciseSeeder

/**
 * v1.27 — FAZA 2.5 — testy ExerciseDbBootstrap.
 *
 * Bootstrap: seed (193 ćwiczeń) + import ExerciseDB + match (CANONICAL +
 * seed_match) + dedup + dead-URL cleanup. Test sprawdza efekt końcowy:
 * biblioteka wzbogacona o GIFy, BEZ duplikatów.
 */
class ExerciseDbBootstrapTest : TestHarness() {

    @Test
    fun `bootstrap wzbogaca biblioteke o GIFy i nie zostawia duplikatow`() = runBlocking {
        // seed 193 domyślnych ćwiczeń
        ExerciseSeeder(context, db.exerciseDao()).seedIfEmpty()
        val beforeCount = db.exerciseDao().count()

        // bootstrap — import ExerciseDB + match + dedup
        val (matched, imported) = ExerciseDbBootstrap(context, db.exerciseDao()).bootstrap()

        val all = db.exerciseDao().getAll()
        val withGif = all.count { !it.gifUrl.isNullOrBlank() }
        val withExternalId = all.count { it.externalId != null }

        // duplikaty: po (znormalizowana nazwa, primaryMuscle)
        fun norm(s: String) = s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val dupGroups = all.groupBy { norm(it.name) to it.primaryMuscle }
            .filter { it.value.size > 1 }

        val tr = TraceReport("exercisedb-bootstrap")
            .section("DANE")
            .kv("ćwiczeń przed bootstrap (seed)", beforeCount.toString())
            .kv("ćwiczeń po bootstrap", all.size.toString())
            .section("WYNIK bootstrap")
            .verdict("bootstrap()", "matched=$matched, imported=$imported", "")
            .kv("ćwiczenia z GIF", "$withGif / ${all.size}")
            .kv("ćwiczenia z externalId", "$withExternalId / ${all.size}")
            .section("OCENA")
        if (dupGroups.isEmpty()) {
            tr.line("brak duplikatów — dedup zadziałał")
        } else {
            tr.note("[KONFLIKT] ${dupGroups.size} grup duplikatów po bootstrap:")
            dupGroups.keys.take(5).forEach { tr.line("  $it") }
        }
        tr.emit()

        assertTrue("bootstrap powinien wzbogacić bibliotekę (import z ExerciseDB)",
            all.size > beforeCount)
        assertTrue("znaczna część ćwiczeń ma GIF po bootstrap (>50%)",
            withGif > all.size / 2)
        assertTrue("bootstrap NIE zostawia duplikatów (normalize name + muscle)",
            dupGroups.isEmpty())
    }
}
