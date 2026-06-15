package pl.filebit.gymtracker.ui.diet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import pl.filebit.gymtracker.util.DailyMacroGoal

/** Produkt w posiłku. trainDeltaG: ile dodać(+)/odjąć(−) w dzień treningowy (0 = bez zmian). */
data class PlanProduct(val name: String, val grams: Int, val trainDeltaG: Int = 0)

/** Posiłek jako kolumna. */
data class PlanMeal(val label: String, val kcal: Int, val products: List<PlanProduct>)

/**
 * Plan diety do druku. Menu jest takie samo każdego dnia — pokazujemy je RAZ (kolumny posiłków).
 * Różnicę dni treningowych wpisujemy WPROST przy produktach: zielone „+X g" (dodaj w trening),
 * pomarańczowe „−Y g" (odejmij w trening). kcal i białko bez zmian.
 */
data class WeeklyPlan(
    val menuLabel: String,
    val trainingDaysLabel: String,
    val meals: List<PlanMeal>,
    val trainGoal: DailyMacroGoal?,
    val restGoal: DailyMacroGoal?
)

/**
 * v2.54.0 — „Drukuj plan": kolumny posiłków + delty trening/wolne wprost przy produktach.
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
        val cols = if (plan.meals.isEmpty()) {
            "<div class=\"empty\">— brak rozpisanych posiłków — zaloguj posiłki, żeby wydrukować plan —</div>"
        } else buildString {
            append("<div class=\"cols\">")
            for (m in plan.meals) {
                append("<div class=\"col\"><div class=\"ch\">").append(esc(m.label))
                append("<span class=\"ck\">").append(m.kcal).append(" kcal</span></div><ul>")
                for (p in m.products) {
                    append("<li>").append(esc(p.name)).append(" <b>").append(p.grams).append(" g</b>")
                    if (p.trainDeltaG > 0) {
                        append(" <span class=\"d up\">+").append(p.trainDeltaG).append(" g</span>")
                    } else if (p.trainDeltaG < 0) {
                        append(" <span class=\"d down\">−").append(-p.trainDeltaG).append(" g</span>")
                    }
                    append("</li>")
                }
                append("</ul></div>")
            }
            append("</div>")
        }
        val goalLine = if (plan.trainGoal != null && plan.restGoal != null) {
            val t = plan.trainGoal; val r = plan.restGoal
            "Cele — <b>trening:</b> ${t.kcal} kcal · B${t.proteinG} W${t.carbsG} T${t.fatG} g · " +
                "<b>wolne:</b> W${r.carbsG} T${r.fatG} g (kcal i białko bez zmian)"
        } else ""

        return """
            <!DOCTYPE html><html lang="pl"><head><meta charset="utf-8">
            <style>
              * { box-sizing: border-box; }
              body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 22px; font-size: 13px; }
              h1 { font-size: 20px; margin: 0 0 2px; }
              .sub { color: #666; font-size: 12.5px; margin-bottom: 14px; }
              .cols { display: flex; gap: 10px; flex-wrap: wrap; }
              .col { flex: 1 1 180px; border: 1px solid #e6e6ea; border-radius: 8px; padding: 10px; page-break-inside: avoid; }
              .ch { font-weight: 700; border-bottom: 2px solid #c27a3a; padding-bottom: 4px; margin-bottom: 6px; }
              .ck { float: right; color: #888; font-weight: 400; font-size: 11px; }
              ul { margin: 0; padding-left: 16px; }
              li { margin: 4px 0; font-size: 12px; }
              .d { font-weight: 700; font-size: 11.5px; white-space: nowrap; }
              .d.up { color: #2e7d32; }
              .d.down { color: #b5532a; }
              .legend { margin-top: 14px; background: #fbf2e8; border: 1px solid #ecd9c2; border-radius: 8px; padding: 9px 12px; font-size: 12px; }
              .legend .up { color: #2e7d32; font-weight: 700; }
              .legend .down { color: #b5532a; font-weight: 700; }
              .goals { margin-top: 8px; color: #555; font-size: 12px; }
              .foot { margin-top: 18px; color: #aaa; font-size: 10px; }
            </style></head><body>
            <h1>Plan diety</h1>
            <div class="sub">${esc(plan.menuLabel)} · dni treningowe: <b>${esc(plan.trainingDaysLabel)}</b></div>
            $cols
            <div class="legend">
              <b>Dni treningowe</b> (Pn/Śr/Pt): zastosuj zmiany przy produktach —
              <span class="up">zielone +g dodaj</span>, <span class="down">pomarańczowe −g odejmij</span>.
              W <b>dni wolne</b> jedz wersję bazową (bez tych zmian). Więcej węgli na trening, mniej tłuszczu — kcal i białko bez zmian.
            </div>
            <div class="goals">$goalLine</div>
            <div class="foot">Wygenerowano w GymTracker</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val JOB_NAME = "Plan diety"
}
