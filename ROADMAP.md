# GymTracker — Roadmap

## Zasady projektu

- Aplikacja **całkowicie darmowa**, bez subskrypcji, bez płatności, bez reklam.
- **Tylko Android** (smartfon / tablet). Bez Wear OS, bez Apple Watch, bez iOS.
- **Offline-first** — wszystko lokalnie (Room DB, plik JSON/CSV w Dysku Google przez systemowy picker).
- **Bez backendu** w MVP — żadnych serwerów, żadnego API z danymi user'a.
- **Open source** styl — repo prywatne, ale kod ma być czysty i utrzymywalny.
- **Bez telemetrii** — żadnego trackingu, żadnego Analytics.
- Funkcje wymagające zewnętrznego API (np. AI generator) — opcjonalne, model **BYOK** (Bring Your Own Key) jeśli w ogóle.

---

## ✅ V0 — MVP zrobione (v0.1.0 → v0.8.0)

- [x] Trening ad-hoc (lista ćwiczeń, dodaj serię, ✓ wykonana, edycja w locie)
- [x] Plany własne (tworzenie / edycja / usuwanie)
- [x] **Per-day exercises** w planie (różne ćwiczenia per Pon/Wt/.../Nd)
- [x] **Per-set parametry** w planie (każda seria osobno: powt / waga / odp.)
- [x] Coach mode (wizard: 1 seria na raz, dialog "wykonałeś X powt?", auto-advance)
- [x] Rest timer (foreground service, **ToneGenerator + STREAM_ALARM**, wibracja)
- [x] Save as plan (z WorkoutDetail i ActiveWorkout — z agregacjami)
- [x] Repeat workout = "Powtórz trening" *(częściowo: przez Save as plan + Start)*
- [x] Backup JSON (Profil → Backup, Eksport / Import)
- [x] Profil (cel, doświadczenie, jednostki, dni, czas sesji, default rest)
- [x] Bottom nav 5-tab: Home / Historia / Plany / Ćwiczenia / Profil
- [x] Home hub: 2 kafelki Plan/Ad-hoc + dziś plan
- [x] Crash logger (Backup → "Skopiuj log crashu")
- [x] Day picker po Start z Plany
- [x] Workout origin label w historii ("Z planu: X · Środa")

---

## 🔴 V1 — kluczowe braki dla codziennego użytku

### Faza 12 — Wartość operacyjna ✅ v0.9.0

- [x] **Typy serii** — `SetType` enum (NORMAL/WARMUP/DROP/FAILURE/AMRAP) w `WorkoutSet` + `PlanExerciseSet`. UI chips per-set zostawione na Fazę 12.1 (najpierw zapis w bazie, potem pełna prezentacja).
- [x] **"Ostatnio: X kg × Y"** — już istnieje w `ExerciseGroupCard` (linijka pod tytułem ćwiczenia) i w Coach mode
- [x] **Auto-fill "Dodaj serię"** — `+ Dodaj serię` w Active workout kopiuje wartości z poprzedniej serii (już istnieje w `ExerciseGroupCard.onAddSet`)
- [x] **Notatki per-trening** — IconButton 📝 w TopAppBar Active/Coach, dialog z `OutlinedTextField` multiline, zapis do `Workout.notes`
- [x] **"Powtórz trening"** w WorkoutDetail (overflow menu) — `repeatWorkout` tworzy nowy aktywny trening z placeholder-serie skopiowanymi z oryginału, nawigacja do ActiveWorkout

### Faza 13 — Statystyki & PR ✅ v0.10.0

