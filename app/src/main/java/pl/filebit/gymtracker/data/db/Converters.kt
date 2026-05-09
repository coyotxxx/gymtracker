package pl.filebit.gymtracker.data.db

import androidx.room.TypeConverter
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.FoodCategory
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.MealType
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.GoalUnit
import pl.filebit.gymtracker.data.entity.MetricType
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PhotoType
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingGoal
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.WeightUnit

class Converters {
    @TypeConverter fun muscleToString(m: MuscleGroup): String = m.name
    @TypeConverter fun stringToMuscle(s: String): MuscleGroup =
        runCatching { MuscleGroup.valueOf(s) }.getOrDefault(MuscleGroup.OTHER)

    @TypeConverter fun equipmentToString(e: Equipment): String = e.name
    @TypeConverter fun stringToEquipment(s: String): Equipment =
        runCatching { Equipment.valueOf(s) }.getOrDefault(Equipment.OTHER)

    @TypeConverter fun goalToString(g: TrainingGoal): String = g.name
    @TypeConverter fun stringToGoal(s: String): TrainingGoal =
        runCatching { TrainingGoal.valueOf(s) }.getOrDefault(TrainingGoal.HYPERTROPHY)

    @TypeConverter fun expToString(e: ExperienceLevel): String = e.name
    @TypeConverter fun stringToExp(s: String): ExperienceLevel =
        runCatching { ExperienceLevel.valueOf(s) }.getOrDefault(ExperienceLevel.INTERMEDIATE)

    @TypeConverter fun unitToString(u: WeightUnit): String = u.name
    @TypeConverter fun stringToUnit(s: String): WeightUnit =
        runCatching { WeightUnit.valueOf(s) }.getOrDefault(WeightUnit.KG)

    @TypeConverter fun weightGoalToString(g: WeightGoalType): String = g.name
    @TypeConverter fun stringToWeightGoal(s: String): WeightGoalType =
        runCatching { WeightGoalType.valueOf(s) }.getOrDefault(WeightGoalType.NONE)

    @TypeConverter fun genderToString(g: Gender): String = g.name
    @TypeConverter fun stringToGender(s: String): Gender =
        runCatching { Gender.valueOf(s) }.getOrDefault(Gender.MALE)

    @TypeConverter fun goalTypeToString(g: GoalType): String = g.name
    @TypeConverter fun stringToGoalType(s: String): GoalType =
        runCatching { GoalType.valueOf(s) }.getOrDefault(GoalType.LOSE_WEIGHT)

    @TypeConverter fun goalUnitToString(u: GoalUnit): String = u.name
    @TypeConverter fun stringToGoalUnit(s: String): GoalUnit =
        runCatching { GoalUnit.valueOf(s) }.getOrDefault(GoalUnit.KG)

    @TypeConverter fun setTypeToString(t: SetType): String = t.name
    @TypeConverter fun stringToSetType(s: String): SetType = SetType.safeValueOf(s)

    @TypeConverter fun photoTypeToString(p: PhotoType): String = p.name
    @TypeConverter fun stringToPhotoType(s: String): PhotoType =
        runCatching { PhotoType.valueOf(s) }.getOrDefault(PhotoType.FRONT)

    @TypeConverter fun metricTypeToString(m: MetricType): String = m.name
    @TypeConverter fun stringToMetricType(s: String): MetricType =
        runCatching { MetricType.valueOf(s) }.getOrDefault(MetricType.WEIGHT_REPS)

    @TypeConverter fun intListToString(l: List<Int>): String = l.joinToString(",")
    @TypeConverter fun stringToIntList(s: String): List<Int> =
        if (s.isBlank()) emptyList()
        else s.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter fun foodCategoryToString(c: FoodCategory): String = c.name
    @TypeConverter fun stringToFoodCategory(s: String): FoodCategory =
        runCatching { FoodCategory.valueOf(s) }.getOrDefault(FoodCategory.OTHER)

    @TypeConverter fun mealTypeToString(m: MealType): String = m.name
    @TypeConverter fun stringToMealType(s: String): MealType =
        runCatching { MealType.valueOf(s) }.getOrDefault(MealType.LUNCH)

    @TypeConverter fun activityLevelToString(a: pl.filebit.gymtracker.data.entity.ActivityLevel): String = a.name
    @TypeConverter fun stringToActivityLevel(s: String): pl.filebit.gymtracker.data.entity.ActivityLevel =
        runCatching { pl.filebit.gymtracker.data.entity.ActivityLevel.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.ActivityLevel.MODERATE)

    @TypeConverter fun dietPrefToString(d: pl.filebit.gymtracker.data.entity.DietPreference): String = d.name
    @TypeConverter fun stringToDietPref(s: String): pl.filebit.gymtracker.data.entity.DietPreference =
        runCatching { pl.filebit.gymtracker.data.entity.DietPreference.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.DietPreference.STANDARD)

    @TypeConverter fun dietGoalToString(g: pl.filebit.gymtracker.data.entity.DietGoalType): String = g.name
    @TypeConverter fun stringToDietGoalType(s: String): pl.filebit.gymtracker.data.entity.DietGoalType =
        runCatching { pl.filebit.gymtracker.data.entity.DietGoalType.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.DietGoalType.MAINTAIN)

    @TypeConverter fun trainingTypeToString(t: pl.filebit.gymtracker.data.entity.TrainingType): String = t.name
    @TypeConverter fun stringToTrainingType(s: String): pl.filebit.gymtracker.data.entity.TrainingType =
        runCatching { pl.filebit.gymtracker.data.entity.TrainingType.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.TrainingType.REST)

    @TypeConverter fun intensityScoreToString(i: pl.filebit.gymtracker.data.entity.IntensityScore): String = i.name
    @TypeConverter fun stringToIntensityScore(s: String): pl.filebit.gymtracker.data.entity.IntensityScore =
        runCatching { pl.filebit.gymtracker.data.entity.IntensityScore.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.IntensityScore.LIGHT)

    @TypeConverter fun perfTrendToString(p: pl.filebit.gymtracker.data.entity.PerformanceTrend): String = p.name
    @TypeConverter fun stringToPerfTrend(s: String): pl.filebit.gymtracker.data.entity.PerformanceTrend =
        runCatching { pl.filebit.gymtracker.data.entity.PerformanceTrend.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.PerformanceTrend.PROGRESS)

    // v1.11.59 — TrainingEvent
    @TypeConverter fun trainingEventTypeToString(t: pl.filebit.gymtracker.data.entity.TrainingEventType): String = t.name
    @TypeConverter fun stringToTrainingEventType(s: String): pl.filebit.gymtracker.data.entity.TrainingEventType =
        runCatching { pl.filebit.gymtracker.data.entity.TrainingEventType.valueOf(s) }
            .getOrDefault(pl.filebit.gymtracker.data.entity.TrainingEventType.PR_SET)
}
