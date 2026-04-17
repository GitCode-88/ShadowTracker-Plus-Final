package com.shadow.tracker.plus.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow.tracker.plus.data.local.TokenDao
import com.shadow.tracker.plus.data.local.TokenEntity
import com.shadow.tracker.plus.data.remote.provider.ApiProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShadowSettingsState(
    val isWalModeEnabled: Boolean = true,
    val liquidityFilterUsd: Float = 2000f,
    val minVolume24hUsd: Float = 0f,
    val maxPriceChangeFromAtl: Float = 500f, // in percentage
    val heliusApiKey: String = "",
    val birdeyeApiKey: String = "",
    val cryptoRankApiKey: String = "",
    val isScanning: Boolean = false
)

class ShadowViewModel(
    private val tokenDao: TokenDao
) : ViewModel() {

    private val _settingsState = MutableStateFlow(ShadowSettingsState())
    val settingsState: StateFlow<ShadowSettingsState> = _settingsState.asStateFlow()

    private var scanCooldownActive = false
    
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()
    
    private fun logDebug(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _debugLogs.update { current -> 
            val newLog = "[$timestamp] $message"
            (current + newLog).takeLast(50) // Keep last 50 logs
        }
    }

    // Derived state: Filter tokens from the database based on all filters
    val filteredTokens: StateFlow<List<TokenEntity>> = combine(
        tokenDao.getAllTokens(),
        _settingsState
    ) { tokens, settings ->
        tokens.filter { token ->
            val passesLiquidity = token.liquidityUsd >= settings.liquidityFilterUsd
            val passesVolume = token.volume24hUsd >= settings.minVolume24hUsd
            
            val priceChangePercent = if (token.atlUsd > 0) {
                ((token.priceUsd - token.atlUsd) / token.atlUsd) * 100.0
            } else {
                0.0
            }
            val passesAtlChange = priceChangePercent <= settings.maxPriceChangeFromAtl
            
            passesLiquidity && passesVolume && passesAtlChange
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        startLiveScan()
    }

    private fun startLiveScan() {
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    fetchLiveTokens(forceRefresh = false)
                }
                delay(15000) // Poll every 15 seconds
            }
        }
    }

    fun forceScan() {
        if (scanCooldownActive) return
        viewModelScope.launch {
            scanCooldownActive = true
            _settingsState.update { it.copy(isScanning = true) }
            
            fetchLiveTokens(forceRefresh = true)
            
            _settingsState.update { it.copy(isScanning = false) }
            // 3 seconds cooldown
            delay(3000)
            scanCooldownActive = false
        }
    }

    private suspend fun fetchLiveTokens(forceRefresh: Boolean) {
        val currentState = _settingsState.value
        val heliusKey = currentState.heliusApiKey
        val birdeyeKey = currentState.birdeyeApiKey
        val cryptoRankKey = currentState.cryptoRankApiKey

        if (heliusKey.isBlank() || birdeyeKey.isBlank() || cryptoRankKey.isBlank()) {
            Log.d("ShadowViewModel", "API keys not fully set. Skipping scan.")
            return
        }

        try {
            logDebug("Stage 1: Helius API Fetching...")
            // Stage 1: Helius (Live Feed)
            val requestBody = mapOf("limit" to 10, "displayOptions" to mapOf("showFungible" to true))
            val heliusResponse = ApiProvider.heliusApi.searchAssets(heliusKey, requestBody)
            val tokens = heliusResponse.items ?: emptyList()
            logDebug("Helius: Found \${tokens.size} tokens.")

            for (tokenItem in tokens) {
                val mintAddress = tokenItem.id ?: continue
                val symbol = tokenItem.content?.metadata?.symbol ?: "UNKNOWN"
                val name = tokenItem.content?.metadata?.name ?: "Unknown Token"

                // Check existing cache
                val cachedToken = tokenDao.getToken(mintAddress)
                
                // Stage 2: Birdeye (Liquidity Filter & Live Price)
                logDebug("Stage 2: Birdeye checking \$symbol...")
                val birdeyeResponse = try {
                    ApiProvider.birdeyeApi.getTokenOverview(birdeyeKey, mintAddress)
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Birdeye failed for \$mintAddress: \${e.message}")
                    logDebug("Birdeye error for \$symbol")
                    continue
                }
                
                val liquidity = birdeyeResponse.data?.liquidity ?: 0.0
                val price = birdeyeResponse.data?.price ?: 0.0
                val volume24h = birdeyeResponse.data?.v24hUSD ?: 0.0
                
                // Immediate fallback/continue if liquidity is below filter
                if (liquidity < currentState.liquidityFilterUsd) {
                    logDebug("Filtered out \$symbol (Liq: \$${liquidity.toInt()})")
                    continue
                }

                // Stage 3: CryptoRank (ATL Data)
                val now = System.currentTimeMillis()
                val needsAtlUpdate = forceRefresh || cachedToken == null || (now - cachedToken.atlLastUpdated > 24 * 60 * 60 * 1000)
                
                var atlUsd = cachedToken?.atlUsd ?: 0.0
                var athUsd = cachedToken?.athUsd ?: 0.0
                var atlLastUpdated = cachedToken?.atlLastUpdated ?: 0L

                if (needsAtlUpdate) {
                    logDebug("Stage 3: CryptoRank fetching \$symbol...")
                    try {
                        // First find the coin ID
                        val coinsResponse = ApiProvider.cryptoRankApi.getCoins(cryptoRankKey, symbol)
                        val coinData = coinsResponse.data?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                        
                        if (coinData != null) {
                            val priceResponse = ApiProvider.cryptoRankApi.getPrice(cryptoRankKey, coinData.id.toString())
                            val atl = priceResponse.data?.get(coinData.id.toString())?.atlPrice?.usd
                            val ath = priceResponse.data?.get(coinData.id.toString())?.athPrice?.usd
                            if (atl != null) {
                                atlUsd = atl
                                athUsd = ath ?: 0.0
                                atlLastUpdated = now
                                logDebug("Updated ATL for \$symbol: \$atl")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ShadowViewModel", "CryptoRank failed for \$symbol: \${e.message}")
                        logDebug("CryptoRank error for \$symbol")
                    }
                } else {
                    logDebug("Stage 3: Using cached ATL for \$symbol")
                }

                // Persist to Room DB
                val updatedToken = TokenEntity(
                    mintAddress = mintAddress,
                    name = name,
                    symbol = symbol,
                    liquidityUsd = liquidity,
                    priceUsd = price,
                    atlUsd = atlUsd,
                    athUsd = athUsd,
                    volume24hUsd = volume24h,
                    atlLastUpdated = atlLastUpdated,
                    isSafe = true,
                    isMutable = tokenItem.mutable ?: false,
                    riskScore = 0
                )
                tokenDao.insertToken(updatedToken)
                logDebug("Saved \$symbol to DB")
            }
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Failed to fetch tokens: \${e.message}")
            logDebug("Critical Error: \${e.message}")
        }
    }

    fun updateWalMode(enabled: Boolean) {
        _settingsState.update { it.copy(isWalModeEnabled = enabled) }
    }
    fun updateLiquidityFilter(value: Float) {
        _settingsState.update { it.copy(liquidityFilterUsd = value) }
    }
    fun updateMinVolume(value: Float) {
        _settingsState.update { it.copy(minVolume24hUsd = value) }
    }
    fun updateMaxAtlChange(value: Float) {
        _settingsState.update { it.copy(maxPriceChangeFromAtl = value) }
    }
    fun updateHeliusApiKey(key: String) {
        _settingsState.update { it.copy(heliusApiKey = key) }
    }
    fun updateBirdeyeApiKey(key: String) {
        _settingsState.update { it.copy(birdeyeApiKey = key) }
    }
    fun updateCryptoRankApiKey(key: String) {
        _settingsState.update { it.copy(cryptoRankApiKey = key) }
    }
}
