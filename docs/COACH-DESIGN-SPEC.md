# COACH DESIGN SPEC — jak system MA działać (bez AI i z AI)

> Towarzyszy `UNIFIED-COACH-ARCHITECTURE.md`. Tamten = mapa + co scalić. Ten = **precyzyjny
> projekt działania**: jak coach podejmuje decyzje, w trybie algorytmicznym (zawsze) i z AI
> (większa precyzja). Projekt wg najlepszych praktyk trenerskich i dietetycznych.
>
> Warunek Macieja: **musi działać bez AI** (deterministycznie, BYOK — user może nie mieć klucza)
> **i z AI** (precyzyjniej — AI analizuje pełny kontekst i wyciąga lepsze wnioski).

---

## 1. Zasada dwóch warstw (rdzeń projektu)

```
TIER A — COACH DETERMINISTYCZNY (zawsze, bez AI)        ← PODŁOGA + bezpieczeństwo
  sygnały → silniki regułowe → arbiter → CoachReaction[]
  Zawsze produkuje poprawny, ostrożny, oparty na badaniach plan. Nigdy nie wymaga klucza AI.

TIER B — AI CO-PILOT (gdy jest klucz, opcjonalnie)      ← PRECYZJA + niuans + komunikacja
  dostaje: pełny kontekst + decyzje Tier A + uzasadnienia
  może: doprecyzować wielkość (w bezpiecznym zakresie), przeważyć priorytet przy konflikcie,
        scalić kilka reakcji w jeden spójny komunikat, wyłapać niuans którego reguły nie widzą,
        wytłumaczyć po ludzku.
  NIE może: złamać SafetyGuard, działać bez bazy Tier A, zmieniać kodu (tylko dane/komunikat).
```

**Reguła żelazna:** AI nigdy nie jest punktem wyjścia. Najpierw deterministyczny algorytm daje
poprawną decyzję; AI ją *poleruje*. Brak/awaria AI → zostaje decyzja Tier A, bez degradacji funkcji.

**Dlaczego tak (best practice):** dobry trener ma sprawdzony schemat (algorytm) i doświadczenie do
niuansów (AI). Schemat chroni przed głupotą; doświadczenie dodaje precyzji. Nigdy odwrotnie.

---

## 2. Co już jest gotowe jako Tier A (nie budujemy od zera)

| Domena | Silnik (czysta funkcja / regułowy) | Jakość |
|--------|-----------------------------------|--------|
| Korekta kcal/makro | `CalorieAdjustmentEngine.analyze` | ⭐ dojrzały, ISSN/Helms/RP, wielosygnałowy |
| Zmęczenie/deload | `detectDeloadNeed` | ⭐ RP/Israetel/MASS, goal-aware |
| Powrót po przerwie | `detectReturnAfterBreak` | ⭐ SHORT/LONG break |
| Kontuzja | `detectActiveInjury` | ⭐ FLAG/PERSISTENT |
| Opuszczony trening | `detectMissedWorkouts` | ⭐ SOFT/FIRM |
| Sen/HRV → trening | `HealthInsightAnalyzer` | REST/DELOAD/LIGHT |
| Cel kcal bazowy | `DietGoals.computeDailyGoal` | TDEE + cap + carb cycling |
| Faza/refeed | `PhaseManager`, `CalorieAdjustmentEngine` (diet break 8tyg) | ✓ |
| Sygnały (wszystko w 1) | `MasterAiContextBuilder` | ⭐ 1 kontekst |

**`CalorieAdjustmentEngine` to wzór całości:** zanim zetnie kcal, sprawdza sen+stres (zła regeneracja
→ HOLD), głód (→ REFEED), soreness+energia (→ DELOAD), trudność (→ SIMPLIFY), spadek kroków (→ NEAT
najpierw), nawodnienie. To DOKŁADNIE „dieta patrzy też na trening, kroki, regenerację" — już istnieje.
Brakuje tylko **tego samego na poziomie CAŁOŚCI**, ponad domenami, plus AI-nadzór.

