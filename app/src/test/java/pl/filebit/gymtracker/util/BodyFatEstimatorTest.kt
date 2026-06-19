package pl.filebit.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.Gender

class BodyFatEstimatorTest {

    @Test
    fun `Deurenberg z BMI gdy brak obwodow`() {
        // 80 kg / 1.80 m -> BMI 24.69; M, 30 lat
        // 1.20*24.69 + 0.23*30 - 10.8 - 5.4 = 20.3
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE
        )!!
        assertEquals(BodyFatMethod.DEURENBERG, e.method)
        assertEquals(20.3, e.percent, 0.2)
    }

    @Test
    fun `Navy ma pierwszenstwo gdy sa obwody (mezczyzna)`() {
        // talia 85, kark 40, wzrost 180 -> ~14.5%
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE,
            waistCm = 85.0, neckCm = 40.0
        )!!
        assertEquals(BodyFatMethod.NAVY, e.method)
        assertEquals(14.5, e.percent, 0.4)
    }

    @Test
    fun `Navy dla kobiety wymaga bioder`() {
        val withoutHips = BodyFatEstimator.estimate(
            weightKg = 62.0, heightCm = 165, ageYears = 30, gender = Gender.FEMALE,
            waistCm = 75.0, neckCm = 33.0
        )
        // bez bioder Navy odpada -> fallback Deurenberg (nie null)
        assertEquals(BodyFatMethod.DEURENBERG, withoutHips!!.method)

        val withHips = BodyFatEstimator.estimate(
            weightKg = 62.0, heightCm = 165, ageYears = 30, gender = Gender.FEMALE,
            waistCm = 75.0, neckCm = 33.0, hipsCm = 95.0
        )!!
        assertEquals(BodyFatMethod.NAVY, withHips.method)
        assertTrue("kobieta zwykle wyższy %BF", withHips.percent in 22.0..32.0)
    }

    @Test
    fun `brak wagi i obwodow daje null`() {
        assertNull(
            BodyFatEstimator.estimate(
                weightKg = null, heightCm = 180, ageYears = 30, gender = Gender.MALE
            )
        )
    }

    @Test
    fun `absurdalne dane odrzucone (poza zakresem 2-70 proc)`() {
        // talia ~= kark -> d bliskie 0 -> wynik poza zakresem -> Navy null, fallback Deurenberg
        val e = BodyFatEstimator.estimate(
            weightKg = 80.0, heightCm = 180, ageYears = 30, gender = Gender.MALE,
            waistCm = 40.5, neckCm = 40.0
        )
        // Navy da skrajnie wysoki % (odrzucony), zostaje sensowny Deurenberg
        assertTrue(e == null || e.method == BodyFatMethod.DEURENBERG)
    }
}
