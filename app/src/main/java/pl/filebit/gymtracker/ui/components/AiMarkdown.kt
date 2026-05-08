package pl.filebit.gymtracker.ui.components

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Wspólny renderer markdownu dla wszystkich tekstów generowanych przez LLM.
 *
 * Implementacja: Markwon (View-based, Android-native) + AndroidView interop.
 * Niezalezna od wersji Compose — odpowiednik klasycznego TextView z
 * SpannableString. Zero ryzyka NoSuchMethodError przy upgrade Compose.
 *
 * Obslugiwane:
 *  - **bold** / *italic* / ~~strike~~ / `code`
 *  - # / ## / ### naglowki
 *  - listy bullet i numerowane
 *  - tabele markdown |---|---|
 *  - blockquote
 *  - linki (auto-linkify URL-e)
 *  - code blocks ``` ```
 *
 * NIE uzywaj dla:
 *  - tekstu wpisywanego przez uzytkownika
 *  - statycznych etykiet UI
 *  - logow AI (tam ma byc widoczny surowy markdown dla diagnostyki)
 */
@Composable
fun AiMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val argbColor = contentColor.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()

    val markwon = remember(context, accentColor) {
        val tableTheme = TableTheme.Builder()
            .tableBorderColor(argbColor and 0x33FFFFFF.toInt())
            .tableHeaderRowBackgroundColor(accentColor and 0x22FFFFFF.toInt())
            .tableEvenRowBackgroundColor(0)
            .tableOddRowBackgroundColor(argbColor and 0x08FFFFFF.toInt())
            .build()
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(tableTheme))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(argbColor)
                setLinkTextColor(accentColor)
                typeface = Typeface.DEFAULT
                setLineSpacing(2f, 1.15f)
            }
        },
        update = { tv ->
            tv.setTextColor(argbColor)
            tv.setLinkTextColor(accentColor)
            markwon.setMarkdown(tv, text)
        }
    )
}
