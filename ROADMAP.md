# GymTracker — Roadmap

## Zasady projektu

- Aplikacja **całkowicie darmowa**, bez subskrypcji, bez płatności, bez reklam.
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

### Faza 12 — Wartość operacyjna

- [ ] **Typy serii** — warm-up / working / drop set / failure / AMRAP (kolumna w `WorkoutSet` + `PlanExerciseSet`, kolorystyka + filtr w statystykach)
- [ ] **"Ostatnio: X kg × Y"** w ad-hoc workout (już jest w Coach mode, dodać też do listy ad-hoc)
- [ ] **Auto-fill "Dodaj serię"** — kopia z poprzedniej serii zamiast `8×0kg`
- [ ] **Notatki per-trening** — textarea pokazywana w ActiveWorkout/CoachWorkout (icon notatki w TopAppBar)
- [ ] **"Powtórz trening"** w WorkoutDetail (overflow menu — kopiuj sety jako placeholdery do nowego treningu, bez przechodzenia przez plan)

### Faza 13 — Statystyki & PR

- [ ] **Statystyki overview** — osobny ekran lub w Profilu: liczba treningów, łączna objętość, łączny czas, średnia objętość/trening, treningi w tym tygodniu/miesiącu
- [ ] **PR per ćwiczenie** — max waga (×powt), max objętość, szacowane 1RM (formuła Epley `1RM = w × (1 + reps/30)`); pokazywane przy karcie ćwiczenia w bibliotece
- [ ] **🏆 "Nowy rekord!"** alert po zakończeniu treningu (jeśli pobity rekord wagi/objętości/1RM)
- [ ] **1RM calculator** w Profilu (osobny ekran: wpisz wagę + powt → wynik wg Epley + Brzycki + Lombardi)

### Faza 14 — Tools

- [ ] **Plate calculator** — kalkulator talerzy na sztangę (dialog z poziomu pola Waga w treningu lub osobny ekran w Profilu)
- [ ] **Filtr "Sprzęt"** w bibliotece ćwiczeń (oprócz już istniejącego "Mięsień")
- [ ] **Eksport CSV** w Backup (oprócz JSON) — treningi, sety, ćwiczenia
- [ ] **Notatki per-ćwiczenie** — notatka per Exercise (np. "uważać na łokcie") + per WorkoutSet ("dziś czułem się słaby")

---

## 🟡 V2 — premium feel (lokalne funkcje, bez backendu)

### Faza 15 — Wykresy & analityka

- [ ] Wykresy progresji per ćwiczenie (ciężar / objętość / powt w czasie) — np. Vico Charts albo Compose Canvas
- [ ] Sugestia progresji ("wszystkie serie ✓ w górnym zakresie powt → +2.5%")
- [ ] Alert stagnacji (brak progresu przez 3-4 treningi → sugestia deload)

### Faza 16 — Plany zaawansowane

- [ ] **Gotowe szablony planów** (Push/Pull/Legs, Upper/Lower, 5x5 Stronglifts, Full Body 3x/tydzień) — wbudowane w aplikację
- [ ] **Drag & drop** kolejności ćwiczeń w PlanEdit (DragAndDropTarget Compose)
- [ ] **Duplikuj plan** — szybkie kopiowanie istniejącego planu
- [ ] **Superserie** A1/A2 — grupowanie ćwiczeń

### Faza 17 — Streaki & motywacja

- [ ] Streak tygodniowy (X tygodni z rzędu)
- [ ] Cele tygodniowe (3 treningi w tygodniu)
- [ ] Odznaki (10 / 50 / 100 treningów, 100k kg objętości)
- [ ] Powiadomienia o niedokończonym treningu

### Faza 18 — Tempo / RPE / RIR

- [ ] Pole RPE (1-10) w `WorkoutSet` (opcjonalne)
- [ ] Pole RIR (reps in reserve) w `WorkoutSet`
- [ ] Tempo (eksc/pauza/konc/pauza, np. "3-1-1-0")

### Faza 19 — Body & pomiary

- [ ] Body measurements (waga, obwód klatki/pasa/ramienia/uda, BF%)
- [ ] Wykres masy ciała w czasie
- [ ] Zdjęcia progresu (przód/bok/tył) — local storage

### Faza 20 — Mapa mięśni (statyczna)

- [ ] Silhouette ciała + procent zaangażowania (suma wolumen per grupa mięśniowa)
- [ ] "Tydzień: ile pracy dla każdej partii?"

---

## 🟢 V3 — FUTURE (wymaga backendu / dużego nakładu)

- [ ] **AI workout generator** — model BYOK (user wkleja klucz Anthropic/OpenAI w ustawieniach), generuje plan na podstawie celu+sprzętu+dni
- [ ] **AI sugestia kolejnego treningu** — analiza historii przez LLM
- [ ] **Cloud sync** — opcjonalnie przez Firebase/Supabase (osobne konto darmowe lub samodzielnie hostowane)
- [ ] **Animacje GIF ćwiczeń** — pakiet zewnętrznych assetów (10-30 MB)
- [ ] **Wear OS / Apple Watch** — osobna platforma
- [ ] **Powiadomienia lokalizacyjne** — geofencing przy siłowni
- [ ] **Mapa regeneracji mięśni** (Fitbod-level) — algorytm decay zaangażowania w czasie
- [ ] **Strength standards** — porównanie do norm (Beginner/Intermediate/Advanced) per ćwiczenie + waga ciała

---

## Konwencje

- Każda **Faza** = osobny commit / release (v0.X.0)
- Każda zmiana DB schema = bump version + (jeśli niedestrukcyjna) Migration spec
- Commit message imperatyw, EN: "Add X", "Fix Y"
- Po każdej fazie: odhaczam checkbox tutaj i commituję update ROADMAP.md
