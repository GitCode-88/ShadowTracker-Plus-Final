package com.shadow.tracker.plus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.shadow.tracker.plus.ui.theme.MatrixDarkGray
import com.shadow.tracker.plus.ui.theme.MatrixNeonGreen
import com.shadow.tracker.plus.ui.viewmodel.ShadowSettingsState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    state: ShadowSettingsState,
    onWalModeChange: (Boolean) -> Unit,
    onLiquidityFilterChange: (Float) -> Unit,
    onMinVolumeChange: (Float) -> Unit,
    onMaxAtlChange: (Float) -> Unit,
    onHeliusApiKeyChange: (String) -> Unit,
    onBirdeyeApiKeyChange: (String) -> Unit,
    onCryptoRankApiKeyChange: (String) -> Unit,
    onForceScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MatrixDarkGray)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM MATRIX SETTINGS",
                    color = MatrixNeonGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = onForceScan,
                    enabled = !state.isScanning,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MatrixNeonGreen),
                    border = BorderStroke(1.dp, MatrixNeonGreen)
                ) {
                    if (state.isScanning) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing)
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scanning",
                            modifier = Modifier.rotate(rotation),
                            tint = MatrixNeonGreen
                        )
                    } else {
                        Text("SCAN MATRIX")
                    }
                }
            }

            // WAL-Mode Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "WAL-Mode (Performance Boost)", color = Color.White)
                Switch(
                    checked = state.isWalModeEnabled,
                    onCheckedChange = onWalModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MatrixNeonGreen,
                        checkedTrackColor = MatrixNeonGreen.copy(alpha = 0.3f)
                    )
                )
            }

            // Liquidity Filter Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Min Liquidity: \$${state.liquidityFilterUsd.roundToInt()}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Slider(
                    value = state.liquidityFilterUsd,
                    onValueChange = onLiquidityFilterChange,
                    valueRange = 0f..100000f,
                    colors = SliderDefaults.colors(
                        thumbColor = MatrixNeonGreen,
                        activeTrackColor = MatrixNeonGreen
                    )
                )
            }

            // Min 24h Volume Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Min 24h Volume: \$${state.minVolume24hUsd.roundToInt()}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Slider(
                    value = state.minVolume24hUsd,
                    onValueChange = onMinVolumeChange,
                    valueRange = 0f..500000f,
                    colors = SliderDefaults.colors(
                        thumbColor = MatrixNeonGreen,
                        activeTrackColor = MatrixNeonGreen
                    )
                )
            }

            // Max ATL Change Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Max +% from ATL: ${state.maxPriceChangeFromAtl.roundToInt()}%",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Slider(
                    value = state.maxPriceChangeFromAtl,
                    onValueChange = onMaxAtlChange,
                    valueRange = 0f..2000f,
                    colors = SliderDefaults.colors(
                        thumbColor = MatrixNeonGreen,
                        activeTrackColor = MatrixNeonGreen
                    )
                )
            }

            // API Keys
            OutlinedTextField(
                value = state.heliusApiKey,
                onValueChange = onHeliusApiKeyChange,
                label = { Text("Helius API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.birdeyeApiKey,
                onValueChange = onBirdeyeApiKeyChange,
                label = { Text("Birdeye API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.cryptoRankApiKey,
                onValueChange = onCryptoRankApiKeyChange,
                label = { Text("CryptoRank API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
