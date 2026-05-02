package pl.filebit.gymtracker.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Plans : Screen("plans")
    object ExerciseLibrary : Screen("exercises")
    object Profile : Screen("profile")
    object ActiveWorkout : Screen("workout/active")
    object CoachWorkout : Screen("workout/coach")
    object Backup : Screen("backup")
    object Stats : Screen("stats")
    object Achievements : Screen("stats/achievements")
    object OneRm : Screen("tools/1rm")
    object PlateCalc : Screen("tools/plate")
    object PlanTemplates : Screen("plans/templates")
    object BodyMeasurements : Screen("tools/body")
    object MuscleEngagement : Screen("tools/muscles")
    object ProgressPhotos : Screen("tools/photos")
    object StrengthStandards : Screen("tools/strength")
    object AiConversations : Screen("ai/conversations")
    object AiSettings : Screen("ai/settings")
    object AiTrainer : Screen("ai/trainer/{conversationId}") {
        fun create(conversationId: Long) = "ai/trainer/$conversationId"
    }
    object AiWeeklyReport : Screen("ai/weekly-report")
    object Measurements : Screen("measurements")
    object MeasurementAdd : Screen("measurements/add?id={id}") {
        fun create(id: Long? = null) = "measurements/add?id=${id ?: 0L}"
    }
    object BodyMap : Screen("body-map")
    object Goals : Screen("goals")
    object Glossary : Screen("glossary")

    object ExerciseDetail : Screen("exercises/{exerciseId}") {
        fun create(exerciseId: Long) = "exercises/$exerciseId"
    }

    object PlanEdit : Screen("plans/{planId}") {
        fun create(planId: Long) = "plans/$planId"
    }

    object ExercisePicker : Screen("workout/picker?mode={mode}") {
        fun create(mode: String = "WORKOUT") = "workout/picker?mode=$mode"
    }

    object WorkoutDetail : Screen("workout/{workoutId}") {
        fun create(workoutId: Long) = "workout/$workoutId"
    }
}
