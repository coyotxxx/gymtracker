package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v1.11.59 — Discrete events na osi treningowej, trzymane na zawsze.
 *
 * To jest "pamięć epizodyczna" AI: PR-y, kontuzje, deloady, zmiany planu,
 * powroty po przerwie, kamienie milowe cyklu. Nawet gdy stare treningi
 * zostaną zagregowane do quarterly/yearly summary (v1.11.60+), te eventy
 * pozostają w pełnej formie, bo to one definiują historię.
 *
 * Wzorzec z research: Trainerize / Future / RP Strength konsekwentnie
 * trzymają eventy jako first-class objects (osobne od logów liczbowych).
 */
@Entity(
    tableName = "training_events",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["workoutId"]),
        Index(value = ["exerciseId"])
    ]
)
data class TrainingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Kiedy się wydarzyło (epoch ms). */
    val date: Long,

    /** Typ eventu — patrz [TrainingEventType]. */
    val type: TrainingEventType,

    // === Foreign keys (opcjonalne — zależne od type) ===
    val workoutId: Long? = null,
    val exerciseId: Long? = null,
    val planId: Long? = null,

    // === Typowane payloady (najczęściej używane pola) ===
    /** Nazwa ćwiczenia (PR_SET, INJURY z kontekstem). */
    val exerciseName: String? = null,

    /** Waga w kg (PR_SET). */
    val weightKg: Double? = null,

    /** Liczba powtórzeń (PR_SET). */
    val reps: Int? = null,

    /** e1RM (PR_SET, dla porównań cross-rep). */
    val e1rmKg: Double? = null,

    /** Obszar bólu / kontuzji (INJURY). */
    val area: String? = null,

    /** Nazwa planu (PLAN_START / PLAN_END / PLAN_CHANGE). */
    val planName: String? = null,

    /** Tygodnie kontekstu (DELOAD_DETECTED: ile tyg w cyklu, GAP_RESUMED: dni przerwy / 7, CYCLE_MILESTONE: długość mesocyklu). */
    val weeksContext: Int? = null,

    /** Wolny opis dla AI ("dlaczego" + kontekst). */
    val notes: String = "",

    /** Kiedy event został wykryty/zarejestrowany (może być inne niż date). */
    val createdAt: Long = System.currentTimeMillis()
)

enum class TrainingEventType {
    /** Pobicie rekordu osobistego (waga × reps lub e1RM). */
    PR_SET,

    /** Aktywacja nowego planu treningowego. */
    PLAN_START,

    /** Dezaktywacja / zakończenie planu. */
    PLAN_END,

    /** Wykryta zmiana planu (start nowego = end starego). */
    PLAN_CHANGE,

    /** Wykryty deload (volume drop ≥40% week-over-week). */
    DELOAD_DETECTED,

    /** Zgłoszony ból / kontuzja podczas treningu. */
    INJURY,

    /** Pierwszy trening po przerwie >14 dni. */
    GAP_RESUMED,

    /** Koniec mesocyklu (4-6 tyg + następujący deload). */
    CYCLE_MILESTONE
}
