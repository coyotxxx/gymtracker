package pl.filebit.gymtracker.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * v1.11.67 — Definicje narzędzi (tools) dostępnych dla AI.
 *
 * Filozofia (Whoop Coach / Anthropic best practice): główny kontekst zawiera
 * agregaty (Tier 1+2+3 z memory architecture). Gdy AI potrzebuje detali
 * z głębokiej historii, wywołuje tool — aplikacja zwraca surowe dane z bazy.
 *
 * Anthropic Tool Use API: AI w odpowiedzi może użyć `tool_use` content
 * blocku zamiast text. Klient wykonuje tool i zwraca wynik jako kolejną
 * wiadomość z `tool_result` content blockiem. AI iteruje aż do finalnej
 * odpowiedzi tekstowej.
 */

object AiTools {

    /** Wszystkie zdefiniowane narzędzia w formacie Anthropic API. */
    fun toolsForApi(): kotlinx.serialization.json.JsonArray = buildJsonArray {
        add(toolGetWorkouts())
        add(toolGetEvents())
        add(toolGetRollups())
        add(toolGetExerciseHistory())
        add(toolGetBodyHistory())
        add(toolProposePeriodizationAction())  // v1.15.0
    }

    /** Lista nazw narzędzi (do walidacji w handlerze). */
    val TOOL_NAMES = setOf(
        "get_workouts",
        "get_events",
        "get_rollups",
        "get_exercise_history",
        "get_body_history",
        "propose_periodization_action"  // v1.15.0
    )