- [x] **Statystyki overview** w Profilu (Profil → Statystyki): liczba treningów, łączna objętość (bez warm-upów), łączny czas, średnia objętość/trening, treningi w tym tygodniu/miesiącu, łączna liczba serii
- [x] **PR per ćwiczenie** w bibliotece — max waga × powt + szacowane 1RM (Epley) wyświetlane pod kartą ćwiczenia gdy historia istnieje
- [x] **🏆 "Nowy rekord!"** dialog po zakończeniu treningu — porównanie szacowanego 1RM z najlepszym z poprzednich treningów (per ćwiczenie); alert pokazuje nazwę ćwiczenia + wagę × powt + nowe 1RM
- [x] **1RM calculator** w Profilu (Profil → Kalkulator 1RM): pola Waga + Powt → wyniki Epley / Brzycki / Lombardi + średnia trzech

### Faza 14 — Tools ✅ v0.11.0

- [x] **Plate calculator** — osobny ekran w Profilu, greedy load talerzami 25/20/15/10/5/2.5/1.25 kg, oblicz różnicę do celu
- [x] **Filtr "Sprzęt"** w bibliotece — drugi rząd chipów pod filtrami mięśni (Sztanga, Hantle, Maszyna, Wyciąg, Ciężar ciała)
- [x] **Eksport CSV** w Backup — `workout_id, started_at, finished_at, exercise, set_number, set_type, reps, weight_kg, is_completed` (jeden wiersz per WorkoutSet)
- [x] **Notatki per-ćwiczenie** — klik na kartę ćwiczenia w bibliotece otwiera dialog edycji `Exercise.notes`, notatka wyświetla się jako 📝 pod kartą (per WorkoutSet zostaje na osobną fazę jeśli będzie potrzebne)

---

## 🟡 V2 — premium feel (lokalne funkcje, bez backendu)

### Faza 15 — Wykresy & analityka ✅ v0.12.0 (częściowo)

- [x] **Wykres progresji per ćwiczenie** — ExerciseDetailScreen z Compose Canvas linechart (max waga w czasie). Klik na kartę ćwiczenia w bibliotece → szczegóły z PR / wykresem / notatką (edytowalną) / pełną historią setów.
- [x] **Sugestia progresji** — `progressionTipsForWorkout` w StatsRepository + UI w finish dialog (v0.18.0). Po Zakończ trening dialog pokazuje 🏆 nowe rekordy + 💡 sugestie progresji w jednym widoku.
- [ ] Alert stagnacji (brak progresu przez 3-4 treningi → sugestia deload) — V2.1

### Faza 16 — Plany zaawansowane ✅ v0.13.0 (częściowo)

- [x] **Gotowe szablony planów** — 4 wbudowane (Full Body 3×/tydzień, Stronglifts 5×5, Upper/Lower, Push/Pull/Legs). FAB → "Z gotowego szablonu" → wybierz → PlanEdit prefilled.
- [x] **Duplikuj plan** — overflow menu na karcie planu (3 kropki) → Duplikuj. Tworzy kopię z " (kopia)" w nazwie.
- [x] **Reorder ćwiczeń** (strzałki ↑↓ w karcie) ✅ v0.20.0 — strzałki góra/dół przy każdym ćwiczeniu w PlanEdit. Pełen drag & drop biblioteczny — opcjonalnie później jeśli ten okaże się niewystarczający
- [x] **Superserie A1/A2** ✅ v0.21.0 — `PlanExercise.supersetGroup: String?` (DB v=9). PlanEdit: button 🔗/🔗⃠ (link/unlink) toggle "z superserii z poprzednim". Karta zmienia kolor na tertiary container gdy w grupie. Label A1/A2/B1 obok nazwy. Coach mode obsługuje superserie naturalnie (kolejne sety w listę), specjalna logika krótszego odpoczynku w grupie — V2.2 jeśli będzie potrzeba.

Bonus dodany: **usuwanie planu** też w overflow menu (kropki) — wcześniej tylko z PlanEdit.

### Faza 17 — Streaki & motywacja ✅ v0.14.0

