package pl.filebit.gymtracker.ui.diet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pl.filebit.gymtracker.util.DailyMacroGoal

/** Jeden posiłek w menu — zwięzła linia (produkty w jednym wierszu). */
data class WeekMealLine(
    val label: String,
    val kcal: Int,
    val items: String
)

/**
 * Plan diety do druku. Menu jest takie samo każdego dnia — pokazujemy je RAZ, a różnicę
 * dni treningowych vs wolnych (carb cycling) jako dwie kolumny celów.
 */
data class WeeklyPlan(
    val menuLabel: String,
    val trainingDaysLabel: String,
    val meals: List<WeekMealLine>,
    val trainGoal: DailyMacroGoal?,
    val restGoal: DailyMacroGoal?
)

/**
 * v2.53.0 — „Drukuj plan": menu raz + cele w kolumnach (trening / wolne). Render → systemowy
 * PrintManager Androida (Drukuj / Zapisz jako PDF). Offline, bez backendu.
 */
object DietPlanPrinter {

    private var pending: WebView? = null

    fun print(context: Context, plan: WeeklyPlan) {
        val html = buildHtml(plan)
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

    fun buildHtml(plan: WeeklyPlan): String {
        val t = plan.trainGoal
        val r = plan.restGoal
        // Wiersz tabeli celów; podświetla różnicę między kolumnami.
        fun row(label: String, tv: String, rv: String): String {
            val diff = if (tv != rv) " class=\"diff\"" else ""
            return "<tr$diff><td class=\"l\">$label</td><td>$tv</td><td>$rv</td></tr>"
        }
        val goalTable = if (t != null && r != null) {
            """
            <table class="goals">
              <thead><tr><th class="l"></th><th>Dzień treningowy</th><th>Dzień wolny</th></tr></thead>
              <tbody>
                ${row("kcal", "${t.kcal}", "${r.kcal}")}
                ${row("Białko", "${t.proteinG} g", "${r.proteinG} g")}
                ${row("Węgle", "${t.carbsG} g", "${r.carbsG} g")}
                ${row("Tłuszcz", "${t.fatG} g", "${r.fatG} g")}
              </tbody>
            </table>
            """.trimIndent()
        } else ""

        val mealsHtml = if (plan.meals.isEmpty()) {
            "<div class=\"empty\">— brak rozpisanych posiłków —</div>"
        } else buildString {
            for (m in plan.meals) {
                append("<div class=\"meal\"><span class=\"mn\">").append(esc(m.label))
                append("</span> <span class=\"mk\">").append(m.kcal).append(" kcal</span>")
                append("<div class=\"items\">").append(esc(m.items)).append("</div></div>")
            }
        }

        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 24px; font-size: 13px; }
              h1 { font-size: 20px; margin: 0 0 2px; }
              .sub { color: #666; font-size: 12.5px; margin-bottom: 16px; }
              h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .5px; color: #888; margin: 18px 0 8px; }
              table.goals { border-collapse: collapse; font-size: 13px; min-width: 360px; }
              table.goals th, table.goals td { padding: 6px 16px; text-align: right; border-bottom: 1px solid #eee; }
              table.goals th { color: #888; font-size: 11px; text-transform: uppercase; }
              table.goals th:nth-child(2) { color: #c27a3a; }
              .l { text-align: left; }
              tr.diff td { font-weight: 700; }
              tr.diff td:nth-child(2) { color: #c27a3a; }
              .train-days { background: #fbf2e8; border: 1px solid #ecd9c2; border-radius: 8px; padding: 8px 12px; font-size: 12.5px; margin-bottom: 4px; }
              .meal { padding: 6px 0; border-top: 1px solid #eee; }
              .meal:first-of-type { border-top: none; }
              .mn { font-weight: 700; }
              .mk { color: #888; font-size: 11.5px; margin-left: 6px; }
              .items { color: #444; font-size: 12px; margin-top: 2px; }
              .empty { color: #aaa; }
              .note { margin-top: 10px; color: #777; font-size: 11.5px; }
              .foot { margin-top: 22px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety</h1>
            <div class="sub">${esc(plan.menuLabel)}</div>

            <div class="train-days">🏋 Dni treningowe: <b>${esc(plan.trainingDaysLabel)}</b></div>
            <h2>Cele dzienne</h2>
            $goalTable
            <div class="note">Menu jest takie samo każdego dnia — różni się tylko cel: w dni treningowe więcej węglowodanów, mniej tłuszczu (ta sama liczba kcal i białka).</div>

            <h2>Menu (codziennie)</h2>
            $mealsHtml

            <div class="foot">Wygenerowano w GymTracker</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety"
}
