package pl.filebit.gymtracker.ui.diet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pl.filebit.gymtracker.util.DailyMacroGoal

/** Produkt w posiłku (gramatura już konkretna dla danego typu dnia). */
data class PlanProduct(val name: String, val grams: Int)

/** Posiłek jako kolumna. */
data class PlanMeal(val label: String, val kcal: Int, val products: List<PlanProduct>)

/** Pełny wariant dnia (treningowy / nietreningowy). */
data class DayMenu(
    val title: String,
    val daysLabel: String,
    val goal: DailyMacroGoal?,
    val meals: List<PlanMeal>,
    val isTraining: Boolean
)

/**
 * Plan diety: DWA pełne warianty (dzień treningowy / nietreningowy), każdy w 3 kolumnach.
 * Gramatury już dostosowane (carb cycling) — bez liczenia w głowie.
 */
data class WeeklyPlan(
    val menuLabel: String,
    val training: DayMenu,
    val rest: DayMenu
)

/**
 * v2.55.0 — „Drukuj plan": dwa pełne warianty dnia (treningowy/nietreningowy), kolumny posiłków.
 * Render → systemowy PrintManager Androida (Drukuj / Zapisz jako PDF). Offline, bez backendu.
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
        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 20px; font-size: 12.5px; }
              h1 { font-size: 20px; margin: 0 0 2px; }
              .sub { color: #666; font-size: 12.5px; margin-bottom: 14px; }
              .section { border-radius: 8px; padding: 12px; margin-bottom: 14px; page-break-inside: avoid; border: 1px solid #e6e6ea; }
              .section.train { background: #fbf2e8; border-color: #ecd9c2; }
              .shead { font-size: 15px; font-weight: 800; margin-bottom: 2px; }
              .sgoal { color: #555; font-size: 11.5px; margin-bottom: 10px; }
              .badge { display: inline-block; font-size: 10.5px; font-weight: 700; padding: 1px 8px; border-radius: 9px; margin-left: 6px; vertical-align: middle; }
              .badge.train { background: #c27a3a; color: #fff; }
              .badge.rest { background: #e6e6ea; color: #666; }
              .cols { display: flex; gap: 10px; flex-wrap: wrap; }
              .col { flex: 1 1 170px; background: #fff; border: 1px solid #e6e6ea; border-radius: 7px; padding: 9px; }
              .ch { font-weight: 700; border-bottom: 2px solid #c27a3a; padding-bottom: 4px; margin-bottom: 5px; }
              .ck { float: right; color: #888; font-weight: 400; font-size: 11px; }
              ul { margin: 0; padding-left: 16px; }
              li { margin: 3px 0; font-size: 12px; }
              .empty { color: #aaa; }
              .foot { margin-top: 14px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety</h1>
            <div class="sub">${esc(plan.menuLabel)}</div>
            ${section(plan.training)}
            ${section(plan.rest)}
            <div class="foot">Wygenerowano w GymTracker. Kcal i białko są takie same w obu wariantach — różnią się węgle i tłuszcz (carb cycling).</div>
            </body></html>
        """.trimIndent()
    }

    private fun section(day: DayMenu): String {
        val badge = if (day.isTraining) "<span class=\"badge train\">🏋 Trening</span>"
            else "<span class=\"badge rest\">Wolne</span>"
        val goalLine = day.goal?.let {
            "Cel: ${it.kcal} kcal · Białko ${it.proteinG} g · Węgle ${it.carbsG} g · Tłuszcz ${it.fatG} g"
        } ?: ""
        val cols = if (day.meals.isEmpty()) "<div class=\"empty\">— brak rozpisanych posiłków —</div>"
        else buildString {
            append("<div class=\"cols\">")
            for (m in day.meals) {
                append("<div class=\"col\"><div class=\"ch\">").append(esc(m.label))
                append("<span class=\"ck\">").append(m.kcal).append(" kcal</span></div><ul>")
                for (p in m.products) {
                    append("<li>").append(esc(p.name)).append(" <b>").append(p.grams).append(" g</b></li>")
                }
                append("</ul></div>")
            }
            append("</div>")
        }
        val cls = if (day.isTraining) "section train" else "section"
        return """
            <div class="$cls">
              <div class="shead">${esc(day.title)}$badge</div>
              <div class="sgoal">${esc(day.daysLabel)} · $goalLine</div>
              $cols
            </div>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety"
}
