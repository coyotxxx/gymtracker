package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ocena posiłku — AI uczy się preferencji usera.
 *
 * Klucz to znormalizowana nazwa dania (lowercase, trim, bez ogonków).
 * Dlaczego nie mealEntryId? Bo:
 *  - chcemy agregować po nazwie ("Owsianka z twarogiem" zjedzona 5× = 5 ocen)
 *  - prompty AI generują różne mealEntries pod tą samą nazwą
 *  - feedback ma być przenośny między dniami/tygodniami
 *
 * Skala 1..5:
 *  - 1: nie lubię, więcej tego nie chcę
 *  - 2: średnio
 *  - 3: ok / neutralnie
 *  - 4: smaczne
 *  - 5: ulubione, daj częściej
 *
 * `tags` — opcjonalne flagi (CSV): "za-suche", "za-tluste", "za-malo", "za-duzo",
 *           "ciekawe", "nudne", "ciezkie-na-noc", "smakowo-ok-glodne", itp.
 */
@Entity(
    tableName = "meal_feedback",
    indices = [
        Index(value = ["dishKey"], unique = true),
        Index("rating"),
        Index("createdAt")
    ]
)
data class MealFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Znormalizowana nazwa (lowercase, trim, bez polskich znaków). Unikat. */
    val dishKey: String,
    /** Oryginalna nazwa wyświetlana userowi. */
    val displayName: String,
    val rating: Int,
    /** CSV tagów (np. "za-suche,nudne") lub pusty string. */
    val tags: String = "",
    val notes: String = "",
    /** Ile razy zjedzony (auto-inkrementuj przy każdej ocenie). */
    val timesEaten: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Normalizuje nazwę do klucza:
         *  - lowercase
         *  - usuwa polskie znaki (ąćęłńóśźż → acelnoszz)
         *  - trim + collapse spaces
         */
        fun normalizeKey(name: String): String {
            val lower = name.trim().lowercase()
            val ascii = StringBuilder(lower.length)
            for (c in lower) {
                ascii.append(
                    when (c) {
                        'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'
                        'ń' -> 'n'; 'ó' -> 'o'; 'ś' -> 's'; 'ź' -> 'z'; 'ż' -> 'z'
                        else -> c
                    }
                )
            }
            return ascii.toString().replace(Regex("\\s+"), " ")
        }
    }
}
