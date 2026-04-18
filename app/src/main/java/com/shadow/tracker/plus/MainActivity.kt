package com.shadow.tracker.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shadow.tracker.plus.data.local.AppDatabase
import com.shadow.tracker.plus.ui.components.FilterPanel
import com.shadow.tracker.plus.ui.components.SettingsScreen
import com.shadow.tracker.plus.ui.components.TokenList
import com.shadow.tracker.plus.ui.components.DebugLogList
import com.shadow.tracker.plus.ui.theme.ShadowTrackerPlusTheme
import com.shadow.tracker.plus.ui.viewmodel.ShadowViewModel
import com.shadow.tracker.plus.ui.viewmodel.ShadowViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val viewModelFactory = ShadowViewModelFactory(database.tokenDao())
        
        setContent {
            ShadowTrackerPlusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShadowTrackerApp(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun ShadowTrackerApp(
    viewModelFactory: ShadowViewModelFactory
) {
    val viewModel: ShadowViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = viewModelFactory)
    val settingsState by viewModel.settingsState.collectAsState()
    val tokens by viewModel.filteredTokens.collectAsState()
    val debugLogs by viewModel.debugLogs.collectAsState()
    
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterPanel(
                    state = settingsState,
                    onWalModeChange = viewModel::updateWalMode,
                    onLiquidityFilterChange = viewModel::updateLiquidityFilter,
                    onMinFdvChange = viewModel::updateMinFdv,
                    onTimeSelectionChange = viewModel::updateTimeSelection,
                    onMinVolumeChange = viewModel::updateMinVolume,
                    onMinTxnsChange = viewModel::updateMinTxns,
                    onMinTradersChange = viewModel::updateMinTraders,
                    onMaxAgeHoursChange = viewModel::updateMaxAgeHours,
                    onMaxAtlChange = viewModel::updateMaxAtlChange,
                    onForceScan = viewModel::forceScan,
                    onNavigateSettings = { navController.navigate("settings") }
                )
                
                TokenList(tokens = tokens, modifier = Modifier.weight(1f))
                
                DebugLogList(logs = debugLogs)
            }
        }
        composable("settings") {
            SettingsScreen(
                state = settingsState,
                onHeliusApiKeyChange = viewModel::updateHeliusApiKey,
                onBirdeyeApiKeyChange = viewModel::updateBirdeyeApiKey,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
