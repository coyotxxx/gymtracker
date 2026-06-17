package pl.filebit.gymtracker.testkit

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.util.SafetyGuard
import pl.filebit.gymtracker.util.SafetyResult

/**
 * v2.63.0 (Maciej: „testuj dalej wszystko w innych konfiguracjach") — MATRYCA SafetyGuard.
 * Siatka bezpieczeństwa kcal/białko/tłuszcz to OSTATNIA linia obrony przed niebezpieczną
 * dietą. Krzyżuje wagi × płeć × BMR × wartości docelowe i sprawdza NIEZMIENNIKI:
 *
 *  - minKcal NIGDY < absolutnego floora (1200 K / 1500 M),
 *  - Block → cappedValue == minimum i cappedValue > 0,
 *  - KLUCZOWE: wartość PO zablokowaniu (cappedValue) sama MUSI być bezpieczna
 *    (re-walidacja = Pass) — inaczej siatka „naprawia" do dalej-niebezpiecznej wartości,
 *  - kierunek werdyktu spójny z progami (poniżej min=Block, powyżej max=Warn).
 */
class SafetyGuardMatrixTest {

    @Test
    fun `matryca SafetyGuard - siatka bezpieczenstwa nigdy nie przepuszcza niebezpiecznej wartosci`() {
        val violations = mutableListOf<String>()
        var count = 0

        val weights = listOf(40.0, 52.0, 60.0, 75.0, 90.0, 120.0, 160.0)
        val genders = listOf(Gender.MALE, Gender.FEMALE)
        val bmrs = listOf<Int?>(null, 1200, 1400, 1700, 1900, 2200, 2600)
        val kcalTargets = listOf(0, 300, 800, 1000, 1200, 1500, 1900, 2400, 4000, 6000, 12000)

        for (w in weights) for (g in genders) for (bmr in bmrs) for (kc in kcalTargets) {
            count++
            val profile = UserProfile(gender = g, bodyweightKg = w)
            val min = SafetyGuard.minKcal(profile, bmr)
            val floor = if (g == Gender.FEMALE) 1200 else 1500
            val ctx = "w=$w g=$g bmr=$bmr target=$kc → min=$min"

            // minKcal nigdy poniżej floora
            if (min < floor) violations += "MIN_PONIZEJ_FLOORA: $ctx"

            val r = SafetyGuard.validateKcal(kc, profile, w, bmr)
            when (r) {
                is SafetyResult.Block -> {
                    if (kc >= min) violations += "BLOCK_GDY_OK: $ctx (target>=min)"
                    if (r.cappedValue != min) violations += "CAPPED_NIE_MIN: $ctx capped=${r.cappedValue}"
                    if (r.cappedValue <= 0) violations += "CAPPED_NIEDODATNI: $ctx"
                    // KLUCZOWE: capped value sam musi być bezpieczny
                    val recheck = SafetyGuard.validateKcal(r.cappedValue, profile, w, bmr)
                    if (recheck is SafetyResult.Block)
                        violations += "CAPPED_DALEJ_NIEBEZPIECZNY: $ctx capped=${r.cappedValue} → $recheck"
                }
                is SafetyResult.Warn -> {
                    if (kc <= SafetyGuard.maxKcal(w)) violations += "WARN_GDY_OK: $ctx"
                }
                SafetyResult.Pass -> {
                    if (kc < min) violations += "PASS_PONIZEJ_MIN: $ctx"
                    if (kc > SafetyGuard.maxKcal(w)) violations += "PASS_POWYZEJ_MAX: $ctx"
                }
            }
        }

        // białko + tłuszcz — capped też musi być bezpieczny
        for (w in weights) for (dpw in listOf(0, 3, 6)) for (p in listOf(0, 30, 80, 150, 400, 800)) {
            count++
            val r = SafetyGuard.validateProtein(p, w, dpw)
            if (r is SafetyResult.Block) {
                if (r.cappedValue <= 0) violations += "PROT_CAPPED_NIEDODATNI w=$w p=$p"
                val re = SafetyGuard.validateProtein(r.cappedValue, w, dpw)
                if (re is SafetyResult.Block) violations += "PROT_CAPPED_DALEJ_BLOCK w=$w p=$p capped=${r.cappedValue}"
            }
        }
        for (w in weights) for (f in listOf(0, 10, 40, 100, 300, 600)) {
            count++
            val r = SafetyGuard.validateFat(f, w)
            if (r is SafetyResult.Block) {
                val re = SafetyGuard.validateFat(r.cappedValue, w)
                if (re is SafetyResult.Block) violations += "FAT_CAPPED_DALEJ_BLOCK w=$w f=$f capped=${r.cappedValue}"
            }
        }

        println("=== MATRYCA SAFETYGUARD: $count kombinacji, naruszeń: ${violations.size} ===")
        violations.take(40).forEach { println("  $it") }
        assertTrue("Naruszenia (${violations.size}):\n" + violations.take(25).joinToString("\n"),
            violations.isEmpty())
    }
}