- [x] **Streak tygodniowy** — current + best (kalendarz ISO weeks). W StatsScreen kafelek 'tertiary container' gdy current > 0.
- [x] **Cel tygodnia** — postęp X/Y treningów + progressbar. Target z UserProfile.daysPerWeek.
- [x] **Odznaki** — 9 wbudowanych: workouts (10/50/100/250), volume (100k/500k), streak (4/12/52). Grid 2 kolumny, zablokowane pokazują progressbar postępu.
- [ ] Powiadomienia o niedokończonym treningu — V2.1 (wymaga WorkManager + POST_NOTIFICATIONS)

### Faza 18 — Tempo / RPE / RIR ✅ v0.15.0 (częściowo)

- [x] **Pole RPE** (1-10) — w WorkoutSet (od poprzedniej fazy) i PlanExerciseSet
- [x] **Pole RIR** (reps in reserve) — w WorkoutSet i PlanExerciseSet
- [x] **Tempo** — String? (np. "3-1-1-0") w WorkoutSet i PlanExerciseSet
- [x] **Toggle 'Pokaż pola zaawansowane'** w Profilu (UserProfile.showAdvancedSetFields)
- [x] **UI pól w PlanEdit** — drugi rząd RPE / RIR / Tempo per set gdy toggle ON
- [x] **UI pól w ActiveWorkout** ✅ v0.19.0 — drugi rząd RPE / RIR / Tempo pod każdą serią gdy `showAdvancedSetFields=true`
- [x] **UI w Coach mode confirm dialog** ✅ v0.19.0 — drugi slider RPE 1-10 (opcjonalny, 0 = pomiń) obok slidera reps

### Faza 19 — Body & pomiary ✅ v0.16.0 (częściowo)

- [x] **Body measurements** — encja `BodyMeasurement` (waga, klatka, pas, biodra, ramię, udo, łydka, BF%, notatki). Profil → 'Pomiary ciała' → ekran z FAB do dodawania
- [x] **Wykres masy ciała w czasie** — Compose Canvas linechart, pokazuje się gdy ≥2 pomiary z wagą
- [ ] Zdjęcia progresu (przód/bok/tył) — V2.1 (wymaga camera permissions + ImagePicker + filesystem)
- [ ] Cel masy (redukcja / masa / utrzymanie) — V2.1

### Faza 20 — Mapa mięśni ✅ v0.17.0 (lista, bez sylwetki)

- [x] **Procent zaangażowania per grupa mięśniowa** — `StatsRepository.muscleEngagement(periodDays)` agreguje volume kg × powt z working sets per `Exercise.primaryMuscle`. UI w Profil → 'Mapa mięśni': lista grup posortowana po wolumenie + progressbar procentowy + 4 zakresy czasu (tydzień / miesiąc / 3 miesiące / cały okres).
- [ ] Silhouette ciała graficzna (przód/tył) — V2.1 (custom Compose Canvas drawing, dużo grafiki)

---

## 🟢 V3 — FUTURE (wymaga backendu / dużego nakładu)

- [ ] **AI workout generator** — model BYOK (user wkleja klucz Anthropic/OpenAI w ustawieniach), generuje plan na podstawie celu+sprzętu+dni
- [ ] **AI sugestia kolejnego treningu** — analiza historii przez LLM
- [ ] **Cloud sync** — opcjonalnie przez Firebase/Supabase (osobne konto darmowe lub samodzielnie hostowane)
- [ ] **Animacje GIF ćwiczeń** — pakiet zewnętrznych assetów (10-30 MB)
- [ ] **Powiadomienia lokalizacyjne** — geofencing przy siłowni
- [ ] **Mapa regeneracji mięśni** (Fitbod-level) — algorytm decay zaangażowania w czasie
- [ ] **Strength standards** — porównanie do norm (Beginner/Intermediate/Advanced) per ćwiczenie + waga ciała

---

## Konwencje

- Każda **Faza** = osobny commit / release (v0.X.0)
- Każda zmiana DB schema = bump version + (jeśli niedestrukcyjna) Migration spec
- Commit message imperatyw, EN: "Add X", "Fix Y"
- Po każdej fazie: odhaczam checkbox tutaj i commituję update ROADMAP.md
