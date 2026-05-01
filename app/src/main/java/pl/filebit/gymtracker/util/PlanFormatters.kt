package pl.filebit.gymtracker.util

import pl.filebit.gymtracker.data.entity.PlanExerciseSet

/**
 * Podsumowuje listę serii ćwiczenia jako "X serii · Y powt. · Zs odp."
 * Używane w PlanEditScreen.PlanExerciseCard pod tytułem ćwiczenia.
 */
fun summarizeSets(sets: List<PlanExerciseSet>): String {
    if (sets.isEmpty()) return "—"
    val seriesLabel = pluralizeSeries(sets.size)
    val repsValues = sets.map { it.reps }.distinct().sorted()
    val repsLabel = if (repsValues.size == 1) {
        "${repsValues[0]} powt."
    } else {
        "${repsValues.first()}-${repsValues.last()} powt."
    }
    val rest = sets.firstNotNullOfOrNull { it.restSeconds }
    val parts = mutableListOf(seriesLabel, repsLabel)
    if (rest != null) parts.add("${rest}s odp.")
    return parts.joinToString(" · ")
}

/**
 * Polska deklinacja liczebnika "seria":
 *  1            → "1 seria"
 *  2,3,4 (oprócz 12,13,14) → "X serie"
 *  pozostałe     → "X serii"
 */
fun pluralizeSeries(n: Int): String {
    val lastTwo = n % 100
    val last = n % 10
    return when {
        n == 1 -> "1 seria"
        last in 2..4 && lastTwo !in 12..14 -> "$n serie"
        else -> "$n serii"
    }
}

/**
 * Filtruje input dla pola wagi: zostawia tylko cyfry + co najwyżej jeden separator
 * dziesiętny ('.' lub ','). Bez tego pole akceptowało dowolny tekst (np. "12kg")
 * a w bazie zostawała stara wartość — UI rozjeżdżał się ze stanem.
 */
fun filterWeightInput(s: String): String {
    val filtered = s.filter { it.isDigit() || it == '.' || it == ',' }
    val sep = filtered.indexOfFirst { it == '.' || it == ',' }
    if (sep < 0) return filtered
    val before = filtered.substring(0, sep + 1)
    val after = filtered.substring(sep + 1).filter { it.isDigit() }
    return before + after
}
