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
        // v1.18.0 — rozszerzone tools periodyzacyjne
        add(toolProposeDeload())
        add(toolTransitionPhase())
        add(toolScheduleNextCycle())
        add(toolGetPendingDecisions())
        // v2.0.0 — canonical exercise-db tools
        add(toolFindExercisesByCriteria())
        add(toolGetExerciseAlternatives())
        add(toolGetExerciseProgression())
        add(toolGetExercisePrerequisites())
        add(toolFindSafeExercisesForUser())
        // v2.22.0 — narzędzia ZAPISU (AI zmienia dane na prośbę usera). Tylko dane,
        // nigdy kod/zachowanie. Bez kasowania. Walidacja + audyt w handlerze.
        add(toolLogWeight())
        add(toolGetMeals())       // v2.37.0 — AI widzi posiłki dnia
        add(toolAddMeal())
        add(toolSaveDietPlan())   // v2.72.0 — atomowy zapis CAŁEGO planu dnia (1 wywołanie)
        add(toolDeleteMeal())     // v2.37.0 — AI podmienia/usuwa posiłek
        add(toolSetCalorieTarget())
        add(toolSetDietGoal())
        add(toolRecordTrainingPause())  // v2.59.0
    }

    /** Lista nazw narzędzi (do walidacji w handlerze). */
    val TOOL_NAMES = setOf(
        "get_workouts",
        "get_events",
        "get_rollups",
        "get_exercise_history",
        "get_body_history",
        "propose_periodization_action",  // v1.15.0
        // v1.18.0
        "propose_deload",
        "transition_phase",
        "schedule_next_cycle",
        "get_pending_decisions",
        // v2.0.0 — canonical
        "find_exercises_by_criteria",
        "get_exercise_alternatives",
        "get_exercise_progression",
        "get_exercise_prerequisites",
        "find_safe_exercises_for_user",
        // v2.22.0 — zapis danych
        "log_weight",
        "add_meal",
        "save_diet_plan",                // v2.72.0 — zapis całego planu dnia jednym wywołaniem
        "set_calorie_target",
        "set_diet_goal",
        // v2.37.0 — odczyt + podmiana posiłków (AI widzi i zmienia kolację)
        "get_meals",
        "delete_meal",
        // v2.59.0 (U9+U10) — zapamiętanie przyczyny przerwy w treningach
        "record_training_pause"
    )

    // === v2.22.0: narzędzia ZAPISU danych (na prośbę usera) ===

    private fun toolLogWeight(): JsonObject = buildJsonObject {
        put("name", "log_weight")
        put("description", "Zapisuje pomiar wagi użytkownika. Użyj gdy user prosi np. 'zapisz wagę 85', 'dziś ważę 84.5 kg'.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("kg") { put("type", "number"); put("description", "Waga w kg (30-300)") }
                putJsonObject("date") { put("type", "string"); put("description", "Data YYYY-MM-DD (opcjonalnie, domyślnie dziś)") }
            }
            putJsonArray("required") { add("kg") }
        }
    }

    private fun toolGetMeals(): JsonObject = buildJsonObject {
        put("name", "get_meals")
        put("description", "Zwraca posiłki dnia (id wpisu, posiłek, produkt, gramy, kcal, białko). UŻYJ ZAWSZE zanim podmienisz/usuniesz posiłek — żeby poznać 'meal_entry_id' aktualnej np. kolacji.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("date") { put("type", "string"); put("description", "Data YYYY-MM-DD (opcjonalnie, domyślnie dziś)") }
            }
            putJsonArray("required") {}
        }
    }

    private fun toolDeleteMeal(): JsonObject = buildJsonObject {
        put("name", "delete_meal")
        put("description", "Usuwa wpis posiłku po 'meal_entry_id' (poznaj go z get_meals). PODMIANA = delete_meal starego + add_meal nowego. Użyj gdy user prosi 'zmień/podmień kolację'.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("meal_entry_id") { put("type", "integer"); put("description", "ID wpisu z get_meals") }
                putJsonObject("date") { put("type", "string"); put("description", "Data YYYY-MM-DD (opcjonalnie, domyślnie dziś)") }
            }
            putJsonArray("required") { add("meal_entry_id") }
        }
    }

    private fun toolAddMeal(): JsonObject = buildJsonObject {
        put("name", "add_meal")
        put("description", "Dodaje produkt do dziennika posiłków. Użyj gdy user prosi np. 'dodaj 200g kurczaka do obiadu'. Produkt musi istnieć w bazie produktów.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("product") { put("type", "string"); put("description", "Nazwa produktu (musi pasować do bazy produktów)") }
                putJsonObject("grams") { put("type", "number"); put("description", "Gramatura (1-2000)") }
                putJsonObject("mealSlot") { put("type", "integer"); put("description", "Numer posiłku 1..N (Posiłek N). Jeśli pominięte — wyliczany z mealType.") }
                putJsonObject("mealType") { put("type", "string"); put("description", "BREAKFAST/LUNCH/DINNER/SNACK (domyślnie LUNCH) — używane gdy brak mealSlot") }
                putJsonObject("date") { put("type", "string"); put("description", "Data YYYY-MM-DD (opcjonalnie, domyślnie dziś)") }
            }
            putJsonArray("required") { add("product"); add("grams") }
        }
    }

    private fun toolSaveDietPlan(): JsonObject = buildJsonObject {
        put("name", "save_diet_plan")
        put("description", "Zapisuje CAŁY plan dnia JEDNYM wywołaniem (atomowo zastępuje posiłki dnia). " +
            "Użyj GDY user poda gotowy plan diety (np. ze zdjęcia lub listę kilku posiłków naraz) — " +
            "NIE wywołuj add_meal po jednym składniku (to przekracza limity i psuje zapis). " +
            "Posiłki w kolejności = Posiłek 1..N. Produkty muszą istnieć w bazie produktów (brakujące zostaną pominięte i zgłoszone w wyniku). " +
            "Ustawia też liczbę posiłków dnia na liczbę przekazanych posiłków.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("date") { put("type", "string"); put("description", "Data YYYY-MM-DD (opcjonalnie, domyślnie dziś)") }
                putJsonObject("meals") {
                    put("type", "array")
                    put("description", "Lista posiłków W KOLEJNOŚCI (1..N). Każdy posiłek = nazwa + składniki.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string"); put("description", "Nazwa dania (opcjonalnie, np. 'Owsianka z bananem')") }
                            putJsonObject("mealType") { put("type", "string"); put("description", "BREAKFAST/LUNCH/DINNER/SNACK (opcjonalnie — domyślnie wyliczane z kolejności)") }
                            putJsonObject("ingredients") {
                                put("type", "array")
                                put("description", "Składniki posiłku (produkt + gramatura)")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("product") { put("type", "string"); put("description", "Nazwa produktu (musi pasować do bazy produktów)") }
                                        putJsonObject("grams") { put("type", "number"); put("description", "Gramatura (1-2000)") }
                                    }
                                    putJsonArray("required") { add("product"); add("grams") }
                                }
                            }
                        }
                        putJsonArray("required") { add("ingredients") }
                    }
                }
            }
            putJsonArray("required") { add("meals") }
        }
    }

    private fun toolRecordTrainingPause(): JsonObject = buildJsonObject {
        put("name", "record_training_pause")
        put("description", "Zapamiętuje PRZYCZYNĘ przerwy w treningach gdy user wyjaśni dlaczego nie ćwiczy " +
            "(np. 'nie mam jak, wrócę za tydzień', 'kontuzja kolana'). Dzięki temu trener przestaje nagabywać " +
            "o trening do umówionego terminu i chroni mięśnie dietą. Użyj GDY user poda powód braku treningu.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("reason") { put("type", "string"); put("description", "NO_TIME / NO_ACCESS / INJURY / OTHER") }
                putJsonObject("note") { put("type", "string"); put("description", "Krótka notatka własnymi słowami usera (opcjonalnie)") }
                putJsonObject("resume_in_days") { put("type", "integer"); put("description", "Za ile dni wrócić do tematu (domyślnie 7, kontuzja 14)") }
            }
            putJsonArray("required") { add("reason") }
        }
    }

    private fun toolSetCalorieTarget(): JsonObject = buildJsonObject {
        put("name", "set_calorie_target")
        put("description", "Ustawia ręczny dzienny cel kaloryczny. Użyj gdy user prosi np. 'ustaw cel 2000 kcal'.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("kcal") { put("type", "integer"); put("description", "Cel kcal/dzień (800-6000)") }
            }
            putJsonArray("required") { add("kcal") }
        }
    }

    private fun toolSetDietGoal(): JsonObject = buildJsonObject {
        put("name", "set_diet_goal")
        put("description", "Zmienia kierunek celu diety. Użyj gdy user prosi np. 'przejdź na masę', 'chcę robić redukcję'.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("goal") { put("type", "string"); put("description", "CUT (redukcja) / BULK (masa) / MAINTAIN (utrzymanie)") }
            }
            putJsonArray("required") { add("goal") }
        }
    }

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

    // === v1.18.0 — rozszerzone tools periodyzacyjne ===

    /**
     * Propose deload — konkretna konfiguracja: data startu, długość, redukcja volume/intensity.
     * Tool RO — zapisuje PendingPeriodizationDecision (status=PENDING), user akceptuje przez UI.
     */
    private fun toolProposeDeload(): JsonObject = buildJsonObject {
        put("name", "propose_deload")
        put("description", """
            Zaproponuj konkretny tydzień deloadu dla użytkownika. Tool zapisuje propozycję jako
            PendingPeriodizationDecision (status=PENDING) — user explicit akceptuje przez UI
            ("AI TRENER PROPONUJE" karta na Home). NIE wykonuje akcji bezpośrednio.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("reason") {
                    put("type", "string")
                    put("description", "Powód deloadu: high_acwr / stagnation / low_recovery / max_weeks_without_deload / user_request")
                }
                putJsonObject("start_date") {
                    put("type", "string")
                    put("description", "Data startu deloadu YYYY-MM-DD")
                }
                putJsonObject("duration_days") {
                    put("type", "integer")
                    put("description", "Długość deloadu w dniach (4-14, typowo 7)")
                }
                putJsonObject("volume_reduction_pct") {
                    put("type", "number")
                    put("description", "Redukcja objętości jako frakcja 0.2-0.5 (np. 0.4 = -40%)")
                }
                putJsonObject("intensity_reduction_pct") {
                    put("type", "number")
                    put("description", "Redukcja intensywności jako frakcja 0.0-0.2 (np. 0.10 = -10% ciężaru). Opcjonalne.")
                }
                putJsonObject("reasoning") {
                    put("type", "string")
                    put("description", "Uzasadnienie dla usera po polsku (cytuj liczby z kontekstu).")
                }
            }
            putJsonArray("required") {
                add("reason")
                add("start_date")
                add("duration_days")
                add("volume_reduction_pct")
                add("reasoning")
            }
        }
    }

    /**
     * Transition phase — przejście do następnej fazy mesocyklu.
     * Tool RO — zapisuje PendingPeriodizationDecision.
     */
    private fun toolTransitionPhase(): JsonObject = buildJsonObject {
        put("name", "transition_phase")
        put("description", """
            Zaproponuj przejście do nowej fazy mesocyklu (np. po deloadzie → akumulacja).
            Zapisuje propozycję jako PendingPeriodizationDecision. User akceptuje przez UI.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("from_phase") {
                    put("type", "string")
                    put("description", "Obecna faza: ACCUMULATION / INTENSIFICATION / DELOAD / PEAKING / RECOVERY")
                }
                putJsonObject("to_phase") {
                    put("type", "string")
                    put("description", "Docelowa faza: ACCUMULATION / INTENSIFICATION / DELOAD / PEAKING / RECOVERY")
                }
                putJsonObject("start_date") {
                    put("type", "string")
                    put("description", "Data startu nowej fazy YYYY-MM-DD")
                }
                putJsonObject("duration_weeks") {
                    put("type", "integer")
                    put("description", "Długość nowej fazy w tygodniach (1-6)")
                }
                putJsonObject("confidence") {
                    put("type", "number")
                    put("description", "Pewność 0.0-1.0")
                }
                putJsonObject("reasoning") {
                    put("type", "string")
                    put("description", "Uzasadnienie po polsku")
                }
            }
            putJsonArray("required") {
                add("from_phase"); add("to_phase"); add("start_date"); add("duration_weeks")
                add("confidence"); add("reasoning")
            }
        }
    }

    /**
     * Schedule next cycle — kompletny plan 4-12 tyg z fazami sekwencyjnie.
     */
    private fun toolScheduleNextCycle(): JsonObject = buildJsonObject {
        put("name", "schedule_next_cycle")
        put("description", """
            Zaplanuj cały następny cykl 4-12 tygodni z fazami (akumulacja → intensyfikacja → deload).
            Tworzy listę PendingPeriodizationDecision (po jednej per faza) ze status=PENDING.
            User widzi propozycję na Home i akceptuje całość lub odrzuca.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("start_date") {
                    put("type", "string")
                    put("description", "Data startu pierwszej fazy YYYY-MM-DD")
                }
                putJsonObject("phases") {
                    put("type", "array")
                    put("description", "Lista faz w kolejności. Każda: {phase, duration_weeks}. Min 2, max 5.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("phase") {
                                put("type", "string")
                                put("description", "ACCUMULATION / INTENSIFICATION / DELOAD / PEAKING / RECOVERY")
                            }
                            putJsonObject("duration_weeks") {
                                put("type", "integer")
                                put("description", "Długość fazy 1-6")
                            }
                        }
                        putJsonArray("required") { add("phase"); add("duration_weeks") }
                    }
                }
                putJsonObject("goal") {
                    put("type", "string")
                    put("description", "Cel cyklu: HYPERTROPHY / STRENGTH / PEAK / RECOVERY (do uzasadnienia)")
                }
                putJsonObject("reasoning") {
                    put("type", "string")
                    put("description", "Uzasadnienie planu po polsku — dlaczego ta sekwencja/długości")
                }
            }
            putJsonArray("required") { add("start_date"); add("phases"); add("goal"); add("reasoning") }
        }
    }

    /**
     * Get pending decisions — pobierz wszystkie oczekujące propozycje (status=PENDING).
     * Read-only — AI może sprawdzić co już zaproponowano przed nową propozycją.
     */
    private fun toolGetPendingDecisions(): JsonObject = buildJsonObject {
        put("name", "get_pending_decisions")
        put("description", """
            Pobierz wszystkie oczekujące propozycje periodyzacji (PendingPeriodizationDecision
            ze status=PENDING). Użyj zanim utworzysz nową propozycję — żeby nie duplikować.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {}
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

    // ============================================================
    // v2.0.0 — CANONICAL EXERCISE-DB TOOLS
    // ============================================================

    private fun toolFindExercisesByCriteria(): JsonObject = buildJsonObject {
        put("name", "find_exercises_by_criteria")
        put("description", """
            Wyszukaj ćwiczenia z canonical exercise-db po kryteriach.
            Użyj gdy planujesz trening lub szukasz alternatyw — np. "ćwiczenia push horizontal dla intermediate
            bez przeciwwskazań na ból dolnych pleców z dostępem do hantli". Zwraca listę slugów + namePl + krótki
            opis. Maksymalnie 30 wyników.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("movement_pattern") {
                    put("type", "string")
                    put("description", "Wzorzec ruchowy: SQUAT, HINGE, LUNGE, PUSH_HORIZONTAL, PUSH_VERTICAL, PULL_HORIZONTAL, PULL_VERTICAL, CARRY, ROTATION, CORE_FLEXION, CORE_EXTENSION, ANTI_EXTENSION, ANTI_ROTATION, ANTI_LATERAL_FLEXION, JUMP, GAIT")
                }
                putJsonObject("level_max") {
                    put("type", "string")
                    put("description", "Maksymalny poziom min do filtrowania: BEGINNER (tylko początkujące), INTERMEDIATE (≤średnio), ADVANCED, ELITE")
                }
                putJsonObject("primary_muscle") {
                    put("type", "string")
                    put("description", "Główna grupa mięśniowa (enum): CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, CARDIO, OTHER")
                }
                putJsonObject("equipment") {
                    put("type", "string")
                    put("description", "Sprzęt (enum): BARBELL, DUMBBELLS, MACHINE, CABLE, BODYWEIGHT, OTHER")
                }
                putJsonObject("exclude_contraindications") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Lista przeciwwskazań do wykluczenia (np. ['acute_lower_back_pain', 'shoulder_impingement']). Ćwiczenia mające te kontraindications nie zostaną zwrócone.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max liczba wyników (1-30, default 10)")
                }
            }
        }
    }

    private fun toolGetExerciseAlternatives(): JsonObject = buildJsonObject {
        put("name", "get_exercise_alternatives")
        put("description", """
            Zwraca canonical alternatives dla ćwiczenia po slug. Używaj gdy user nie może zrobić ćwiczenia
            (kontuzja, brak sprzętu, monotonia) i potrzebujesz zamiennika. Zwraca listę slugów + nazwy PL.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("slug") {
                    put("type", "string")
                    put("description", "Canonical slug ćwiczenia (np. 'barbell-back-squat')")
                }
            }
            putJsonArray("required") { add("slug") }
        }
    }

    private fun toolGetExerciseProgression(): JsonObject = buildJsonObject {
        put("name", "get_exercise_progression")
        put("description", """
            Zwraca cięższe wersje (progression_to) dla ćwiczenia. Użyj gdy user ma stagnację lub gotów na
            harder variation. Np. progresja "push-up" → "decline-push-up", "weighted-push-up", "one-arm-push-up".
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("slug") {
                    put("type", "string")
                    put("description", "Canonical slug ćwiczenia")
                }
            }
            putJsonArray("required") { add("slug") }
        }
    }

    private fun toolGetExercisePrerequisites(): JsonObject = buildJsonObject {
        put("name", "get_exercise_prerequisites")
        put("description", """
            Zwraca ćwiczenia, które user powinien opanować PRZED danym ćwiczeniem (prerequisites).
            Np. dla "muscle-up" prerequisites = ['pull-up', 'triceps-dip']. Użyj zanim zaproponujesz
            zaawansowane ćwiczenie — sprawdź czy user ma fundamenty.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("slug") {
                    put("type", "string")
                    put("description", "Canonical slug ćwiczenia (zaawansowanego)")
                }
            }
            putJsonArray("required") { add("slug") }
        }
    }

    private fun toolFindSafeExercisesForUser(): JsonObject = buildJsonObject {
        put("name", "find_safe_exercises_for_user")
        put("description", """
            Sprawdza listę ćwiczeń względem przeciwwskazań usera (z UserProfile.medicalConditions).
            Zwraca dla każdego ćwiczenia: czy jest safe / wymaga uwagi (caution) / odradzane (avoid),
            wraz z modification cue z canonical contraindications. Niezbędne PRZED zaproponowaniem
            planu lub konkretnego ćwiczenia.
        """.trimIndent())
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("slugs_to_check") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Lista canonical slugów ćwiczeń do sprawdzenia (max 20)")
                }
                putJsonObject("user_medical_conditions") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Lista warunków medycznych usera (np. ['lower_back_pain', 'shoulder_impingement']). Aplikacja może też wyciągnąć z UserProfile automatycznie jeśli puste.")
                }
            }
            putJsonArray("required") { add("slugs_to_check") }
        }
    }
}
