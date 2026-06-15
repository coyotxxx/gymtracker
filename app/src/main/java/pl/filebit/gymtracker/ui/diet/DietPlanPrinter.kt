package pl.filebit.gymtracker.ui.diet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v2.50.0 — „Drukuj plan": render planu dnia do HTML i przekazanie do systemowego
 * frameworka druku Androida (PrintManager). User dostaje natywnie „Drukuj" ORAZ
 * „Zapisz jako PDF". Offline, bez backendu — zgodne z zasadami projektu.
 */
object DietPlanPrinter {

    // Trzymamy referencję, żeby WebView nie został zebrany przez GC przed wystartowaniem druku.
    private var pending: WebView? = null

    fun print(context: Context, state: DietUiState) {
        val html = buildHtml(state)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val adapter = view.createPrintDocumentAdapter(JOB_NAME)
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print(JOB_NAME, adapter, PrintAttributes.Builder().build())
                pending = null
            }
        }
        pending = webView
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun buildHtml(state: DietUiState): String {
        val dateLabel = runCatching {
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl", "PL")).format(Date(state.dateMs))
        }.getOrDefault("").replaceFirstChar { it.uppercase() }
        val g = state.goal

        val mealsHtml = buildString {
            for (group in state.groups) {
                val title = (mealTypeLabel(group.type) +
                    (group.customLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                    (group.timeLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                append("<h2>").append(esc(title)).append("</h2>")
                if (group.entries.isEmpty()) {
                    append("<p class=\"muted\">— brak pozycji —</p>")
                } else {
                    append("<table><thead><tr>")
                    append("<th class=\"l\">Produkt</th><th>Ilość</th><th>kcal</th><th>B</th><th>W</th><th>T</th>")
                    append("</tr></thead><tbody>")
                    for (e in group.entries) {
                        append("<tr>")
                        append("<td class=\"l\">").append(esc(e.product.name)).append("</td>")
                        append("<td>").append(e.entry.grams.roundToInt()).append(" g</td>")
                        append("<td>").append(e.kcal.roundToInt()).append("</td>")
                        append("<td>").append(e.protein.roundToInt()).append("</td>")
                        append("<td>").append(e.carbs.roundToInt()).append("</td>")
                        append("<td>").append(e.fat.roundToInt()).append("</td>")
                        append("</tr>")
                    }
                    val t = group.totals
                    append("<tr class=\"sum\"><td class=\"l\">Razem</td><td></td><td>")
                    append(t.kcal.roundToInt()).append("</td><td>").append(t.protein.roundToInt())
                    append("</td><td>").append(t.carbs.roundToInt()).append("</td><td>")
                    append(t.fat.roundToInt()).append("</td></tr>")
                    append("</tbody></table>")
                }
            }
        }

        val day = state.totals
        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 24px; }
              h1 { font-size: 20px; margin: 0 0 2px; }
              .date { color: #666; font-size: 13px; margin-bottom: 16px; }
              .goal { background: #f4f4f6; border: 1px solid #e2e2e8; border-radius: 8px; padding: 10px 14px; margin-bottom: 18px; }
              .goal b { font-size: 15px; }
              .goal span { color: #555; font-size: 13px; margin-right: 14px; }
              h2 { font-size: 14px; margin: 16px 0 6px; border-bottom: 2px solid #c27a3a; padding-bottom: 3px; }
              table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
              th, td { padding: 5px 6px; text-align: right; border-bottom: 1px solid #eee; }
              th { color: #888; font-weight: 600; font-size: 11px; text-transform: uppercase; }
              .l { text-align: left; }
              .sum td { font-weight: 700; border-top: 1px solid #ccc; border-bottom: none; }
              .muted { color: #999; font-size: 12px; }
              .daysum { margin-top: 20px; padding-top: 10px; border-top: 2px solid #1a1a1a; font-size: 14px; }
              .daysum b { margin-right: 16px; }
              .foot { margin-top: 24px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety</h1>
            <div class="date">${esc(dateLabel)}</div>
            <div class="goal">
              <b>Cel dnia:</b>
              <span>${g.kcal} kcal</span>
              <span>Białko ${g.proteinG} g</span>
              <span>Węgle ${g.carbsG} g</span>
              <span>Tłuszcz ${g.fatG} g</span>
            </div>
            $mealsHtml
            <div class="daysum">
              <b>Suma dnia:</b>
              <b>${day.kcal.roundToInt()} kcal</b>
              <b>B ${day.protein.roundToInt()} g</b>
              <b>W ${day.carbs.roundToInt()} g</b>
              <b>T ${day.fat.roundToInt()} g</b>
            </div>
            <div class="foot">Wygenerowano w GymTracker</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety"
}
