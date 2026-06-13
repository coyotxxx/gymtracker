package pl.filebit.gymtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import pl.filebit.gymtracker.data.coach.CoachAction
import pl.filebit.gymtracker.data.coach.CoachActionType
import pl.filebit.gymtracker.data.coach.CoachPriority
import pl.filebit.gymtracker.data.coach.CoachReaction
import pl.filebit.gymtracker.data.coach.CoachVerdict
import pl.filebit.gymtracker.ui.theme.AccentOrange
import pl.filebit.gymtracker.ui.theme.DarkOnSurface
import pl.filebit.gymtracker.ui.theme.DarkOnSurfaceVariant
import pl.filebit.gymtracker.ui.theme.ErrorRed

/**
 * v2.34.0 (U4b) — JEDNA karta coacha. Pokazuje dominujący werdykt z CoachOrchestratora
 * (trening+dieta+regeneracja w jednym głosie) + akcje. Zastępuje rozproszone karty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachCard(
    verdict: CoachVerdict,
    onAction: (CoachReaction, CoachActionType) -> Unit
) {
    val primary = verdict.primary ?: return
    val accent = priorityColor(primary.priority)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "COACH · ${priorityLabel(primary.priority)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp
                ),
                color = accent,
                modifier = Modifier.weight(1f)
            )
            if (verdict.secondary.isNotEmpty()) {
                Text(
                    "+${verdict.secondary.size} więcej",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(primary.title, color = DarkOnSurface, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            primary.message,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = DarkOnSurface
        )
        if (primary.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                primary.actions.forEach { a -> ActionButton(a, accent) { onAction(primary, a.type) } }
            }
        }
    }
}

@Composable
private fun ActionButton(action: CoachAction, accent: Color, onClick: () -> Unit) {
    // Akcja główna (pierwsza, „zastosuj…") = wypełniony przycisk; reszta = outline.
    val isPrimary = action.type in PRIMARY_ACTIONS
    if (isPrimary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
        ) { Text(action.label) }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(action.label, color = DarkOnSurface)
        }
    }
}

private val PRIMARY_ACTIONS = setOf(
    CoachActionType.APPLY_DELOAD, CoachActionType.APPLY_REFEED, CoachActionType.APPLY_KCAL_ADJUST,
    CoachActionType.START_WORKOUT, CoachActionType.REST_INJURY, CoachActionType.RETURN_LIGHT,
    CoachActionType.SIMPLIFY_PLAN
)

private fun priorityColor(p: CoachPriority): Color = when (p) {
    CoachPriority.HEALTH -> ErrorRed
    CoachPriority.RECOVERY -> AccentOrange
    CoachPriority.RETURN -> AccentOrange
    CoachPriority.CONSISTENCY -> AccentOrange
    CoachPriority.OPTIMIZATION -> AccentOrange
}

private fun priorityLabel(p: CoachPriority): String = when (p) {
    CoachPriority.HEALTH -> "ZDROWIE"
    CoachPriority.RECOVERY -> "REGENERACJA"
    CoachPriority.RETURN -> "POWRÓT"
    CoachPriority.CONSISTENCY -> "SYSTEMATYCZNOŚĆ"
    CoachPriority.OPTIMIZATION -> "OPTYMALIZACJA"
}
