# Release Flow — GymTracker

Pełna procedura wydania nowej wersji, żeby APK trafił do użytkownika **bez ręcznej instalacji** (przez in-app updater).

---

## TL;DR — kolejność kroków

1. **Bump wersji** w `app/build.gradle.kts` (oba: `versionCode` i `versionName`)
2. **Commit + push** na branch (np. `redesign-v2`)
3. **Triggeruj CI ręcznie** (push na nie-main NIE odpala workflowa automatycznie)
4. **Po sukcesie CI**: pobierz APK przez `gh api .../zip` (NIE `gh run download` — nie działa)
5. **Utwórz GitHub Release** z APK — bez tego in-app updater nie zobaczy aktualizacji

Pominięcie któregokolwiek kroku = user mówi „nie mogę zaktualizować".

---

## 1. Bump wersji

**Oba pola muszą zostać podbite. Zawsze.** Android odmawia instalacji APK z `versionCode` ≤ aktualnie zainstalowanej.

```kotlin
// app/build.gradle.kts
defaultConfig {
    versionCode = 70        // +1 KAŻDY release (nawet hotfix)
    versionName = "0.52.0"  // SemVer
}
```

| Zmiana | versionCode | versionName |
|---|---|---|
| Nowa funkcja | +1 | minor bump (0.51.1 → 0.52.0) |
| Bugfix po release | +1 | patch bump (0.52.0 → 0.52.1) |
| Hotfix (compile error po push) | +1 | bez zmian (0.52.0 zostaje) — tylko CI re-run |

> Hotfix pod tym samym `versionName` jest OK pod warunkiem, że release nie został jeszcze utworzony. Po publikacji release'a — bump patch.

---

## 2. Sprawdź zanim spushujesz (lokalnie nie ma gradlew!)

W repo **NIE MA** `gradlew` ani globalnego `gradle` na tym serwerze. Nie ma jak przetestować kompilacji przed pushem. Dlatego ręczny audyt importów jest obowiązkowy.

### Checklist przed pushem

Dla **każdego** edytowanego pliku Compose:

```bash
# Czy plik używa .sp (font size, letter spacing)?
grep -n "fontSize.*\.sp\|letterSpacing.*\.sp\|TextUnit" PLIK.kt
# Jeśli tak: musi mieć
grep -q "import androidx.compose.ui.unit.sp" PLIK.kt
```

**Najczęstsze brakujące importy:**

| Użycie | Wymagany import |
|---|---|
| `12.dp`, `Modifier.padding(16.dp)` | `androidx.compose.ui.unit.dp` |
| `14.sp`, `fontSize = 11.sp` | `androidx.compose.ui.unit.sp` |
| `Color.Black`, `Color(0xFF…)` | `androidx.compose.ui.graphics.Color` |
| `BorderStroke(1.dp, …)` | `androidx.compose.foundation.BorderStroke` |
| `Modifier.background(…)` | `androidx.compose.foundation.background` |
| `Modifier.border(…)` | `androidx.compose.foundation.border` |
| `Modifier.size(48.dp)` | `androidx.compose.foundation.layout.size` |
| `Modifier.width(…)` | `androidx.compose.foundation.layout.width` |
| `RoundedCornerShape(…)` | `androidx.compose.foundation.shape.RoundedCornerShape` |
| `OutlinedTextFieldDefaults.colors(…)` | `androidx.compose.material3.OutlinedTextFieldDefaults` |
| `TopAppBarDefaults.topAppBarColors(…)` | `androidx.compose.material3.TopAppBarDefaults` |
| `LinearProgressIndicator(progress = { … })` | `androidx.compose.material3.LinearProgressIndicator` |
| `StrokeCap.Round` | `androidx.compose.ui.graphics.StrokeCap` |
| `Icons.Default.AutoAwesome` | `androidx.compose.material.icons.filled.AutoAwesome` |
| `Icons.AutoMirrored.Filled.ArrowBack` | `androidx.compose.material.icons.automirrored.filled.ArrowBack` |

### Pułapki regex

`grep "\.sp"` daje **false positives** dla `Arrangement.spacedBy(...)`, `Arrangement.SpaceAround` itp. Sprawdź ręcznie:

```bash
# Tylko realne użycia jednostki sp:
grep -nE "[0-9]+\.sp\b|fontSize\s*=" PLIK.kt
```

### `@OptIn` nie jest potrzebny

Projekt ma globalny opt-in:
```
freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
```
→ nie trzeba dawać `@OptIn(ExperimentalMaterial3Api::class)` na funkcjach używających `OutlinedTextFieldDefaults`, `TopAppBar`, etc.

---

## 3. Commit + push

```bash
git add <konkretne pliki>   # NIGDY git add -A — może wciągnąć debug.keystore lub .env
git commit -m "$(cat <<'EOF'
v0.52.0 — krótki opis zmian

- pierwsza zmiana
- druga zmiana

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin redesign-v2
```

---

## 4. Trigger CI (push na nie-main NIE odpala workflowa)

