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
    object OneRm : Screen("tools/1rm")

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
