package pl.filebit.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pl.filebit.gymtracker.data.db.dao.AiChatMessageDao
import pl.filebit.gymtracker.data.db.dao.AiConversationDao
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.db.dao.WeeklyPlanOverrideDao
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.UnlockedAchievementDao
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.GoalDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.ProgressPhotoDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.db.dao.UserProfileDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.AiChatMessageEntity
import pl.filebit.gymtracker.data.entity.AiConversation
import pl.filebit.gymtracker.data.entity.AiWeeklyReport
import pl.filebit.gymtracker.data.entity.WeeklyPlanOverride
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.UnlockedAchievement
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.ProgressPhoto
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutSet::class,
        UserProfile::class,
        TrainingPlan::class,
        PlanExercise::class,
        PlanExerciseSet::class,
        BodyMeasurement::class,
        ProgressPhoto::class,
        Goal::class,
        AiConversation::class,
        AiChatMessageEntity::class,
        AiWeeklyReport::class,
        WeeklyPlanOverride::class,
        UnlockedAchievement::class
    ],
    version = 28,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun planExerciseDao(): PlanExerciseDao
    abstract fun planExerciseSetDao(): PlanExerciseSetDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun goalDao(): GoalDao
    abstract fun aiConversationDao(): AiConversationDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
    abstract fun aiWeeklyReportDao(): AiWeeklyReportDao
    abstract fun weeklyPlanOverrideDao(): WeeklyPlanOverrideDao
    abstract fun unlockedAchievementDao(): UnlockedAchievementDao

    companion object {
        const val NAME = "gymtracker.db"
    }
}
