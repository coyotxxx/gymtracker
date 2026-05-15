package pl.filebit.gymtracker.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.27.0 — regresja bugu C: cicha utrata posiłków przy imporcie diety.
 *
 * DietBackupManager zapisuje posiłki po NAZWIE produktu. Gdy przy imporcie
 * produkt o tej nazwie nie istnieje, posiłek był wcześniej pomijany cichym
 * `continue` — user widział "import OK", a posiłki znikały bez śladu.
 * Teraz pominięte posiłki są zliczane i raportowane userowi.
 */
class DietImportSummaryTest {

    @Test
    fun `komunikat ostrzega o pominietych posilkach z nazwami produktow`() {
        val summary = ImportSummary(
            customProductsImported = 2,
            mealsImported = 5,
            feedbackImported = 0,
            hydrationImported = 0,
            recoveryImported = 0,
            mealsSkipped = 3,
            skippedProductNames = listOf("Kurczak BIO", "Mój jogurt", "Owsianka domowa")
        )
        val msg = summary.toUserMessage()

        assertTrue("komunikat liczy zaimportowane posiłki", msg.contains("5 posiłków"))
        assertTrue("komunikat ostrzega o pominiętych", msg.contains("Pominięto 3"))
        assertTrue("komunikat wymienia brakujący produkt", msg.contains("Kurczak BIO"))
        assertTrue("komunikat radzi jak odzyskać posiłki",
            msg.contains("zaimportuj", ignoreCase = true))
    }

    @Test
    fun `bez pominietych posilkow komunikat nie zawiera ostrzezenia`() {
        val summary = ImportSummary(
            customProductsImported = 1,
            mealsImported = 10,
            feedbackImported = 0,
            hydrationImported = 0,
            recoveryImported = 0,
            mealsSkipped = 0
        )
        val msg = summary.toUserMessage()
        assertFalse("brak pominięć → brak ostrzeżenia", msg.contains("Pominięto"))
    }

    @Test
    fun `wiele pominietych - komunikat skraca liste`() {
        val summary = ImportSummary(
            customProductsImported = 0,
            mealsImported = 0,
            feedbackImported = 0,
            hydrationImported = 0,
            recoveryImported = 0,
            mealsSkipped = 12,
            skippedProductNames = (1..12).map { "Produkt $it" }
        )
        val msg = summary.toUserMessage()
        assertTrue("pokazuje liczbę pominiętych", msg.contains("Pominięto 12"))
        assertTrue("skraca długą listę nazw", msg.contains("i 7 innych"))
    }
}
