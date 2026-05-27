package pl.filebit.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.ExerciseTrend
import pl.filebit.gymtracker.ai.StagnationAnalyzer
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExercisePr
import pl.filebit.gymtracker.data.repository.ExerciseProgressionPoint
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.StatsRepository
import javax.inject.Inject

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val pr: ExercisePr? = null,
    val progression: List<ExerciseProgressionPoint> = emptyList(),
    val history: List<WorkoutSet> = emptyList(),
    val trend: ExerciseTrend? = null,
    val translating: Boolean = false,  // v1.25.0: AI tłumaczy instructionsEn → PL
    /** v2.2.0: mapowanie slug → namePl dla powiązanych ćwiczeń (prerequisites/progression/alternatives). */
    val relatedExerciseNames: Map<String, String> = emptyMap()
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val statsRepo: StatsRepository,
    private val stagnationAnalyzer: StagnationAnalyzer,
    // v1.25.0: AI dla tłumaczeń EN→PL ExerciseDB instructions
    private val aiClient: pl.filebit.gymtracker.ai.AiClient,
    private val aiPrefs: pl.filebit.gymtracker.ai.AiPreferences,
    private val exerciseDao: pl.filebit.gymtracker.data.db.dao.ExerciseDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _translationError = MutableStateFlow<String?>(null)
    val translationError: StateFlow<String?> = _translationError.asStateFlow()

    fun clearTranslationError() { _translationError.value = null }

    private fun setTranslating(b: Boolean) {
        _state.value = _state.value.copy(translating = b)
    }

    /**
     * v1.25.0 — AI tłumaczy `instructionsEnJson` → polski + zapisuje cache do
     * `instructionsPlJson`. Wynik = lista kroków PL. Pierwsza wizyta = wywołanie API,
     * kolejne = z DB cache (offline).
     */
    fun translateInstructionsToPl() {
        val ex = _state.value.exercise ?: return
        val enJson = ex.instructionsEnJson ?: return
        if (_state.value.translating) return
        viewModelScope.launch {
            setTranslating(true)
            _translationError.value = null
            try {
                val cfg = aiPrefs.load()
                if (!cfg.isConnected) {
                    _translationError.value = "Wpisz klucz API w Profil → Połączenie AI"
                    return@launch
                }
                val enSteps = kotlinx.serialization.json.Json
                    .parseToJsonElement(enJson)
                    .let { it as? kotlinx.serialization.json.JsonArray }
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    .orEmpty()
                val prompt = buildString {
                    append("Przetłumacz na polski instrukcje wykonania ćwiczenia '${ex.name}'. ")
                    append("Każdy krok osobno, naturalnym polskim treningowym językiem (RPE, technika, sygnały).\n\n")
                    append("Zwróć JSON list ze stepami (bez prefixu 'Step:N', sam tekst kroku):\n")
                    append("```json\n[\"krok 1...\", \"krok 2...\", ...]\n```\n\n")
                    append("Oryginał EN:\n")
                    enSteps.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                }
                val result = aiClient.chat(
                    config = cfg,
                    messages = listOf(
                        pl.filebit.gymtracker.ai.AiMessage(
                            pl.filebit.gymtracker.ai.AiRole.USER,
                            prompt
                        )
                    ),
                    source = "ExerciseTranslate"
                )
                result.fold(
                    onSuccess = { response ->
                        // Wyodrębnij JSON list z odpowiedzi (może być wrapped w ```json ... ```)
                        val jsonMatch = Regex("\\[[\\s\\S]+?\\]").find(response)?.value
                        val plJson = jsonMatch ?: response
                        // Walidacja: czy parsable jako JsonArray
                        runCatching {
                            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(plJson)
                            if (parsed !is kotlinx.serialization.json.JsonArray) {
                                throw IllegalArgumentException("not array")
                            }
                            exerciseDao.updateInstructionsPl(ex.id, plJson)
                            _state.value = _state.value.copy(
                                exercise = ex.copy(instructionsPlJson = plJson)
                            )
                        }.onFailure {
                            _translationError.value = "AI zwróciło nieprawidłowy format, spróbuj ponownie."
                        }
                    },
                    onFailure = { err ->
                        _translationError.value = err.message ?: "Błąd AI"
                    }
                )
            } finally {
                setTranslating(false)
            }
        }
    }

    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L

    private val _state = MutableStateFlow(ExerciseDetailUiState())
    val state: StateFlow<ExerciseDetailUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val ex = exerciseRepo.get(exerciseId)
            val pr = statsRepo.prForExercise(exerciseId)
            val progression = statsRepo.progressionForExercise(exerciseId)
            val history = statsRepo.historyForExercise(exerciseId)
            // Trend e1RM nie ma sensu dla cardio (czas/dystans) — pomijamy.
            val isCardio = ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DISTANCE_DURATION ||
                ex?.metricType == pl.filebit.gymtracker.data.entity.MetricType.DURATION
            val trend = if (isCardio) null
            else runCatching { stagnationAnalyzer.analyzeOne(exerciseId) }.getOrNull()
            // v2.2.0: resolve nazwy powiązanych ćwiczeń (prerequisites/progression/alternatives)
            val relatedNames = resolveRelatedNames(ex)
            _state.value = ExerciseDetailUiState(
                loading = false,
                exercise = ex,
                pr = pr,
                progression = progression,
                history = history,
                trend = trend,
                relatedExerciseNames = relatedNames
            )
        }
    }

    private suspend fun resolveRelatedNames(ex: Exercise?): Map<String, String> {
        if (ex == null) return emptyMap()
        val allSlugs = mutableSetOf<String>()
        for (jsonStr in listOf(ex.prerequisitesJson, ex.progressionToJson, ex.alternativesJson)) {
            if (jsonStr.isNullOrBlank() || jsonStr == "[]") continue
            runCatching {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
                    as? kotlinx.serialization.json.JsonArray ?: return@runCatching
                arr.forEach { el ->
                    (el as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { allSlugs.add(it) }
                }
            }
        }
        val result = mutableMapOf<String, String>()
        for (slug in allSlugs) {
            exerciseDao.findBySlug(slug)?.let { e ->
                result[slug] = e.namePl ?: e.name
            }
        }
        return result
    }

    fun saveNotes(notes: String) {
        val ex = _state.value.exercise ?: return
        viewModelScope.launch {
            exerciseRepo.upsert(ex.copy(notes = notes))
            _state.value = _state.value.copy(exercise = ex.copy(notes = notes))
        }
    }

    /** Toggle "unikaj" — AI nie zaproponuje tego ćwiczenia w nowym planie. */
    fun toggleAvoided() {
        val ex = _state.value.exercise ?: return
        viewModelScope.launch {
            val newAvoided = !ex.isAvoided
            // Logika: gdy zaznaczasz "unikaj" — auto-odznacz "ulubione" (sprzeczne).
            val newFav = if (newAvoided) false else ex.isFavorite
            exerciseRepo.upsert(ex.copy(isAvoided = newAvoided, isFavorite = newFav))
            _state.value = _state.value.copy(exercise = ex.copy(isAvoided = newAvoided, isFavorite = newFav))
        }
    }

    /** Toggle "ulubione" — AI używa do priorytetyzacji w generowanym planie. */
    fun toggleFavorite() {
        val ex = _state.value.exercise ?: return
        viewModelScope.launch {
            val newFav = !ex.isFavorite
            // Auto-odznacz "unikaj" gdy zaznaczasz "ulubione".
            val newAvoided = if (newFav) false else ex.isAvoided
            exerciseRepo.upsert(ex.copy(isFavorite = newFav, isAvoided = newAvoided))
            _state.value = _state.value.copy(exercise = ex.copy(isFavorite = newFav, isAvoided = newAvoided))
        }
    }
}
