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

/** Jeden posiłek w planie dnia — zwięzła linia (produkty w jednym wierszu). */
data class WeekMealLine(
    val label: String,
    val kcal: Int,
    /** Produkty z gramaturą: „Płatki owsiane 61 g, WPI 37 g, …". */
    val items: String
)

/** Jeden dzień w kompaktowym planie tygodniowym. */
data class WeekDayPlan(
    val dateMs: Long,
    val dayLabel: String,
    val isTraining: Boolean,
    val mealsPerDay: Int,
    val goal: DailyMacroGoal?,
    val meals: List<WeekMealLine> = emptyList()
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
        val daysHtml = buildString {
            for (d in week.days) {
                val cls = if (d.isTraining) " train" else ""
                val badge = if (d.isTraining) "<span class=\"b train\">🏋 Trening</span>"
                    else "<span class=\"b rest\">Wolne</span>"
                val macros = d.goal?.let {
                    " · <b>${it.kcal} kcal</b> · B${it.proteinG} W${it.carbsG} T${it.fatG} g"
                } ?: ""
                append("<div class=\"day$cls\">")
                append("<div class=\"dhead\">").append(esc(d.dayLabel)).append(" ")
                    .append(badge).append(macros).append("</div>")
                if (d.meals.isEmpty()) {
                    append("<div class=\"empty\">— brak rozpisanych posiłków —</div>")
                } else {
                    for (m in d.meals) {
                        append("<div class=\"meal\"><span class=\"mn\">")
                        append(esc(m.label)).append("</span> <span class=\"mk\">")
                        append(m.kcal).append(" kcal</span><div class=\"items\">")
                        append(esc(m.items)).append("</div></div>")
                    }
                }
                append("</div>")
            }
        }
        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 22px; font-size: 12.5px; }
              h1 { font-size: 19px; margin: 0 0 2px; }
              .range { color: #666; font-size: 12.5px; margin-bottom: 14px; }
              .day { border: 1px solid #e6e6ea; border-radius: 8px; padding: 9px 12px; margin-bottom: 9px; page-break-inside: avoid; }
              .day.train { background: #fbf2e8; border-color: #ecd9c2; }
              .dhead { font-size: 13.5px; margin-bottom: 6px; }
              .b { display: inline-block; font-size: 10.5px; font-weight: 700; padding: 1px 7px; border-radius: 9px; }
              .b.train { background: #c27a3a; color: #fff; }
              .b.rest { background: #e6e6ea; color: #666; }
              .meal { padding: 3px 0; border-top: 1px solid #00000010; }
              .meal:first-of-type { border-top: none; }
              .mn { font-weight: 700; }
              .mk { color: #888; font-size: 11px; margin-left: 6px; }
              .items { color: #444; font-size: 11.5px; margin-top: 1px; }
              .empty { color: #aaa; font-size: 11.5px; }
              .legend { margin-top: 12px; color: #777; font-size: 11px; }
              .foot { margin-top: 18px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety — tydzień</h1>
            <div class="range">${esc(week.rangeLabel)}</div>
            $daysHtml
            <div class="legend">🏋 dzień treningowy = więcej węglowodanów, mniej tłuszczu (ta sama liczba kcal). „Wolne" = odwrotnie.</div>
            <div class="foot">Wygenerowano w GymTracker</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety — tydzień"
}
