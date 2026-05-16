package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.toDietGoal
import pl.filebit.gymtracker.data.entity.toWeightGoal
import pl.filebit.gymtracker.data.repository.UserProfileRepository

/**
 * v1.28.1 — Etap 2 refaktoru "jedno źródło prawdy" (docs/CONFIG-UNIFICATION-PLAN.md).
 *
 * `goalType` (DietGoalType) jest JEDYNYM celem aplikacji. Legacy `weightGoalType`
 * jest auto-normalizowany z `goalType` przy każdym zapisie — dwa pola NIE mogą
 * się rozjechać (Bug B z audytu: trening=Redukcja vs dieta=Utrzymanie).
 */
class GoalUnificationTest : TestHarness() {

    @Test
    fun `mapowanie goalType - weightGoal jest spojne`() {
        // 8 wartości DietGoalType → kierunek wagi
        assertEquals(WeightGoalType.CUT, DietGoalType.FAT_LOSS.toWeightGoal())
        assertEquals(WeightGoalType.CUT, DietGoalType.EVENT_PREP.toWeightGoal())
        assertEquals(WeightGoalType.BULK, DietGoalType.MUSCLE_GAIN.toWeightGoal())
        assertEquals(WeightGoalType.MAINTAIN, DietGoalType.MAINTAIN.toWeightGoal())
        assertEquals(WeightGoalType.MAINTAIN, DietGoalType.RECOMP.toWeightGoal())
        assertEquals(WeightGoalType.MAINTAIN, DietGoalType.STRENGTH.toWeightGoal())
        // odwrotne mapowanie (legacy → kanoniczny)
        assertEquals(DietGoalType.FAT_LOSS, WeightGoalType.CUT.toDietGoal())
        assertEquals(DietGoalType.MUSCLE_GAIN, WeightGoalType.BULK.toDietGoal())
        assertEquals(DietGoalType.MAINTAIN, WeightGoalType.MAINTAIN.toDietGoal())
        assertEquals(DietGoalType.MAINTAIN, WeightGoalType.NONE.toDietGoal())
    }

    @Test
    fun `zapis profilu normalizuje weightGoalType z goalType`() = runBlocking {
        val repo = UserProfileRepository(db.userProfileDao())
        repo.save(UserProfile(goalType = DietGoalType.FAT_LOSS))
        val saved = repo.get()

        val tr = TraceReport("goal-normalize")
            .section("ZAPIS goalType=FAT_LOSS")
            .kv("goalType", saved.goalType.name)
            .verdict("weightGoalType", saved.weightGoalType.name, "CUT (znormalizowany)")
            .emit()

        assertEquals(DietGoalType.FAT_LOSS, saved.goalType)
        assertEquals(WeightGoalType.CUT, saved.weightGoalType)
    }

    @Test
    fun `zapis naprawia rozjechany cel (Bug B)`() = runBlocking {
        val repo = UserProfileRepository(db.userProfileDao())
        // Symulacja rozjazdu: ktoś ustawia legacy weightGoalType=CUT, ale goalType=MAINTAIN.
        repo.save(UserProfile(goalType = DietGoalType.MAINTAIN, weightGoalType = WeightGoalType.CUT))
        val saved = repo.get()

        val tr = TraceReport("goal-desync-fix")
            .section("OCENA — koniec rozjazdu trening↔dieta")
            .kv("zapisano weightGoalType", "CUT (legacy)")
            .kv("zapisano goalType", "MAINTAIN (kanoniczny)")
            .verdict("weightGoalType po zapisie", saved.weightGoalType.name,
                "MAINTAIN — zsynchronizowany z goalType, nie da się rozjechać")
            .emit()

        // goalType wygrywa — weightGoalType znormalizowany do MAINTAIN.
        assertEquals(WeightGoalType.MAINTAIN, saved.weightGoalType)
        assertEquals(DietGoalType.MAINTAIN, saved.goalType)
    }
}
