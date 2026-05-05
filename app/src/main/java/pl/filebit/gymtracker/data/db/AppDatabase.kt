package pl.filebit.gymtracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pl.filebit.gymtracker.data.db.dao.AiChatMessageDao
import pl.filebit.gymtracker.data.db.dao.AiConversationDao
import pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao
import pl.filebit.gymtracker.data.db.dao.WeeklyPlanOverrideDao
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.FastingWindowDao
import pl.filebit.gymtracker.data.db.dao.FoodProductDao
import pl.filebit.gymtracker.data.db.dao.DailyActivityLogDao
import pl.filebit.gymtracker.data.db.dao.DietPhaseDao
import pl.filebit.gymtracker.data.db.dao.HydrationLogDao
import pl.filebit.gymtracker.data.db.dao.MealPrepPlanDao
import pl.filebit.gymtracker.data.db.dao.RecoveryLogDao
import pl.filebit.gymtracker.data.db.dao.MealConsumptionDao
import pl.filebit.gymtracker.data.db.dao.MealEntryDao
import pl.filebit.gymtracker.data.db.dao.MealFeedbackDao
import pl.filebit.gymtracker.data.db.dao.ShoppingListDao
import pl.filebit.gymtracker.data.db.dao.RecipeDao
import pl.filebit.gymtracker.data.db.dao.UnlockedAchievementDao
import pl.filebit.gymtracker.data.db.dao.AdherenceLogDao
import pl.filebit.gymtracker.data.db.dao.DietAdjustmentDao
import pl.filebit.gymtracker.data.db.dao.TrainingDaySummaryDao
import pl.filebit.gymtracker.data.db.dao.UserDietProfileDao
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
import pl.filebit.gymtracker.data.entity.FastingWindow
import pl.filebit.gymtracker.data.entity.FoodProduct
import pl.filebit.gymtracker.data.entity.DailyActivityLog
import pl.filebit.gymtracker.data.entity.DietPhase
import pl.filebit.gymtracker.data.entity.HydrationLog
import pl.filebit.gymtracker.data.entity.MealPrepPlan
import pl.filebit.gymtracker.data.entity.MealPrepStep
import pl.filebit.gymtracker.data.entity.RecoveryLog
import pl.filebit.gymtracker.data.entity.MealConsumption
import pl.filebit.gymtracker.data.entity.MealEntry
import pl.filebit.gymtracker.data.entity.MealFeedback
import pl.filebit.gymtracker.data.entity.ShoppingList
import pl.filebit.gymtracker.data.entity.ShoppingListItem
import pl.filebit.gymtracker.data.entity.Recipe
import pl.filebit.gymtracker.data.entity.UnlockedAchievement
import pl.filebit.gymtracker.data.entity.AdherenceLog
import pl.filebit.gymtracker.data.entity.DietAdjustment
import pl.filebit.gymtracker.data.entity.TrainingDaySummary
import pl.filebit.gymtracker.data.entity.UserDietProfile
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
        UnlockedAchievement::class,
        FoodProduct::class,
        MealEntry::class,
        FastingWindow::class,
        Recipe::class,
        UserDietProfile::class,
        TrainingDaySummary::class,
        AdherenceLog::class,
        DietAdjustment::class,
        MealFeedback::class,
        ShoppingList::class,
        ShoppingListItem::class,
        HydrationLog::class,
        RecoveryLog::class,
        DailyActivityLog::class,
        DietPhase::class,
        MealPrepPlan::class,
        MealPrepStep::class,
        MealConsumption::class
    ],
    version = 45,
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
    abstract fun foodProductDao(): FoodProductDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun fastingWindowDao(): FastingWindowDao
    abstract fun recipeDao(): RecipeDao
    abstract fun userDietProfileDao(): UserDietProfileDao
    abstract fun trainingDaySummaryDao(): TrainingDaySummaryDao
    abstract fun adherenceLogDao(): AdherenceLogDao
    abstract fun dietAdjustmentDao(): DietAdjustmentDao
    abstract fun mealFeedbackDao(): MealFeedbackDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun hydrationLogDao(): HydrationLogDao
    abstract fun recoveryLogDao(): RecoveryLogDao
    abstract fun dailyActivityLogDao(): DailyActivityLogDao
    abstract fun dietPhaseDao(): DietPhaseDao
    abstract fun mealPrepPlanDao(): MealPrepPlanDao
    abstract fun mealConsumptionDao(): MealConsumptionDao

    companion object {
        const val NAME = "gymtracker.db"
    }
}