---

## 3. Pełny przepływ decyzji (jak działa za każdym razem)

Wyzwalacze: codzienny Bilans (21:00), po treningu, po ważeniu, wejście na Home/Dietę, on-demand (AI Trener).

```
1. CoachSignals = MasterAiContext + (volatility, braki dnia, ACWR pełne, faza)   [1 źródło]
2. SILNIKI (Tier A) liczą kandydatów per domena:
     diet      → CalorieAdjustmentEngine.analyze(...)        → AdjustmentDecision
     fatigue   → detectDeloadNeed(...)                       → DeloadRecommendation?
     return    → detectReturnAfterBreak(...)                 → ReturnAfterBreak?
     injury    → detectActiveInjury(...)                     → ActiveInjury?
     missed    → detectMissedWorkouts(...)                   → MissedWorkout?
     health    → HealthInsightAnalyzer.analyze()             → REST/DELOAD/LIGHT
     consist.  → braki dnia (posiłki/trening nieoznaczone)   → ConsistencyGap?
     goal      → GoalAchievementService                      → GoalReached?
3. ARBITER (Tier A, deterministyczny):
     a) DEDUP — deload z 4 źródeł → jeden sygnał deloadu
     b) PRIORYTET (drabina niżej) — wybierz dominujący + ewentualne poboczne
     c) ANTI-KONFLIKT — reguły wykluczeń (niżej)
     → kanoniczna lista CoachReaction[] (zwykle 1 główna + 0-2 poboczne)
4. AI-NADZÓR (Tier B, jeśli klucz):
     wejście: CoachSignals + CoachReaction[] z uzasadnieniami
     wyjście: ta sama lista, ale: wielkości doprecyzowane (w granicach ±), priorytet
              ewentualnie przeważony, komunikaty scalone/po ludzku, niuanse dodane
     WALIDACJA wyjścia AI: SafetyGuard + sanity bounds; cokolwiek poza zakresem → bierzemy Tier A
5. EMISJA: jeden renderer (karta Home/Dieta) + jeden notifier systemowy + log diagnostic_events
```

---

## 4. Drabina priorytetów (najlepsza praktyka trenerska)

Od najwyższego. Dominujący sygnał = najwyższy aktywny szczebel.

1. **ZDROWIE / BEZPIECZEŃSTWO** — aktywna kontuzja/ból; deficyt poniżej bezpiecznego (SafetyGuard);
   skrajnie zła regeneracja. *„Najpierw nie szkodzić."*
2. **REGENERACJA** — zły sen/stres/soreness/energia → odpoczynek/deload/refeed PRZED forsowaniem.
   *„Zmęczony organizm nie adaptuje."* (CalorieEngine już to robi dla diety — podnosimy na poziom całości.)
3. **POWRÓT PO PRZERWIE** — ostrożny restart ma priorytet nad deloadem (to nie przetrenowanie).
4. **SYSTEMATYCZNOŚĆ / ADHERENCE** — opuszczone treningi, nieoznaczone/niezalogowane posiłki, niska
   zgodność → najpierw zachowanie, dopiero potem liczby. *„Nie strój planu którego nie wykonujesz."*
5. **OPTYMALIZACJA** — korekty kcal, progresja/zwiększenie obciążenia, przejścia fazy cyklu, periodyzacja.

**Zasada:** schodzimy w dół tylko gdy wyższe szczeble czyste. Np. nie tnij kcal (5) gdy sen zły (2) —
to już jest w CalorieEngine; arbiter rozszerza tę zasadę na cały system.

---

## 5. Reguły anti-konflikt (żeby nie było kolosa sprzecznych reakcji)

