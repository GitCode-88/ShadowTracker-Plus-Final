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

    // Derived state: Filter tokens from the database based on the current liquidity filter
    val filteredTokens: StateFlow<List<TokenEntity>> = combine(
        tokenDao.getAllTokens(),
        _settingsState
    ) { tokens, settings ->
        tokens.filter { it.liquidityUsd >= settings.liquidityFilterUsd }
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
            // Stage 1: Helius (Live Feed)
            val requestBody = mapOf("limit" to 10, "displayOptions" to mapOf("showFungible" to true))
            val heliusResponse = ApiProvider.heliusApi.searchAssets(heliusKey, requestBody)
            val tokens = heliusResponse.items ?: emptyList()

            for (tokenItem in tokens) {
                val mintAddress = tokenItem.id ?: continue
                val symbol = tokenItem.content?.metadata?.symbol ?: "UNKNOWN"
                val name = tokenItem.content?.metadata?.name ?: "Unknown Token"

                // Check existing cache
                val cachedToken = tokenDao.getToken(mintAddress)
                
                // Stage 2: Birdeye (Liquidity Filter & Live Price)
                val birdeyeResponse = try {
                    ApiProvider.birdeyeApi.getTokenOverview(birdeyeKey, mintAddress)
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Birdeye failed for \$mintAddress: \${e.message}")
                    continue
                }
                
                val liquidity = birdeyeResponse.data?.liquidity ?: 0.0
                val price = birdeyeResponse.data?.price ?: 0.0
                
                // Immediate fallback/continue if liquidity is below filter
                if (liquidity < currentState.liquidityFilterUsd) {
                    continue
                }

                // Stage 3: CryptoRank (ATL Data)
                val now = System.currentTimeMillis()
                val needsAtlUpdate = forceRefresh || cachedToken == null || (now - cachedToken.atlLastUpdated > 24 * 60 * 60 * 1000)
                
                var atlUsd = cachedToken?.atlUsd ?: 0.0
                var atlLastUpdated = cachedToken?.atlLastUpdated ?: 0L

                if (needsAtlUpdate) {
                    try {
                        // First find the coin ID
                        val coinsResponse = ApiProvider.cryptoRankApi.getCoins(cryptoRankKey, symbol)
                        val coinData = coinsResponse.data?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                        
                        if (coinData != null) {
                            val priceResponse = ApiProvider.cryptoRankApi.getPrice(cryptoRankKey, coinData.id.toString())
                            val atl = priceResponse.data?.get(coinData.id.toString())?.atlPrice?.usd
                            if (atl != null) {
                                atlUsd = atl
                                atlLastUpdated = now
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ShadowViewModel", "CryptoRank failed for \$symbol: \${e.message}")
                    }
                }

                // Persist to Room DB
                val updatedToken = TokenEntity(
                    mintAddress = mintAddress,
                    name = name,
                    symbol = symbol,
                    liquidityUsd = liquidity,
                    priceUsd = price,
                    atlUsd = atlUsd,
                    atlLastUpdated = atlLastUpdated,
                    isSafe = true,
                    riskScore = 0
                )
                tokenDao.insertToken(updatedToken)
            }
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Failed to fetch tokens: \${e.message}")
        }
    }

    fun updateWalMode(enabled: Boolean) {
        _settingsState.update { it.copy(isWalModeEnabled = enabled) }
    }
    fun updateLiquidityFilter(value: Float) {
        _settingsState.update { it.copy(liquidityFilterUsd = value) }
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
