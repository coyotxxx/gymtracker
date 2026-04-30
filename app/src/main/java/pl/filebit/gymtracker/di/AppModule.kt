package pl.filebit.gymtracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.db.dao.BodyMeasurementDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseDao
import pl.filebit.gymtracker.data.db.dao.PlanExerciseSetDao
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

    @Provides
    @Singleton
    fun provideExerciseSeeder(
        @ApplicationContext context: Context,
        dao: ExerciseDao
    ): ExerciseSeeder = ExerciseSeeder(context, dao)

    @Provides
    @Singleton
    fun provideAiClient(impl: AiClientImpl): AiClient = impl
}
