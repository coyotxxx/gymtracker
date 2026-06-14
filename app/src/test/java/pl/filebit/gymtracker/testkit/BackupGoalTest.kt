package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.gymtracker.data.entity.DietGoalType
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import java.io.File

/**
 * v2.43.0 — DATA-SAFETY: backup treningowy NIE może spłaszczać 8-wartościowego celu.
 * Wcześniej cel zapisywał się tylko jako 4-wart. weightGoalType → RECOMP/STRENGTH/HEALTH
 * degradowały się do MAINTAIN przy restore. Teraz backup trzyma pełny `goalType`.
 */
class BackupGoalTest : TestHarness() {

    private fun importJson(json: String) = runBlocking {
        val tmp = File.createTempFile("backup_goal", ".json")
        tmp.writeText(json)
        try { backupImporter.importFromFile(tmp) } finally { tmp.delete() }
    }

    private fun profileGoal() = runBlocking { UserProfileRepository(db.userProfileDao()).get().goalType }

    @Test
    fun `backup zachowuje cel RECOMP (nie splaszcza do MAINTAIN)`() {
        importJson(
            """{"version":2,"exportedAt":0,"profile":{"goal":"HYPERTROPHY","experience":"INTERMEDIATE",""" +
                """"daysPerWeek":4,"sessionMinutes":60,"preferredUnit":"KG","defaultRestSeconds":120,""" +
                """"injuriesNotes":"","weightGoalType":"MAINTAIN","goalType":"RECOMP"},""" +
                """"exercises":[],"workouts":[],"sets":[]}"""
        )
        assertEquals("RECOMP przeżył backup (był gubiony do MAINTAIN)", DietGoalType.RECOMP, profileGoal())
    }

    @Test
    fun `stary backup bez goalType wyprowadza z weightGoalType`() {
        importJson(
            """{"version":2,"exportedAt":0,"profile":{"goal":"HYPERTROPHY","experience":"INTERMEDIATE",""" +
                """"daysPerWeek":4,"sessionMinutes":60,"preferredUnit":"KG","defaultRestSeconds":120,""" +
                """"injuriesNotes":"","weightGoalType":"CUT"},""" +
                """"exercises":[],"workouts":[],"sets":[]}"""
        )
        assertEquals("stary backup CUT → FAT_LOSS (kompatybilność)", DietGoalType.FAT_LOSS, profileGoal())
    }
}
