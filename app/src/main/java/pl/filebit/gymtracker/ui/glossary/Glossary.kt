package pl.filebit.gymtracker.ui.glossary

/**
 * Słownik terminów i skrótów używanych w aplikacji.
 * Klucz = identyfikator (używany w InfoIcon), wartość = (tytuł, opis).
 */
object Glossary {
    private val ENTRIES: Map<String, Pair<String, String>> = mapOf(
        "1RM" to ("1RM (One-Rep Max)" to
            "Maksymalny ciężar jaki możesz podnieść na jedno powtórzenie. Aplikacja oblicza " +
            "szacowane 1RM wzorem Epleya:\n\n1RM ≈ waga × (1 + powt. / 30)\n\n" +
            "Np. 80 kg × 5 powt. → szacowane 1RM ≈ 93.3 kg. Pomocne do porównania siły między " +
            "różnymi schematami powt. (5×5 vs 8×3 vs 10×10) i do trackowania progresu."),

        "RPE" to ("RPE (Rate of Perceived Exertion)" to
            "Skala 1-10 trudności serii.\n\n" +
            "• RPE 6-7 = lekko, mogłeś jeszcze 3-4 powt.\n" +
            "• RPE 8 = trudno, mogłeś jeszcze 2 powt.\n" +
            "• RPE 9 = bardzo trudno, 1 powt. w zapasie\n" +
            "• RPE 10 = padłeś, ostatnie powt. ledwo\n\n" +
            "Aplikacja używa RPE do sugerowania następnej wagi: niski RPE → +kg, wysoki → utrzymaj."),

        "RIR" to ("RIR (Reps in Reserve)" to
            "Ile powtórzeń zostało Ci jeszcze w zapasie po zakończeniu serii.\n\n" +
            "• RIR 3 = mogłeś jeszcze 3 powt.\n• RIR 1 = jedno w zapasie\n• RIR 0 = padłeś\n\n" +
            "Odwrotność RPE: RIR = 10 - RPE."),

        "TEMPO" to ("Tempo treningu" to
            "Format 4-cyfrowy np. \"3-1-1-0\":\n\n" +
            "• 3s — faza ekscentryczna (opuszczanie)\n" +
            "• 1s — pauza w pozycji rozciągniętej\n" +
            "• 1s — faza koncentryczna (podnoszenie)\n" +
            "• 0s — pauza w pozycji skróconej\n\n" +
            "Wolniejsze tempo = większy time-under-tension i hipertrofia."),

        "AMRAP" to ("AMRAP" to
            "As Many Reps As Possible — \"tyle powtórzeń ile damy radę\".\n\n" +
            "Często ostatnia seria w treningu — wyciskasz aż do upadku, sprawdzasz ile powt. " +
            "się udało. Świetna miara progresu (więcej powt. tym samym ciężarem = postęp)."),

        "DROP_SET" to ("Drop Set" to
            "Po wykonaniu serii do upadku, natychmiast zmniejszasz ciężar (~20-30%) i kontynuujesz " +
            "do kolejnego upadku. Można powtórzyć 2-3 razy.\n\n" +
            "Przykład: 80 kg × 8 → 60 kg × 6 → 40 kg × 8.\n\n" +
            "Bardzo intensywny — głównie hipertrofia, używaj rzadko (raz na 1-2 tygodnie per partia)."),

        "WARMUP" to ("Warmup (rozgrzewka)" to
            "Seria rozgrzewkowa z lżejszym ciężarem przed seriami roboczymi.\n\n" +
            "W aplikacji oznaczona typem WARMUP — NIE liczy się do volume i NIE bierze udziału " +
            "w wykrywaniu PR ani sugestiach progresji."),

        "VOLUME" to ("Volume (objętość treningowa)" to
            "Suma kg × powt. ze wszystkich serii roboczych (bez warm-upów).\n\n" +
            "Np. 3 serie × 10 powt. × 80 kg = 2400 kg objętości.\n\n" +
            "Główny driver hipertrofii — utrzymanie/zwiększanie volume w czasie = wzrost. " +
            "Dla siły mniej istotne niż intensywność (% 1RM)."),

        "CARDIO" to ("Cardio — czas i prędkość" to
            "Ćwiczenia cardio (bieżnia, rower, orbitrek) mierzymy inaczej niż siłowe — " +
            "nie ciężarem i powtórzeniami, tylko CZASEM i PRĘDKOŚCIĄ.\n\n" +
            "• Wpisujesz czas (minuty) i prędkość (km/h) — to wartości które realnie " +
            "ustawiasz na maszynie\n" +
            "• Dystans liczy się sam: dystans = prędkość × czas\n" +
            "• Nie ma tu 1RM ani tonażu kg — cardio nie wchodzi do objętości siłowej " +
            "ani do rekordów osobistych\n\n" +
            "Progresja cardio = trenuj DŁUŻEJ albo SZYBCIEJ. Ćwiczenia izometryczne " +
            "(plank, deska) mierzymy samym czasem."),

        "DELOAD" to ("Deload (rozładowanie)" to
            "Tydzień zmniejszonego obciążenia (-10% do -20% wagi lub -30% objętości) co 4-8 " +
            "tygodni.\n\n" +
            "Cel: regeneracja CNS, zagojenie mikrouszkodzeń, reset progresu. Nie pomijaj — bez " +
            "deloadu szybciej wpadasz w stagnację i kontuzje."),

        "STAGNATION" to ("Stagnacja" to
            "Brak progresu wagi w danym ćwiczeniu przez 3+ kolejne treningi.\n\n" +
            "Aplikacja wykrywa to automatycznie (alert ⚠️ po zakończeniu treningu). Reakcje:\n" +
            "• deload tydzień\n" +
            "• zmiana zakresu powt. (np. z 5×5 na 3×8)\n" +
            "• zmiana ćwiczenia/wariantu\n" +
            "• audyt techniki, snu, kalorii"),

        "RECOVERY" to ("Regeneracja (recovery)" to
            "Karta pokazuje, ile dni minęło od ostatniego treningu danej partii mięśniowej.\n\n" +
            "Liczone na podstawie ukończonych serii (bez warm-upów). Dla każdej grupy bierze datę " +
            "najnowszego treningu, który ją obciążył.\n\n" +
            "Statusy:\n" +
            "• ŚWIEŻO (0–1 dni) — mięsień świeżo trenowany, regeneruje się\n" +
            "• GOTOWE (2–6 dni) — odpoczął, można trenować\n" +
            "• POTRENUJ (7–13 dni) — czas wrócić\n" +
            "• ⚠ ZANIEDBANA (14+ dni) — pilnie potrenować\n\n" +
            "Praktyczna heurystyka: większe partie (klatka, plecy, nogi) lubią 48-72h przerwy, " +
            "mniejsze (biceps, biceps uda) regenerują się szybciej."),

        "STREAK" to ("Streak (passa)" to
            "Liczba tygodni z rzędu z minimum 1 ukończonym treningiem.\n\n" +
            "Aplikacja używa kalendarza ISO (tydzień Pon-Nd). Bieżący streak resetuje się jeśli " +
            "minie cały tydzień bez treningu."),

        "BW_RATIO" to ("Ratio × BW (wielokrotność wagi ciała)" to
            "Stosunek 1RM do wagi ciała. Standardowy benchmark siły, niezależny od masy.\n\n" +
            "Przykłady (mężczyźni, intermediate):\n" +
            "• Bench press: 1.25× BW (90 kg dla 72 kg)\n" +
            "• Squat: 1.75× BW\n" +
            "• Deadlift: 2.0× BW\n\n" +
            "Aplikacja klasyfikuje na 5 poziomów (BEGINNER → ELITE)."),

        "PR" to ("PR (Personal Record)" to
            "Twój rekord osobisty w danym ćwiczeniu — najwyższe szacowane 1RM.\n\n" +
            "Aplikacja wyświetla 🏆 dialog po zakończeniu treningu gdy pobijesz PR. Pokazuje " +
            "się tylko gdy szacowane 1RM jest WYŻSZE niż wszystko z poprzednich ukończonych " +
            "treningów."),

        "SUPERSET" to ("Superseria (A1/A2)" to
            "2 (lub więcej) ćwiczenia wykonane jedno po drugim BEZ odpoczynku, dopiero " +
            "potem przerwa.\n\n" +
            "Przykład: A1 ławka 8 powt. → A2 wiosłowanie 8 powt. → 90s pauza → powtórz.\n\n" +
            "Oszczędność czasu, większa pompa, dla antagonistów (klatka+plecy) lub agonistów " +
            "(klatka+barki). W aplikacji ćwiczenia oznaczone tą samą literą grupy są w superserii."),

        "GOAL_TYPE" to ("Typy celów treningowych" to
            "• **STRENGTH (siła)** — 1-6 powt., ciężko, pauzy 3-5 min\n" +
            "• **HYPERTROPHY (masa)** — 6-12 powt., średnio, pauzy 60-120s\n" +
            "• **MIX (siła + masa)** — kombinacja\n" +
            "• **GENERAL_FITNESS** — sprawność ogólna, krążenie\n" +
            "• **CARDIO_LIFTING** — siła + cardio (HIIT, circuits)\n\n" +
            "Aplikacja używa celu do sugerowania progresji (siła = +2.5kg, masa = +1.25kg)."),

        "SET_TYPE" to ("Typy serii" to
            "• **NORMAL** — robocza seria, liczona do volume i PR\n" +
            "• **WARMUP** — rozgrzewkowa, pomijana w stats\n" +
            "• **DROP** — drop set (po sukcesie zmniejszasz ciężar)\n" +
            "• **FAILURE** — do upadku mięśniowego\n" +
            "• **AMRAP** — As Many Reps As Possible"),

        "EPLEY" to ("Wzór Epleya" to
            "Najczęściej używany wzór do szacowania 1RM:\n\n" +
            "1RM = waga × (1 + powt. / 30)\n\n" +
            "Sprawdza się dobrze do ~10 powt. — powyżej tego niedoszacowuje. Dla wysokich " +
            "powt. lepszy Brzycki."),

        // v1.19.0 — terminy periodyzacji
        "MESOCYCLE" to ("Mesocykl (4-6 tygodni)" to
            "Okres treningowy z jasnym celem — akumulacja, intensyfikacja, deload lub peaking. " +
            "Klasyczna struktura periodyzacji liniowej:\n\n" +
            "• Akumulacja 2-4 tyg → Intensyfikacja 2-4 tyg → Deload 1 tydz\n\n" +
            "Wiele mesocykli składa się w makrocykl (sezon 12-24 tyg). Aplikacja śledzi aktywny " +
            "mesocykl na karcie 'Faza cyklu' i 'Plan cyklu'."),

        "MICROCYCLE" to ("Mikrocykl (tydzień)" to
            "Pojedynczy tydzień treningowy z 3-5 sesjami. Najmniejsza jednostka planowania.\n\n" +
            "W ramach jednego mezocyklu wszystkie mikrocykle mają podobną intensywność i objętość " +
            "z progresją tydzień-do-tygodnia (np. +2.5kg w głównych liftach)."),

        "ACCUMULATION" to ("Akumulacja (budowanie objętości)" to
            "Faza mesocyklu nastawiona na hipertrofię i wytrzymałość mięśniową.\n\n" +
            "• Powt: 8-12 (czasem 15-20)\n" +
            "• RPE: 6-8 (zostawiasz 2-4 powt. w zapasie)\n" +
            "• Pauzy: 60-90s\n" +
            "• Sets: więcej niż w intensyfikacji (więcej objętości całkowitej)\n\n" +
            "Plateau po 4 tyg jest normalne — to sygnał do przejścia w intensyfikację lub deload."),

        "INTENSIFICATION" to ("Intensyfikacja (budowanie siły)" to
            "Faza mesocyklu nastawiona na max siłę i 1RM.\n\n" +
            "• Powt: 3-6 (czasem 1-2)\n" +
            "• RPE: 8-9 (1-2 powt. w zapasie)\n" +
            "• Pauzy: 3-5 minut (pełna regeneracja)\n" +
            "• Sets: mniej niż w akumulacji, ale cięższe ciężary\n\n" +
            "Naturalne kontynuowanie po akumulacji — wykorzystujesz zbudowaną bazę objętościową."),

        "PEAKING" to ("Peaking (faza testowa)" to
            "Krótka faza (1-2 tyg) przed sprawdzaniem 1RM lub zawodami.\n\n" +
            "• Powt: 1-3, RPE 9-10\n" +
            "• Pauzy: bardzo długie (3-8 min)\n" +
            "• Volume: niski (np. 50-60% normalnego)\n\n" +
            "Cel: pełne odświeżenie CNS, super-kompensacja, sprawdzenie progresji z poprzednich " +
            "8-12 tyg pracy. Po peakingu zazwyczaj recovery lub nowy mesocykl."),

        "ACWR" to ("ACWR (Acute:Chronic Workload Ratio)" to
            "Stosunek objętości 7-dniowej (acute) do średniej 28-dniowej (chronic).\n\n" +
            "ACWR = volume_7d / avg_volume_28d\n\n" +
            "• <0.8 — undertraining (ryzyko detraining)\n" +
            "• 0.8-1.3 — sweet spot (optymalne obciążenie)\n" +
            "• 1.3-1.5 — overreaching (uważaj)\n" +
            "• >1.5 — wysokie ryzyko kontuzji\n\n" +
            "Aplikacja pokazuje ACWR w karcie 'Obciążenie' i alertuje gdy wskaźnik wpada w strefę " +
            "ryzyka. Bazuje na badaniach Tim Gabbett (sport science)."),

        "WILKS" to ("Wilks Score" to
            "Formuła znormalizowana do porównywania siły między różnymi wagami ciała w trójboju " +
            "siłowym. Im wyższy score, tym większa relatywna siła.\n\n" +
            "Wilks = total_kg × współczynnik(bw, gender)\n\n" +
            "Pozwala uczciwie porównać atletów: 200kg total przy 75kg vs 250kg total przy 110kg. " +
            "Wartości referencyjne: 300+ początkujący, 400+ średnio-zaawansowany, 500+ zawodnik.")
    )

    fun get(key: String): Pair<String, String>? = ENTRIES[key]

    fun all(): List<Pair<String, Pair<String, String>>> = ENTRIES.toList()
}
