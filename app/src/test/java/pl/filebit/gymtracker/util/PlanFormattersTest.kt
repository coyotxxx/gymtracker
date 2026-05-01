package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.PlanExerciseSet

class PluralizeSeriesTest {

    @Test fun `1 returns 1 seria`() = assertEquals("1 seria", pluralizeSeries(1))

    @Test
    fun `2 to 4 returns serie`() {
        assertEquals("2 serie", pluralizeSeries(2))
        assertEquals("3 serie", pluralizeSeries(3))
        assertEquals("4 serie", pluralizeSeries(4))
    }

    @Test
    fun `5 to 21 returns serii including 12 13 14`() {
        assertEquals("5 serii", pluralizeSeries(5))
        assertEquals("11 serii", pluralizeSeries(11))
        assertEquals("12 serii", pluralizeSeries(12))
        assertEquals("13 serii", pluralizeSeries(13))
        assertEquals("14 serii", pluralizeSeries(14))
        assertEquals("21 serii", pluralizeSeries(21))
    }

    @Test
    fun `22 to 24 returns serie`() {
        assertEquals("22 serie", pluralizeSeries(22))
        assertEquals("23 serie", pluralizeSeries(23))
        assertEquals("24 serie", pluralizeSeries(24))
    }

    @Test
    fun `25 onward returns serii again`() {
        assertEquals("25 serii", pluralizeSeries(25))
        assertEquals("31 serii", pluralizeSeries(31))
    }
}

class SummarizeSetsTest {

    private fun set(reps: Int, rest: Int? = null): PlanExerciseSet =
        PlanExerciseSet(planExerciseId = 1L, setNumber = 1, reps = reps, restSeconds = rest)

    @Test fun `empty list returns dash`() = assertEquals("—", summarizeSets(emptyList()))

    @Test
    fun `single set with all fields`() {
        val r = summarizeSets(listOf(set(reps = 10, rest = 90)))
        assertEquals("1 seria · 10 powt. · 90s odp.", r)
    }

    @Test
    fun `three sets uniform reps`() {
        val r = summarizeSets(listOf(set(8, 90), set(8, 90), set(8, 90)))
        assertEquals("3 serie · 8 powt. · 90s odp.", r)
    }

    @Test
    fun `varied reps shows range`() {
        val r = summarizeSets(listOf(set(12), set(10), set(8), set(6), set(5)))
        // brak rest → trzecia część pomijana
        assertEquals("5 serii · 5-12 powt.", r)
    }

    @Test
    fun `picks first non-null rest`() {
        val r = summarizeSets(listOf(set(8, null), set(8, 120), set(8, null)))
        assertEquals("3 serie · 8 powt. · 120s odp.", r)
    }
}

class FilterWeightInputTest {

    @Test
    fun `keeps digits`() {
        assertEquals("12", filterWeightInput("12"))
        assertEquals("1234", filterWeightInput("1234"))
    }

    @Test
    fun `keeps decimal separator dot or comma`() {
        assertEquals("12.5", filterWeightInput("12.5"))
        assertEquals("12,5", filterWeightInput("12,5"))
    }

    @Test
    fun `strips letters and units`() {
        assertEquals("12.5", filterWeightInput("12.5kg"))
        assertEquals("12", filterWeightInput("abc12def"))
        assertEquals("100", filterWeightInput("100lbs"))
    }

    @Test
    fun `allows only one decimal separator`() {
        // "12.5.6" → keep "12." then digits after first sep → "12.56"
        assertEquals("12.56", filterWeightInput("12.5.6"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", filterWeightInput(""))
        assertEquals("", filterWeightInput("kg"))
    }

    @Test
    fun `negative sign is stripped`() {
        // negatywne wagi nie istnieją, znak '-' usuwany
        assertEquals("12.5", filterWeightInput("-12.5"))
    }
}
