package com.shadow.tracker.plus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadow.tracker.plus.ui.theme.MatrixDarkGray
import com.shadow.tracker.plus.ui.theme.MatrixNeonGreen
import com.shadow.tracker.plus.ui.viewmodel.ShadowSettingsState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    state: ShadowSettingsState,
    onWalModeChange: (Boolean) -> Unit,
    onLiquidityFilterChange: (Float) -> Unit,
    onMinFdvChange: (Float) -> Unit,
    onTimeSelectionChange: (String) -> Unit,
    onMinVolumeChange: (Float) -> Unit,
    onMinTxnsChange: (Float) -> Unit,
    onMaxAtlChange: (Float) -> Unit,
    onForceScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

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
                    text = "DASHBOARD / FILTERS",
                    color = MatrixNeonGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("SCAN")
                        }
                    }
                }
            }

            if (expanded) {



                // Liquidity Filter Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Min Liquidity: \$${state.liquidityFilterUsd.roundToInt()}", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = state.liquidityFilterUsd,
                        onValueChange = onLiquidityFilterChange,
                        valueRange = 0f..100000f,
                        colors = SliderDefaults.colors(thumbColor = MatrixNeonGreen, activeTrackColor = MatrixNeonGreen)
                    )
                }

                // Min Volume Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Min Volume (24H): \$${state.minVolumeUsd.roundToInt()}", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = state.minVolumeUsd,
                        onValueChange = onMinVolumeChange,
                        valueRange = 0f..500000f,
                        colors = SliderDefaults.colors(thumbColor = MatrixNeonGreen, activeTrackColor = MatrixNeonGreen)
                    )
                }

                // Min FDV Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Min FDMC/MCap: \$${state.minFdvUsd.roundToInt()}", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = state.minFdvUsd,
                        onValueChange = onMinFdvChange,
                        valueRange = 0f..1000000f,
                        colors = SliderDefaults.colors(thumbColor = MatrixNeonGreen, activeTrackColor = MatrixNeonGreen)
                    )
                }
                
                // Min Txns Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Min TXNS (24H): ${state.minTxns.roundToInt()}", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = state.minTxns,
                        onValueChange = onMinTxnsChange,
                        valueRange = 0f..5000f,
                        colors = SliderDefaults.colors(thumbColor = MatrixNeonGreen, activeTrackColor = MatrixNeonGreen)
                    )
                }
                
                // Max ATL Change Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Max +% from ATL: ${state.maxPriceChangeFromAtl.roundToInt()}%", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = state.maxPriceChangeFromAtl,
                        onValueChange = onMaxAtlChange,
                        valueRange = 0f..2000f,
                        colors = SliderDefaults.colors(thumbColor = MatrixNeonGreen, activeTrackColor = MatrixNeonGreen)
                    )
                }
            } else {
                 Text(text = "Click to expand filters ", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
