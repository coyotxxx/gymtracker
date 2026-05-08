package pl.filebit.gymtracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Wspólny renderer markdownu dla wszystkich tekstów generowanych przez LLM.
 *
 * Używaj wszędzie gdzie wyświetlany jest długi tekst od AI:
 * - czat z trenerem (AiTrainer)
 * - quick ask sheet
 * - raport tygodniowy
 * - karty Recovery/Phase/Readiness na Home
 * - wyjaśnienia z DietScreen
 *
 * NIE używaj dla:
 * - tekstu wpisywanego przez użytkownika
 * - statycznych etykiet UI
 * - logów AI (tam ma być widoczny surowy markdown dla diagnostyki)
 */
@Composable
fun AiMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Markdown(
        content = text,
        modifier = modifier,
        colors = markdownColor(text = contentColor),
        typography = markdownTypography()
    )
}
