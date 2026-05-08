package pl.filebit.gymtracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.filebit.gymtracker.MainActivity
import pl.filebit.gymtracker.data.repository.StatsRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.entity.SetType

/**
 * Codzienny check AI — sprawdza recovery/stagnacje/ból z ostatnich treningów
 * i wysyła pojedynczą notyfikację z najsilniejszym sygnałem. Tap na notyfikację
 * otwiera AI Trener.
 *
 * Reguły wyboru sygnału (priorytet od najwyższego):
 * 1. Powtarzający się ból w ≥2 z 3 ostatnich treningów (PILNE)
 * 2. Stagnacja wykryta w ostatnim treningu (WAŻNE)
 * 3. Partia mięśniowa nie trenowana ≥7 dni (PRZYPOMNIENIE)
 *
 * Brak sygnałów → bez notyfikacji.
 */
@HiltWorker
class ProactiveAiCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profileRepo: UserProfileRepository,
    private val statsRepo: StatsRepository,
    private val workoutDao: WorkoutDao,
    private val statsCacheService: pl.filebit.gymtracker.data.repository.StatsCacheService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profile = profileRepo.get()
        if (!profile.aiProactiveChecksEnabled) return Result.success()

        // 1. Sprawdź ostatnie treningi pod kątem powtarzającego się bólu
        val recent = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null }
            .sortedByDescending { it.startedAt }
            .take(3)
        val recurringPain = recent
            .mapNotNull { it.painArea }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .keys
            .firstOrNull()

        // 2. Stagnacje z ostatniego treningu
        val lastWorkoutId = recent.firstOrNull()?.id
        val stagnation = if (lastWorkoutId != null) {
            statsRepo.detectStagnation(lastWorkoutId, threshold = 3).firstOrNull()
        } else null

        // 3. Partia >7 dni bez treningu
        val recovery = statsRepo.recoveryByMuscleFast(statsCacheService.snapshot())
        val staleMuscle = recovery
            .filter { it.daysAgo >= 7 }
            .maxByOrNull { it.daysAgo }

        val (title, body, prompt) = when {
            recurringPain != null -> Triple(
                "⚠ Ostrzeżenie od AI",
                "Zgłaszałeś ból ($recurringPain) w ≥2 z ostatnich 3 treningów. Sprawdźmy co zmienić.",
                "PAIN_RECOVERY"
            )
            stagnation != null -> Triple(
                "📊 Stagnacja w ${stagnation.exerciseName}",
                "Już ${stagnation.workoutsAtSameWeight} treningów na tej samej wadze. Czas na deload?",
                "DELOAD"
            )
            staleMuscle != null -> Triple(
                "💪 ${muscleLabel(staleMuscle.muscle)} bez treningu ${staleMuscle.daysAgo} dni",
                "Czas wrócić — kliknij by AI zaproponowało dzisiejszą sesję.",
                "TODAY"
            )
            else -> return Result.success()  // brak sygnału — milczymy
        }

        notify(title, body, prompt)
        return Result.success()
    }

    private fun muscleLabel(muscle: pl.filebit.gymtracker.data.entity.MuscleGroup): String =
        when (muscle) {
            pl.filebit.gymtracker.data.entity.MuscleGroup.CHEST -> "Klatka"
            pl.filebit.gymtracker.data.entity.MuscleGroup.BACK -> "Plecy"
            pl.filebit.gymtracker.data.entity.MuscleGroup.SHOULDERS -> "Barki"
            pl.filebit.gymtracker.data.entity.MuscleGroup.BICEPS -> "Biceps"
            pl.filebit.gymtracker.data.entity.MuscleGroup.TRICEPS -> "Triceps"
            pl.filebit.gymtracker.data.entity.MuscleGroup.QUADS -> "Czworogłowe"
            pl.filebit.gymtracker.data.entity.MuscleGroup.HAMSTRINGS -> "Dwugłowe"
            pl.filebit.gymtracker.data.entity.MuscleGroup.GLUTES -> "Pośladki"
            pl.filebit.gymtracker.data.entity.MuscleGroup.CALVES -> "Łydki"
            pl.filebit.gymtracker.data.entity.MuscleGroup.CORE -> "Brzuch"
            pl.filebit.gymtracker.data.entity.MuscleGroup.CARDIO -> "Cardio"
            pl.filebit.gymtracker.data.entity.MuscleGroup.OTHER -> "Inne"
        }

    private fun notify(title: String, body: String, deepLinkAction: String) {
        val ctx = applicationContext
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AI_TRAINER, true)
            putExtra(EXTRA_QUICK_ACTION, deepLinkAction)
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "proactive_ai_check_channel"
        const val NOTIFICATION_ID = 5252
        const val UNIQUE_WORK_NAME = "proactive_ai_check"
        const val EXTRA_OPEN_AI_TRAINER = "open_ai_trainer"
        const val EXTRA_QUICK_ACTION = "ai_quick_action"
    }
}