Plik `.github/workflows/build.yml` ma:
```yaml
on:
  push:
    branches: [ main, master ]   # tylko main!
  workflow_dispatch:              # ręcznie
```

Branch `redesign-v2` (lub jakikolwiek feature branch) wymaga ręcznego triggera:

```bash
gh workflow run build.yml --ref redesign-v2
sleep 3
gh run list --branch redesign-v2 --limit 1   # zweryfikuj że "queued"
```

Watch w tle:
```bash
gh run watch <RUN_ID> --exit-status
```

Jeśli build się wywali — pobierz logi:
```bash
gh run view <RUN_ID> --log-failed 2>&1 | grep -E "e: file:|error:|FAILURE|Compilation" | head -40
```

---

## 5. Pobranie APK (gotcha — `gh run download` NIE DZIAŁA)

```bash
# ❌ ŹLE — wywala "would result in path traversal"
gh run download <RUN_ID> -n <ARTIFACT_NAME>

# ✅ DOBRZE — przez API + unzip
mkdir -p /tmp/gym-apk && cd /tmp/gym-apk
ARTIFACT_ID=$(gh api "repos/coyotxxx/gymtracker/actions/runs/<RUN_ID>/artifacts" --jq '.artifacts[0].id')
gh api -H "Accept: application/vnd.github+json" \
    "repos/coyotxxx/gymtracker/actions/artifacts/$ARTIFACT_ID/zip" > apk.zip
unzip -o apk.zip
ls -la *.apk
```

Nazwa artifactu odpowiada nazwie ze `Upload APK` step w workflowie:
`gymtracker-v<VERSION>-<BRANCH>` → APK wewnątrz: `gymtracker-v<VERSION>-<BRANCH>-debug.apk`.

---

## 6. GitHub Release (BEZ TEGO IN-APP UPDATER NIE ZOBACZY APK)

In-app updater (`ui/update/`) odpytuje endpoint `repos/coyotxxx/gymtracker/releases/latest` (lub `/releases`). **Artifacty z Actions są niewidoczne** dla updatera — to inny endpoint.

```bash
cd /home/debian/gh-projects/gymtracker   # gh release MUSI być w git repo

gh release create v<VERSION>-redesign \
    --target redesign-v2 \
    --prerelease \
    --title "v<VERSION> — krótki tytuł" \
    --notes "$(cat <<'EOF'
## Co nowego
- punkt 1
- punkt 2
EOF
)" \
    /tmp/gym-apk/gymtracker-v<VERSION>-redesign-v2-debug.apk
```

### Konwencja tagów

| Branch | Tag |
|---|---|
| `main` | `v0.52.0` |
| `redesign-v2` | `v0.52.0-redesign` |
| feature branch | `v0.52.0-<branch-shortname>` |

Wszystkie release'y z branchy != main są **pre-release** (`--prerelease`).

### Sprawdź że release jest widoczny

```bash
gh release list --limit 3
gh release view v<VERSION>-redesign
```

---

## 7. Diagnoza „nie mogę zaktualizować"

| Objaw | Przyczyna | Fix |
|---|---|---|
| Updater milczy | Brak GitHub Release dla nowej wersji | Krok 6 |
| „Nie zainstalowano aplikacji" | Inny signing key (debug vs release lub różny CI runner) | Sprawdź czy `app/debug.keystore` jest w repo i committed |
| versionCode się nie zmienił | Forgot bump | Krok 1 |
| Updater pokazuje starszą | Cache: `Settings → Apps → GymTracker → Storage → Clear cache` |
| Pre-release nie pokazuje się | Updater filtruje pre-release | Sprawdź `ui/update/UpdateChecker.kt` — flag `prerelease` |

---

## 8. Pełny przykład: hotfix po niedopilnowanym imporcie

```bash
# 1. Bump nie potrzebny — release jeszcze nie utworzony, tylko fix CI
# 2. Edytuj plik
# 3. Commit
git add app/src/main/java/.../BodyMeasurementsScreen.kt
git commit -m "$(cat <<'EOF'
Fix v0.52.0: import androidx.compose.ui.unit.sp w BodyMeasurementsScreen

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin redesign-v2

# 4. Trigger CI
gh workflow run build.yml --ref redesign-v2
sleep 3 && gh run list --branch redesign-v2 --limit 1

# 5. Watch
gh run watch <RUN_ID> --exit-status

# 6. APK + release jak w krokach 5-6 powyżej
```

---

## 9. Co NIE robić

- ❌ `git add -A` — ryzyko wciągnięcia `local.properties`, `.env`, kluczy
- ❌ `git commit --amend` po wysłaniu na CI — nowy commit zamiast amend
- ❌ Push na main przed CI success na branchu
- ❌ `gh run download` (path traversal bug w Debian build gh CLI)
- ❌ Pominięcie release'a — APK w artifacts NIE wystarczy
- ❌ `versionCode` bez zmiany — Android odrzuci instalację
- ❌ Modyfikacja `app/debug.keystore` — łamie aktualizacje (Android wymaga ten sam signing key)
