package com.mmnuig.shoplistcc

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mmnuig.shoplistcc.ui.ConfirmDialog
import com.mmnuig.shoplistcc.ui.HomeScreen
import com.mmnuig.shoplistcc.ui.PlanScreen
import com.mmnuig.shoplistcc.ui.ShopScreen
import com.mmnuig.shoplistcc.ui.theme.ShopListCCTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

enum class Screen { Home, Plan, Shop }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ShopViewModel = viewModel()
            val largeText by viewModel.largeText.collectAsState()
            val themePref by viewModel.themePref.collectAsState()
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            ShopListCCTheme(darkTheme = darkTheme, largeText = largeText) {
                ShopListApp(viewModel, largeText, themePref)
            }
        }
    }
}

@Composable
fun ShopListApp(
    viewModel: ShopViewModel,
    largeText: Boolean,
    themePref: String
) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    // Page each mode opens on: 0 from Home, or the matching category page when
    // jumping between Plan and Shop.
    var planStart by rememberSaveable { mutableIntStateOf(0) }
    var shopStart by rememberSaveable { mutableIntStateOf(0) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri -> uri?.let { viewModel.exportTo(it) { msg -> toast(msg) } } }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            onShop = { shopStart = 0; screen = Screen.Shop },
            onPlan = { planStart = 0; screen = Screen.Plan },
            onImport = {
                importLauncher.launch(arrayOf(XLSX_MIME, "application/octet-stream"))
            },
            onExport = {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                exportLauncher.launch("Shop-$today.xlsx")
            },
            largeText = largeText,
            theme = themePref,
            onToggleLargeText = { viewModel.setLargeText(!largeText) },
            onCycleTheme = {
                viewModel.setTheme(
                    when (themePref) {
                        "system" -> "light"
                        "light" -> "dark"
                        else -> "system"
                    }
                )
            }
        )
        Screen.Plan -> PlanScreen(
            viewModel,
            onHome = { screen = Screen.Home },
            initialPage = planStart,
            onSwitchToShop = { categoryIndex ->
                shopStart = categoryIndex.coerceAtLeast(0)
                screen = Screen.Shop
            }
        )
        Screen.Shop -> ShopScreen(
            viewModel,
            onHome = { screen = Screen.Home },
            initialPage = shopStart,
            onSwitchToPlan = { categoryIndex ->
                planStart = categoryIndex + 1
                screen = Screen.Plan
            }
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Import shopping list",
            message = "This replaces ALL current categories and items with the file contents. Continue?",
            confirmLabel = "Replace",
            onConfirm = { viewModel.importFrom(uri) { msg -> toast(msg) } },
            onDismiss = { pendingImport = null }
        )
    }
}
