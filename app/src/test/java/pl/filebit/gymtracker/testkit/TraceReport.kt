package pl.filebit.gymtracker.testkit

import java.io.File

/**
 * v1.27 — FAZA 0.2 — Trace Reporter.
 *
 * Każdy test E2E produkuje czytelny raport — nie czarna skrzynka pass/fail.
 * Wymóg Macieja: "po teście musisz wiedzieć wszystko co się stało, jak
 * aplikacja zareagowała i dlaczego".
 *
 * Raport ma stałą strukturę:
 *   - DANE (pokrycie) — co analiza miała do dyspozycji, czego brakuje
 *   - DETEKTORY — werdykt każdego warunku + uzasadnienie
 *   - CO WIDZI USER — finalne karty/komunikaty ekranu
 *   - reguły ukrywania — co świadomie pominięto i czemu
 *   - OCENA — uwagi jakościowe
 *
 * Użycie:
 * ```
 * TraceReport("scenariusz C")
 *     .section("DANE (pokrycie)")
 *     .kv("treningi", "60")
 *     .coverage("HRV", false)
 *     .section("DETEKTORY")
 *     .verdict("DeloadDetector", "REFEED_RECOMMENDED", "avgRPE 14d=10.0, cel=CUT")
 *     .section("CO WIDZI USER")
 *     .shows("REFEED ZALECANY", "Twoje treningi są intensywne...")
 *     .hidden("TrainingPhaseCard", "DeloadSuggestion ma priorytet")
 *     .section("OCENA")
 *     .note("brak HRV/snu — recovery oparta tylko na RPE")
 *     .emit()
 * ```
 */
class TraceReport(private val scenario: String) {

    private val sb = StringBuilder()

    init {
        sb.appendLine("=".repeat(70))
        sb.appendLine("  SCENARIUSZ: $scenario")
        sb.appendLine("=".repeat(70))
    }

    /** Nowa sekcja raportu (DANE / DETEKTORY / CO WIDZI USER / OCENA ...). */
    fun section(title: String): TraceReport {
        sb.appendLine()
        val dash = "-".repeat((64 - title.length).coerceAtLeast(3))
        sb.appendLine("--- $title $dash")
        return this
    }

    /** Wolna linia tekstu. */
    fun line(text: String): TraceReport {
        sb.appendLine("  $text")
        return this
    }

    /** Para klucz-wartość. */
    fun kv(key: String, value: String): TraceReport {
        sb.appendLine("  $key: $value")
        return this
    }

    /**
     * Pokrycie danych — czy dany typ danych był dostępny dla analizy.
     * Wprost adresuje obawę Macieja "nie oceniamy w 20% bo brak danych".
     */
    fun coverage(dataType: String, available: Boolean, detail: String = ""): TraceReport {
        val mark = if (available) "OK" else "BRAK"
        val suffix = if (detail.isNotBlank()) " ($detail)" else ""
        sb.appendLine("  [$mark] $dataType$suffix")
        return this
    }

    /**
     * Werdykt detektora/warunku z uzasadnieniem — sedno wymogu "dlaczego".
     */
    fun verdict(detector: String, result: String, reason: String = ""): TraceReport {
        sb.appendLine("  $detector -> $result")
        if (reason.isNotBlank()) sb.appendLine("      powod: $reason")
        return this
    }

    /** Element widoczny dla użytkownika (karta Home, komunikat). */
    fun shows(label: String, message: String = ""): TraceReport {
        sb.appendLine("  [WIDOCZNE] $label${if (message.isNotBlank()) " — $message" else ""}")
        return this
    }

    /** Element świadomie ukryty + powód (reguła kompozycji kart). */
    fun hidden(label: String, reason: String): TraceReport {
        sb.appendLine("  [UKRYTE]   $label — bo: $reason")
        return this
    }

    /** Uwaga jakościowa / ostrzeżenie / ocena. */
    fun note(text: String): TraceReport {
        sb.appendLine("  (!) $text")
        return this
    }

    fun render(): String = sb.toString()

    /**
     * Wypisuje raport na stdout (widoczny w logu CI) i zapisuje do
     * build/reports/traces/<scenario>.txt (do przejrzenia po przebiegu).
     */
    fun emit(): TraceReport {
        val text = render()
        println(text)
        runCatching {
            val dir = File("build/reports/traces")
            dir.mkdirs()
            val safe = scenario.replace(Regex("[^A-Za-z0-9_-]"), "_")
            File(dir, "$safe.txt").writeText(text)
        }
        return this
    }
}