    /**
     * v1.15.0 — AI proponuje konkretną akcję periodyzacyjną.
     *
     * **Bezpieczeństwo:** Tool RO — NIE modyfikuje bazy bezpośrednio. Zapisuje
     * PendingPeriodizationDecision ze status=PENDING. User MUSI explicit zaakceptować
     * przez UI ("AI TRENER PROPONUJE" karta na Home → button [Zastosuj]).
     *
     * Używany przez:
     *  - `ProactiveAiCheckWorker` gdy `PeriodizationOrchestrator.pulse()` zwraca TransitionDue
     *  - `AiTrainerScreen` gdy user pyta "co teraz?" w kontekście cyklu
     */
    private fun toolProposePeriodizationAction(): JsonObject = buildJsonObject {
        put("name", "propose_periodization_action")
        put("description", """
            Zaproponuj konkretną akcję periodyzacyjną (przejście fazy mesocyklu) na podstawie
            sygnałów: bieżący mesocykl (faza, weekInPhase, dni do końca), algorithm proposal
            (recommendedNext, plannedStartDate), stagnation report, RPE trend, sen/HRV.

            Tool NIE wykonuje akcji — tylko zapisuje propozycję jako PendingPeriodizationDecision
            (status=PENDING) widoczną dla usera w karcie "AI TRENER PROPONUJE" na Home.
            User explicit akceptuje przez [Zastosuj].
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("action") {
                    put("type", "string")
                    put("description", "Typ akcji: ACCEPT_ALGORITHM (zgódź się z algorytmem), SHIFT_START (przesuń datę startu), MODIFY_PARAMS (zmień volume/intensity), REJECT (odrzuć - zostań na obecnej fazie jeszcze)")
                }
                putJsonObject("recommended_next_phase") {
                    put("type", "string")
                    put("description", "Docelowa faza: ACCUMULATION, INTENSIFICATION, DELOAD, PEAKING, RECOVERY")
                }
                putJsonObject("planned_start_date") {
                    put("type", "string")
                    put("description", "Planowany start nowej fazy w formacie YYYY-MM-DD")
                }
                putJsonObject("planned_duration_weeks") {
                    put("type", "integer")
                    put("description", "Długość nowej fazy w tygodniach (1-6)")
                }
                putJsonObject("volume_modifier") {
                    put("type", "number")
                    put("description", "Optional: nadpisz default volume scale dla nowej fazy (0.5-1.2)")
                }
                putJsonObject("intensity_modifier") {
                    put("type", "number")
                    put("description", "Optional: nadpisz default intensity scale dla nowej fazy (0.7-1.15)")
                }
                putJsonObject("confidence") {
                    put("type", "number")
                    put("description", "Pewność decyzji 0.0-1.0. Wyższa gdy zgadzasz się z algorytmem + masz dobre dane (sen/HRV).")
                }
                putJsonObject("reasoning") {
                    put("type", "string")
                    put("description", "Szczegółowe uzasadnienie dla usera (wyświetlone w karcie 'Wyjaśnij więcej'). Cytuj liczby z kontekstu (RPE, sen, stagnacja). Po polsku.")
                }
            }
            putJsonArray("required") {
                add("action")
                add("recommended_next_phase")
                add("planned_start_date")
                add("planned_duration_weeks")
                add("confidence")
                add("reasoning")
            }
        }
    }

    private fun toolGetWorkouts(): JsonObject = buildJsonObject {
        put("name", "get_workouts")
        put("description", "Pobiera surowe dane treningów z konkretnego okresu. Użyj gdy potrzebujesz szczegółów setów (waga, reps, RPE, RIR) z dat starszych niż recent_workouts (5 ostatnich). Maks 30 sesji per call.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("from_date") {
                    put("type", "string")
                    put("description", "Data początkowa w formacie YYYY-MM-DD (włącznie)")
                }
                putJsonObject("to_date") {
                    put("type", "string")
                    put("description", "Data końcowa w formacie YYYY-MM-DD (włącznie)")
                }
                putJsonObject("exercise_name") {
                    put("type", "string")
                    put("description", "Opcjonalnie: filtr po nazwie ćwiczenia (np. 'Wyciskanie sztangi leżąc'). Pomijasz = wszystkie ćwiczenia.")
                }
            }
            putJsonArray("required") { add("from_date"); add("to_date") }
        }
    }

    private fun toolGetEvents(): JsonObject = buildJsonObject {
        put("name", "get_events")
        put("description", "Pobiera eventy z TrainingEvent log (PR_SET / INJURY / GAP_RESUMED / DELOAD_DETECTED / PLAN_START / PLAN_END / CYCLE_MILESTONE). Użyj gdy chcesz zobaczyć więcej niż 15 ostatnich z event_log w głównym kontekście.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("type") {
                    put("type", "string")
                    put("description", "Typ eventu: PR_SET / INJURY / GAP_RESUMED / DELOAD_DETECTED / PLAN_START / PLAN_END / CYCLE_MILESTONE / ALL")
                }
                putJsonObject("days_back") {
                    put("type", "integer")
                    put("description", "Ile dni wstecz pobrać (np. 365 dla rocznej historii)")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maksymalna liczba eventów (default 50)")
                }
            }
            putJsonArray("required") { add("type"); add("days_back") }
        }
    }

    private fun toolGetRollups(): JsonObject = buildJsonObject {
        put("name", "get_rollups")
        put("description", "Pobiera period rollupy (week/month/quarter) — prekomputowane podsumowania z totalVolume, sessions, prCount, deload itd. Użyj gdy potrzebujesz więcej niż 4w/6m/4q z głównego historical_summary.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("period") {
                    put("type", "string")
                    put("description", "Typ rollupu: WEEK / MONTH / QUARTER")
                }
                putJsonObject("count") {
                    put("type", "integer")
                    put("description", "Ile ostatnich rollupów pobrać (np. 12 tygodni / 12 miesięcy / 8 kwartałów)")
                }
            }
            putJsonArray("required") { add("period"); add("count") }
        }
    }

    private fun toolGetExerciseHistory(): JsonObject = buildJsonObject {
        put("name", "get_exercise_history")
        put("description", "Pełna historia konkretnego ćwiczenia: lista (data, top set, e1RM) chronologicznie. Idealne do analizy trendu pojedynczego liftu przez dłuższy okres.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("exercise_name") {
                    put("type", "string")
                    put("description", "Dokładna nazwa ćwiczenia (jak w bibliotece, np. 'Przysiad ze sztangą (back squat)')")
                }
                putJsonObject("weeks_back") {
                    put("type", "integer")
                    put("description", "Ile tygodni wstecz analizować (default 12)")
                }
            }
            putJsonArray("required") { add("exercise_name") }
        }
    }

    private fun toolGetBodyHistory(): JsonObject = buildJsonObject {
        put("name", "get_body_history")
        put("description", "Pełna historia pomiarów ciała (waga, obwody, body fat) chronologicznie. Użyj do analizy trendu redukcji/masy przez dłuższy okres.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("weeks_back") {
                    put("type", "integer")
                    put("description", "Ile tygodni wstecz pomiarów pobrać (np. 26 dla pół roku)")
                }
            }
            putJsonArray("required") { add("weeks_back") }
        }
    }
}
