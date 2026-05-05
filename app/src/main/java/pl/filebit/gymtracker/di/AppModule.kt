package pl.filebit.gymtracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.db.dao.AiChatMessageDao
import pl.filebit.gymtracker.data.db.dao.AiConversationDao
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.UnlockedAchievementDao
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
import pl.filebit.gymtracker.data.db.dao.GoalDao
import pl.filebit.gymtracker.data.db.dao.ProgressPhotoDao
import pl.filebit.gymtracker.data.db.dao.TrainingPlanDao
import pl.filebit.gymtracker.data.db.dao.UserProfileDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.ai.AiClient
import pl.filebit.gymtracker.ai.AiClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // MVP, brak produkcyjnych danych do migracji
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideWorkoutSetDao(db: AppDatabase): WorkoutSetDao = db.workoutSetDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideTrainingPlanDao(db: AppDatabase): TrainingPlanDao = db.trainingPlanDao()
    @Provides fun providePlanExerciseDao(db: AppDatabase): PlanExerciseDao = db.planExerciseDao()
    @Provides fun providePlanExerciseSetDao(db: AppDatabase): PlanExerciseSetDao = db.planExerciseSetDao()
    @Provides fun provideBodyMeasurementDao(db: AppDatabase): BodyMeasurementDao = db.bodyMeasurementDao()
    @Provides fun provideProgressPhotoDao(db: AppDatabase): ProgressPhotoDao = db.progressPhotoDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun provideAiConversationDao(db: AppDatabase): AiConversationDao = db.aiConversationDao()
    @Provides fun provideAiChatMessageDao(db: AppDatabase): AiChatMessageDao = db.aiChatMessageDao()
    @Provides fun provideAiWeeklyReportDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.AiWeeklyReportDao = db.aiWeeklyReportDao()
    @Provides fun provideWeeklyPlanOverrideDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.WeeklyPlanOverrideDao = db.weeklyPlanOverrideDao()
    @Provides fun provideUnlockedAchievementDao(db: AppDatabase): UnlockedAchievementDao = db.unlockedAchievementDao()
    @Provides fun provideFoodProductDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.FoodProductDao = db.foodProductDao()
    @Provides fun provideMealEntryDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.MealEntryDao = db.mealEntryDao()
    @Provides fun provideFastingWindowDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.FastingWindowDao = db.fastingWindowDao()
    @Provides fun provideRecipeDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.RecipeDao = db.recipeDao()
    @Provides fun provideUserDietProfileDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.UserDietProfileDao = db.userDietProfileDao()
    @Provides fun provideTrainingDaySummaryDao(db: AppDatabase): pl.filebit.gymtracker.data.db.dao.TrainingDaySummaryDao = db.trainingDaySummaryDao()

    @Provides
    @Singleton
    fun provideExerciseSeeder(
        @ApplicationContext context: Context,
        dao: ExerciseDao
    ): ExerciseSeeder = ExerciseSeeder(context, dao)

    @Provides
    @Singleton
    fun provideFoodProductSeeder(
        @ApplicationContext context: Context,
        dao: pl.filebit.gymtracker.data.db.dao.FoodProductDao
    ): pl.filebit.gymtracker.data.seed.FoodProductSeeder =
        pl.filebit.gymtracker.data.seed.FoodProductSeeder(context, dao)

    @Provides
    @Singleton
    fun provideAiClient(impl: AiClientImpl): AiClient = impl
}
