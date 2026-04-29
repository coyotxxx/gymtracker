package pl.filebit.gymtracker.data.entity

/**
 * Typ serii — wpływa na statystyki (warm-up często wykluczany z wolumenu) i UX.
 */
enum class SetType {
    NORMAL,    // robocza
    WARMUP,    // rozgrzewkowa
    DROP,      // drop set (waga spadająca)
    FAILURE,   // do upadku
    AMRAP;     // as many reps as possible

    companion object {
        fun safeValueOf(s: String?): SetType =
            runCatching { valueOf(s ?: "") }.getOrDefault(NORMAL)
    }
}
