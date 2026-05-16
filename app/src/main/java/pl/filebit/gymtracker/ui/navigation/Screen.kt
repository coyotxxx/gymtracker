package pl.filebit.gymtracker.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
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
    object Calculators : Screen("tools/calculators")
    object OneRm : Screen("tools/1rm")
    object PlateCalc : Screen("tools/plate")
    object PlanTemplates : Screen("plans/templates")
    object MuscleEngagement : Screen("tools/muscles")
    object FoodDatabase : Screen("tools/food-database")
    object ProgressPhotos : Screen("tools/photos")
    object StrengthStandards : Screen("tools/strength")
    object AiConversations : Screen("ai/conversations")
    object AiSettings : Screen("ai/settings")
    object AiLog : Screen("ai/log")
    object HealthScreenshot : Screen("health/screenshot")
    object HealthHistory : Screen("health/history")
    object Notifications : Screen("notifications")
    object AiTrainer : Screen("ai/trainer/{conversationId}?auto={auto}") {
        fun create(conversationId: Long, autoAction: String? = null): String {
            // Zawsze dołączamy ?auto=… żeby Compose Navigation rejestrował query
            // poprawnie (defaultValue = "" obsłuży brak akcji)
            return "ai/trainer/$conversationId?auto=${autoAction ?: ""}"
        }
    }
    object AiWeeklyReport : Screen("ai/weekly-report")
    object Measurements : Screen("measurements")
    object MeasurementAdd : Screen("measurements/add?id={id}") {
        fun create(id: Long? = null) = "measurements/add?id=${id ?: 0L}"
    }
    object BodyMap : Screen("body-map")
    object TrainingSettings : Screen("settings/training")
    object Goals : Screen("goals")
    object Glossary : Screen("glossary")
    // v1.16.0 — ekran "Plan cyklu" (oś czasu mezo-cykli)
    object PeriodizationPlan : Screen("periodization/plan")
    // v1.21.0 — ukryty ekran Debug (5×click w stopkę wersji)
    object Debug : Screen("debug")
    object Diet : Screen("diet")
    object DietOnboarding : Screen("diet/onboarding")
    object DietAdherenceReport : Screen("diet/adherence")
    object DietAdjustmentHistory : Screen("diet/adjustments")
    object MealPreferences : Screen("diet/preferences")
    object ShoppingList : Screen("diet/shopping")
    object MealPrep : Screen("diet/mealprep")
    object BarcodeScanner : Screen("diet/scanner")
    object FoodImageAnalyzer : Screen("diet/image-analyzer")
    object RecipeBrowser : Screen("diet/recipes")

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