| Konflikt | Rozstrzygnięcie |
|----------|-----------------|
| Kontuzja + deload jednocześnie | Kontuzja wygrywa; deload pomijany (inny problem) |
| Deload + cięcie kcal w tym samym tygodniu | Najpierw deload/refeed; korekta kcal czeka tydzień |
| Powrót po przerwie + deload | Powrót wygrywa (wysokie RPE = szok, nie przetrenowanie) |
| Cięcie kcal + zła regeneracja/NEAT drop/odwodnienie | HOLD/REFEED zamiast cięcia (już w CalorieEngine) |
| Bulk + niska frekwencja treningów | HOLD kcal (nadwyżka bez treningu = tłuszcz) |
| Wiele alertów naraz | 1 dominujący na karcie/notyfikacji + reszta zwinięta („zobacz więcej") |

---

## 6. Per-cel — co coach optymalizuje (funkcja celu)

Jeden **CoachGoal** steruje wagami sygnałów i progami.

| Cel | Optymalizuje | Reaguje najmocniej na | Próg wagi/korekt |
|-----|--------------|------------------------|-------------------|
| **CUT / FAT_LOSS** | utrata tłuszczu + ochrona mięśni | stagnacja wagi, niskie białko, spadek NEAT, zła regeneracja | deficyt ostrożny, deload→refeed, diet break 8tyg |
| **BULK / MUSCLE_GAIN** | progresja siły + lean surplus | brak progresji, za szybki przyrost (tłuszcz), niska frekwencja | nadwyżka tylko przy treningach, deload przy stagnacji |
| **MAINTAIN** | stabilność w korytarzu ± | dryf wagi w obie strony | małe korekty ±100 |
| **RECOMP** | skład/siła przy stałej wadze | spadek siły, duże wahania | waga STAŁA = cel, nie alarm |

Cel pochodzi z JEDNEGO źródła (patrz U1) — dieta i trener czytają to samo.

---

## 7. Rola AI — dokładnie co dokłada (Tier B)

AI dostaje deterministyczną decyzję i pełny kontekst. Wartość AI = **niuans i komunikacja**, nie
„własne wymyślanie":

1. **Doprecyzowanie wielkości** — algorytm mówi „−150 kcal"; AI widząc trend + sen + miesiączkę/podróż/
   chorobę w notatkach może zaproponować „−100 i obserwuj 5 dni". Tylko w bezpiecznym zakresie.
2. **Przeważenie przy konflikcie** — dwa sygnały równorzędne; AI rozstrzyga z pełnym obrazem
   („sen spada tylko w dni treningowe → to DOMS, nie bezsenność → nie blokuj diety, popraw timing").
3. **Scalenie komunikatu** — 3 reguły odpaliły; AI łączy w jedną ludzką wiadomość zamiast 3 kart.
4. **Wykrycie wzorca** — algorytm patrzy na progi; AI widzi sekwencję („3 tydzień z rzędu spadek
   energii w środy = przeciążenie nóg w poniedziałki").
5. **Edukacja/wyjaśnienie** — „dlaczego" po ludzku, z zasadą trenerską.
6. **Zmiana danych na prośbę** (już jest: `log_weight/add_meal/set_calorie_target/set_diet_goal`).

**Granice AI (twarde):** nie przekracza SafetyGuard; nie zmienia kodu/zachowania apki; jego wyjście
jest WALIDOWANE — poza zakresem → bierzemy decyzję Tier A. Bez klucza → Tier A działa w 100%.

---

## 8. Co zostaje, co nowe, co konsolidujemy

**ZOSTAJE (jako czujniki/doradcy, minimalnie zmienione):** wszystkie silniki z §2. Są dobre.
Przestają tylko *samodzielnie* odpalać UI — oddają wynik arbitrowi.

**NOWE (cienka warstwa spinająca):**
- `CoachGoal` — jedno źródło celu (U1).
- `CoachSignals` — alias/rozszerzenie `MasterAiContext` (U2).
- `CoachOrchestrator` — arbiter §3-5 (U3).
- `CoachReaction` — jeden model wyjścia + jeden renderer + jeden notifier (U4).
- `CoachAiOverseer` — adapter Tier B z walidacją (U5).

**KONSOLIDUJEMY:** deload×4 → 1 (arbiter dedup); 2 kanały powiadomień → 1; dualizm celu → 1;
kcal w 3 miejscach → jasna kolejność (bazowy → korekta → faza) pod arbitrem.

---

## 9. Mierniki „czy działa jako personal" (kontrola efektów)

Coach ma pilnować EFEKTÓW i JAKOŚCI, nie tylko reagować:
- **Efekt vs cel** — trend wagi vs cel (GoalAchievementService) → komunikat postępu.
- **Systematyczność** — % wykonanych treningów + % zalogowanych/oznaczonych posiłków (adherence).
- **Jakość ćwiczeń** — progresja e1RM (StagnationAnalyzer), RPE/RIR, technika (painArea).
- **Regeneracja** — sen/HRV/soreness trend.
- Te mierniki = wejście do Bilansu (§3) i do raportu tygodniowego.

---

## 10. Roadmapa wykonania (z `UNIFIED-COACH-ARCHITECTURE.md`)
U1 jeden CEL → U2 CoachSignals → U3 Orchestrator+arbiter (dedup deload) → U4 jeden CoachReaction
kanał → U5 AI-nadzór z walidacją → U6 per-goal + E2E. Każdy etap = release, testy zielone, bez utraty
funkcji, bez duplikacji.

**START: U1 (jeden cel).** Najbezpieczniejszy, odblokowuje resztę: dziś `WeightGoalType` i
`DietGoalType` żyją równolegle — ujednolicamy źródło prawdy i mapowanie, żeby dieta i trener
optymalizowały DOKŁADNIE ten sam cel.

---

## 11. Realne przykłady (jak to działa w życiu) — kontekst CUT, ~1986 kcal, 187g B, 3× bieżnia

**1. Waga stoi tydzień, dieta trzymana, ale kroki spadły 9000→6000.**
Bez AI: arbiter (CUT+stagnacja+dobra dieta+NEAT drop) → HOLD kcal, „to kroki, nie metabolizm — wróć do 9000".
Z AI: „spadek = deszczowy tydzień? dorzuć spacer, sprawdzimy za 5 dni". Dziś: ryzyko osobnego „utnij 150 kcal".

**2. Pominięta kolacja, dzień 1400/1986 kcal, białko 120/187.**
Bez AI: CUT→priorytet białko, „brakuje 67g B — skyr+szejk albo odpuść świadomie". Z AI: widzi 3. raz w tygodniu →
„przesuńmy kolację wcześniej/mniejszą". Dziś: karta nie zna celu/białka/wzorca.

**3. Konflikt: RPE 9.0 + waga stoi.** Dziś: „DELOAD" obok „utnij kcal" (sprzeczne). Bez AI: CUT+wysokie RPE=deficyt
→ JEDNA reakcja „refeed 1-2 dni, nie tnij, nie zmieniaj wag"; korekta kcal wstrzymana. Z AI: „+ jedna noc dłużej śpij".

**4. Ból kolana 2× + stagnacja 3 ćwiczeń.** Bez AI: drabina ZDROWIE>optymalizacja → „odpuść nogi 5-7 dni/fizjo",
deload/stagnacja zawieszone. Z AI: konkretne zamienniki bez obciążania kolana.

**5. Sen spada 5 dni + waga stoi (różnica bez AI / z AI).** Bez AI: zła regeneracja → HOLD, „popraw sen". Z AI:
sekwencja — sen spada tylko w noce po nogach → „to DOMS nie bezsenność; przesuń nogi, dieta może iść, −100".

**Cel = utrzymanie:** waga +0.5kg/tydz → „−100 kcal" (korytarz w obie strony). Cel steruje funkcją celu coacha.

Wspólny mianownik: zamiast kilku kart które się nie znają i przeczą — JEDEN głos coacha (zna cel, łączy domeny,
priorytetyzuje). Algorytm = poprawnie i bezpiecznie zawsze; AI = niuans + ludzkie wyjaśnienie.

## 12. Status
Projekt gotowy do realizacji — czeka na akceptację Macieja. Po „OK" startujemy U1.
