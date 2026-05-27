# Legacy Exercise-DB (ExerciseDB v1)

Kopia bezpieczeństwa starej bazy ćwiczeń (ExerciseDB) zarchiwizowana przed migracją do canonical exercise-db w v2.0.0.

## Co tu jest

### `assets/`
- `exercises.json` — 193 ćwiczenia, stary seed
- `exercisedb_v1.json` — ~1500 ćwiczeń ExerciseDB (GIF z `static.exercisedb.dev`)
- `exercisedb_v1_pl.json` — tłumaczenia PL ExerciseDB
- `exercisedb_v1_aliases.json` — aliasy ID po dedup
- `exercisedb_v1_dead.json` — martwe GIFy (404)
- `exercisedb_v1_seed_match.json` — mapping seed → ExerciseDB ID
- `exercise_aliases.json` — PL aliasy nazw dla seed

### `code/`
- `ExerciseDbBootstrap.kt` — stary loader (575 linii) z `CANONICAL_PL_MATCH` (hardkod 65 PL nazw)

## Jak rollback

Jeśli v2.0.0 zawiedzie i trzeba wrócić:
```bash
git checkout baseline-pre-canonical-migration
# albo
git reset --hard baseline-pre-canonical-migration
```

Tag `baseline-pre-canonical-migration` zachowuje pełny stan v1.29.25.

## Plan usunięcia archive

Po 1 tygodniu sprawnej produkcji v2.0.0 → `git rm -rf archive/` w v2.0.1.
