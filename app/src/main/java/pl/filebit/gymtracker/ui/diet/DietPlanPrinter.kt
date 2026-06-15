package pl.filebit.gymtracker.ui.diet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pl.filebit.gymtracker.util.DailyMacroGoal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jeden dzień w kompaktowym planie tygodniowym. */
data class WeekDayPlan(
    val dateMs: Long,
    val dayLabel: String,
    val isTraining: Boolean,
    val mealsPerDay: Int,
    val goal: DailyMacroGoal?
)

data class WeeklyPlan(
    val rangeLabel: String,
    val days: List<WeekDayPlan>
)

/**
 * v2.51.0 — „Drukuj plan": kompaktowy plan na CAŁY TYDZIEŃ (1 wiersz/dzień) do druku/PDF.
 *
 * Każdy dzień: oznaczenie trening/wolne + cele (kcal, B/W/T). Bez rozpisywania każdego
 * posiłku — zwarte zestawienie. Dni treningowe mają inne makra (carb cycling) i to widać.
 * Render → systemowy PrintManager Androida (Drukuj / Zapisz jako PDF). Offline, bez backendu.
 */
object DietPlanPrinter {

    private var pending: WebView? = null

    fun print(context: Context, week: WeeklyPlan) {
        val html = buildHtml(week)
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

    fun buildHtml(week: WeeklyPlan): String {
        val rows = buildString {
            for (d in week.days) {
                val cls = if (d.isTraining) " class=\"train\"" else ""
                val badge = if (d.isTraining) "<span class=\"b train\">🏋 Trening</span>"
                    else "<span class=\"b rest\">Wolne</span>"
                append("<tr$cls>")
                append("<td class=\"l\">").append(esc(d.dayLabel)).append("</td>")
                append("<td class=\"l\">").append(badge).append("</td>")
                if (d.goal != null) {
                    val perMeal = if (d.mealsPerDay > 0) d.goal.kcal / d.mealsPerDay else 0
                    append("<td><b>").append(d.goal.kcal).append("</b></td>")
                    append("<td>").append(d.goal.proteinG).append("</td>")
                    append("<td>").append(d.goal.carbsG).append("</td>")
                    append("<td>").append(d.goal.fatG).append("</td>")
                    append("<td class=\"muted\">").append(d.mealsPerDay).append(" × ~")
                        .append(perMeal).append(" kcal</td>")
                } else {
                    append("<td colspan=\"5\" class=\"muted\">— brak celu —</td>")
                }
                append("</tr>")
            }
        }
        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 22px; }
              h1 { font-size: 20px; margin: 0 0 2px; }
              .range { color: #666; font-size: 13px; margin-bottom: 16px; }
              table { width: 100%; border-collapse: collapse; font-size: 13px; }
              th, td { padding: 8px 8px; text-align: right; border-bottom: 1px solid #eee; }
              th { color: #888; font-weight: 600; font-size: 11px; text-transform: uppercase; }
              .l { text-align: left; }
              tr.train { background: #fbf2e8; }
              .b { display: inline-block; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 10px; }
              .b.train { background: #c27a3a; color: #fff; }
              .b.rest { background: #e6e6ea; color: #666; }
              .muted { color: #999; font-size: 11.5px; }
              .legend { margin-top: 14px; color: #777; font-size: 11.5px; }
              .foot { margin-top: 22px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety — tydzień</h1>
            <div class="range">${esc(week.rangeLabel)}</div>
            <table>
              <thead><tr>
                <th class="l">Dzień</th><th class="l">Typ</th>
                <th>kcal</th><th>Białko</th><th>Węgle</th><th>Tłuszcz</th><th class="l">Na posiłek</th>
              </tr></thead>
              <tbody>$rows</tbody>
            </table>
            <div class="legend">🏋 dzień treningowy = więcej węglowodanów, mniej tłuszczu (ta sama liczba kcal). „Wolne" = odwrotnie.</div>
            <div class="foot">Wygenerowano w GymTracker</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety — tydzień"
}
