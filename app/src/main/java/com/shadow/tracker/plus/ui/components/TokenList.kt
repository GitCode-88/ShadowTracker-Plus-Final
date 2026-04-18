package com.shadow.tracker.plus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadow.tracker.plus.data.local.TokenEntity
import com.shadow.tracker.plus.ui.theme.MatrixDarkGray
import com.shadow.tracker.plus.ui.theme.MatrixNeonGreen
import com.shadow.tracker.plus.ui.theme.MatrixRed

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext

@Composable
fun TokenList(
    tokens: List<TokenEntity>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    if (tokens.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text = "Scanning Matrix...", color = MatrixNeonGreen, fontSize = 18.sp)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tokens, key = { it.mintAddress }) { token ->
                TokenCard(token = token)
            }
        }
    }
}

@Composable
fun DebugLogList(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(logs) { log ->
            Text(
                text = "> $log",
                color = MatrixNeonGreen.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TokenCard(token: TokenEntity) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MatrixDarkGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = token.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = token.symbol,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Price: $${String.format("%.6f", token.priceUsd)}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = "Liq: $${token.liquidityUsd.toInt()}",
                        color = MatrixNeonGreen,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Vol: $${token.volume24hUsd.toInt()}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "MC: $${token.marketCapUsd.toInt()}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    val atlText = if (token.atlUsd > 0.0) {
                        val change = ((token.priceUsd - token.atlUsd) / token.atlUsd) * 100
                        "ATL: +${String.format("%.2f", change)}%"
                    } else {
                        "ATL: N/A"
                    }
                    
                    Text(
                        text = atlText,
                        color = if (token.atlUsd > 0.0) MatrixNeonGreen else Color.Gray,
                        fontSize = 12.sp
                    )
                    
                    val ageMs = System.currentTimeMillis() - token.pairCreatedAt
                    val ageHours = (ageMs / (1000 * 60 * 60)).toInt()
                    val ageText = if (token.pairCreatedAt > 0) "Age: ${ageHours}h" else "Age: N/A"
                    Text(
                        text = ageText,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (token.isSafe) "SAFE" else "RISK",
                            color = if (token.isSafe) MatrixNeonGreen else MatrixRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (token.isMutable) "[MUTABLE]" else "[LOCKED]",
                            color = if (token.isMutable) MatrixRed else MatrixNeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Interaction Row (Copy CA & Links)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "CA: " + token.mintAddress.take(4) + "..." + token.mintAddress.takeLast(4),
                    color = MatrixNeonGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Contract Address", token.mintAddress)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CA Copied!", Toast.LENGTH_SHORT).show()
                    }
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://birdeye.so/token/\${token.mintAddress}?chain=solana"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Birdeye", fontSize = 10.sp, color = Color.White)
                    }
                    
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dexscreener.com/solana/\${token.mintAddress}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("DexScreener", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
