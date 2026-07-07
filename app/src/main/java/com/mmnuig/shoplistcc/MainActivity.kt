package com.mmnuig.shoplistcc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mmnuig.shoplistcc.ui.HomeScreen
import com.mmnuig.shoplistcc.ui.theme.ShopListCCTheme

enum class Screen { Home, Plan, Shop }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShopListCCTheme {
                ShopListApp()
            }
        }
    }
}

@Composable
fun ShopListApp() {
    val viewModel: ShopViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            onShop = { screen = Screen.Shop },
            onPlan = { screen = Screen.Plan },
            onImport = { /* wired in import/export milestone */ },
            onExport = { /* wired in import/export milestone */ }
        )
        Screen.Plan -> Text("Plan - coming soon")
        Screen.Shop -> Text("Shop - coming soon")
    }
}
