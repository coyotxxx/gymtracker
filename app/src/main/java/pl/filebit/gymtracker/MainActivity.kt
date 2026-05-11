package pl.filebit.gymtracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import pl.filebit.gymtracker.ui.navigation.AppNavigation
import pl.filebit.gymtracker.ui.splash.SplashScreen
import pl.filebit.gymtracker.ui.theme.GymTrackerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notifications są nice-to-have, brak zgody nie blokuje UI */ }

    /**
     * v1.14.1 — pending deep link route z intent extras (push notifications z workerów).
     * Konsumowane raz przez AppNavigation, potem reset na null.
     * mutableStateOf żeby Compose recompose'ował NavHost gdy zmiana (np. onNewIntent).
     */
    private var pendingNavigation by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        // v1.14.1: parse intent.extras z push (luka K4)
        pendingNavigation = IntentNavigationParser.parseInitialNavigation(intent)

        setContent {
            GymTrackerTheme {
                // Splash pokazuje się raz na proces — rememberSaveable
                // przeżyje rotacje, ale nie kill processu (co jest OK).
                var showSplash by rememberSaveable { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    AppNavigation(
                        initialNavigation = pendingNavigation,
                        onInitialNavigationConsumed = { pendingNavigation = null }
                    )
                }
            }
        }
    }

    /**
     * v1.14.1 — re-handle deep link gdy aplikacja działa i przychodzi nowy intent
     * (np. user kliknie push gdy app już w tle).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentNavigationParser.parseInitialNavigation(intent)?.let { route ->
            pendingNavigation = route
        }
    }
}
