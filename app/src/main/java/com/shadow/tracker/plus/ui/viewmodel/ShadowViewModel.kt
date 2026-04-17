package com.shadow.tracker.plus.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow.tracker.plus.data.local.TokenDao
import com.shadow.tracker.plus.data.local.TokenEntity
import com.shadow.tracker.plus.data.remote.model.HeliusRpcRequest
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
        startWorkers()
    }

    private fun startWorkers() {
        // Worker A: Helius (Live Feed Scan)
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    workerHeliusScan()
                }
                delay(15000) // Poll every 15 seconds
            }
        }

        // Worker B: Birdeye (Liquidity & Price Updater)
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    workerBirdeyeScan()
                }
                delay(10000) // Poll every 10 seconds
            }
        }

        // Worker C: CryptoRank (ATL Updater)
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    workerCryptoRankScan(forceRefresh = false)
                }
                delay(60000) // Poll every 60 seconds
            }
        }
    }

    fun forceScan() {
        if (scanCooldownActive) return
        viewModelScope.launch {
            scanCooldownActive = true
            _settingsState.update { it.copy(isScanning = true) }
            
            // Execute all workers concurrently for force scan
            val heliusJob = launch { workerHeliusScan() }
            val birdeyeJob = launch { workerBirdeyeScan() }
            val cryptoRankJob = launch { workerCryptoRankScan(forceRefresh = true) }
            
            heliusJob.join()
            birdeyeJob.join()
            cryptoRankJob.join()
            
            _settingsState.update { it.copy(isScanning = false) }
            delay(3000) // 3 seconds cooldown
            scanCooldownActive = false
        }
    }

    private suspend fun workerHeliusScan() {
        val heliusKey = _settingsState.value.heliusApiKey
        if (heliusKey.isBlank()) {
            logDebug("Worker A (Helius): Key missing, skipping.")
            return
        }

        try {
            logDebug("Worker A (Helius): Scanning new mints...")
            // Specifically using searchAssets from the Helius DAS API via JSON-RPC
            val params = mapOf("limit" to 10, "displayOptions" to mapOf("showFungible" to true))
            val requestBody = HeliusRpcRequest(
                method = "searchAssets",
                params = params
            )
            
            val response = ApiProvider.heliusApi.searchAssets(heliusKey, requestBody)
            val tokens = response.result?.items ?: emptyList()
            logDebug("Worker A (Helius): OK. Found \${tokens.size} tokens.")

            for (item in tokens) {
                val mintAddress = item.id ?: continue
                val existingToken = tokenDao.getToken(mintAddress)
                
                // Only insert if it doesn't exist to prevent overwriting Birdeye/CryptoRank data
                if (existingToken == null) {
                    val symbol = item.content?.metadata?.symbol ?: "UNKNOWN"
                    val name = item.content?.metadata?.name ?: "Unknown Token"
                    val isMutable = item.mutable ?: false
                    
                    val newToken = TokenEntity(
                        mintAddress = mintAddress,
                        name = name,
                        symbol = symbol,
                        liquidityUsd = 0.0,
                        priceUsd = 0.0,
                        atlUsd = 0.0,
                        athUsd = 0.0,
                        volume24hUsd = 0.0,
                        atlLastUpdated = 0L,
                        isSafe = true,
                        isMutable = isMutable,
                        riskScore = 0
                    )
                    tokenDao.insertToken(newToken)
                    logDebug("Worker A (Helius): Inserted new token \$symbol.")
                }
            }
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker A (Helius) Error: \${e.message}")
            logDebug("Worker A (Helius): Error \${e.message?.take(20)}...")
        }
    }

    private suspend fun workerBirdeyeScan() {
        val birdeyeKey = _settingsState.value.birdeyeApiKey
        if (birdeyeKey.isBlank()) {
            logDebug("Worker B (Birdeye): Key missing, skipping.")
            return
        }

        try {
            // Get all tokens from local DB to update them. In a real app, you might want to paginate or filter.
            val tokensFlow = tokenDao.getAllTokens()
            // We need a single snapshot of current tokens
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            delay(100) // Wait for snapshot
            job.cancel()
            
            if (currentTokens.isEmpty()) return
            logDebug("Worker B (Birdeye): Updating \${currentTokens.size} tokens...")

            for (token in currentTokens) {
                try {
                    val response = ApiProvider.birdeyeApi.getTokenOverview(birdeyeKey, token.mintAddress)
                    val liq = response.data?.liquidity ?: 0.0
                    val price = response.data?.price ?: 0.0
                    val vol = response.data?.v24hUSD ?: 0.0
                    
                    val updatedToken = token.copy(
                        liquidityUsd = liq,
                        priceUsd = price,
                        volume24hUsd = vol
                    )
                    tokenDao.insertToken(updatedToken)
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Worker B (Birdeye) failed for \${token.symbol}: \${e.message}")
                    logDebug("Worker B (Birdeye): Error for \${token.symbol}")
                }
            }
            logDebug("Worker B (Birdeye): OK.")
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker B (Birdeye) Error: \${e.message}")
            logDebug("Worker B (Birdeye): Global Error \${e.message?.take(20)}...")
        }
    }

    private suspend fun workerCryptoRankScan(forceRefresh: Boolean) {
        val cryptoRankKey = _settingsState.value.cryptoRankApiKey
        if (cryptoRankKey.isBlank()) {
            logDebug("Worker C (CryptoRank): Key missing, skipping.")
            return
        }

        try {
            val tokensFlow = tokenDao.getAllTokens()
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            delay(100) // Wait for snapshot
            job.cancel()

            val now = System.currentTimeMillis()
            var checkedCount = 0

            for (token in currentTokens) {
                // Only update if older than 24 hours or forced
                val needsUpdate = forceRefresh || (now - token.atlLastUpdated > 24 * 60 * 60 * 1000)
                if (needsUpdate) {
                    checkedCount++
                    try {
                        val coinsResponse = ApiProvider.cryptoRankApi.getCoins(cryptoRankKey, token.symbol)
                        val coinData = coinsResponse.data?.firstOrNull { it.symbol.equals(token.symbol, ignoreCase = true) }
                        
                        if (coinData != null) {
                            val priceResponse = ApiProvider.cryptoRankApi.getPrice(cryptoRankKey, coinData.id.toString())
                            val atl = priceResponse.data?.get(coinData.id.toString())?.atlPrice?.usd
                            val ath = priceResponse.data?.get(coinData.id.toString())?.athPrice?.usd
                            
                            val updatedToken = token.copy(
                                atlUsd = atl ?: token.atlUsd,
                                athUsd = ath ?: token.athUsd,
                                atlLastUpdated = now
                            )
                            tokenDao.insertToken(updatedToken)
                        } else {
                            // Coin not found on CryptoRank. Update timestamp to avoid hammering API.
                            val updatedToken = token.copy(atlLastUpdated = now)
                            tokenDao.insertToken(updatedToken)
                        }
                    } catch (e: Exception) {
                        Log.e("ShadowViewModel", "Worker C (CryptoRank) failed for \${token.symbol}: \${e.message}")
                        logDebug("Worker C (CryptoRank): Error for \${token.symbol}")
                        
                        // Also update timestamp on generic errors to enforce cooldown
                        val updatedToken = token.copy(atlLastUpdated = now)
                        tokenDao.insertToken(updatedToken)
                    }
                }
            }
            if (checkedCount > 0) {
                logDebug("Worker C (CryptoRank): OK. Updated \$checkedCount tokens.")
            }
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker C (CryptoRank) Error: \${e.message}")
            logDebug("Worker C (CryptoRank): Global Error \${e.message?.take(20)}...")
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
