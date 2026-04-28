package pl.filebit.gymtracker.data.db

import androidx.room.TypeConverter
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.ExperienceLevel
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.TrainingGoal
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
}
