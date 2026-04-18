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
    val minFdvUsd: Float = 0f,
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
            // Hard safety filter first
            if (!token.isSafe) return@filter false
            
            val passesLiquidity = token.liquidityUsd >= settings.liquidityFilterUsd
            val passesVolume = token.volume24hUsd >= settings.minVolume24hUsd
            val passesFdv = token.marketCapUsd >= settings.minFdvUsd
            
            val priceChangePercent = if (token.atlUsd > 0) {
                ((token.priceUsd - token.atlUsd) / token.atlUsd) * 100.0
            } else {
                0.0
            }
            val passesAtlChange = priceChangePercent <= settings.maxPriceChangeFromAtl
            
            passesLiquidity && passesVolume && passesFdv && passesAtlChange
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

        // Worker B: DexScreener (Market Data Updater)
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    workerDexScreenerScan()
                }
                delay(10000) // Poll every 10 seconds
            }
        }

        // Worker C: RugCheck (Security Scanner)
        viewModelScope.launch {
            while (true) {
                if (!scanCooldownActive) {
                    workerRugCheckScan()
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
            
            // Execute all workers concurrently for force scan
            val heliusJob = launch { workerHeliusScan() }
            val dexScreenerJob = launch { workerDexScreenerScan() }
            val rugCheckJob = launch { workerRugCheckScan() }
            
            heliusJob.join()
            dexScreenerJob.join()
            rugCheckJob.join()
            
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
                        marketCapUsd = 0.0,
                        volume24hUsd = 0.0,
                        txns24h = 0,
                        pairCreatedAt = 0L,
                        atlUsd = 0.0,
                        athUsd = 0.0,
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

    private suspend fun workerDexScreenerScan() {
        try {
            val tokensFlow = tokenDao.getAllTokens()
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            delay(100) // Wait for snapshot
            job.cancel()
            
            if (currentTokens.isEmpty()) return
            
            // DexScreener allows up to 30 addresses per request separated by commas
            val chunks = currentTokens.chunked(30)
            
            logDebug("Worker B (DexScreener): Updating \${currentTokens.size} tokens...")

            for (chunk in chunks) {
                val addresses = chunk.joinToString(",") { it.mintAddress }
                try {
                    val response = ApiProvider.dexScreenerApi.getTokens(addresses)
                    val pairs = response.pairs ?: emptyList()
                    
                    // Update tokens with the best pair (usually the first one returned for that token)
                    for (token in chunk) {
                        val bestPair = pairs.firstOrNull { it.baseToken?.address == token.mintAddress }
                        if (bestPair != null) {
                            val liq = bestPair.liquidity?.usd ?: 0.0
                            val priceStr = bestPair.priceUsd ?: "0"
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            val vol = bestPair.volume?.h24 ?: 0.0
                            val txns = (bestPair.txns?.h24?.buys ?: 0) + (bestPair.txns?.h24?.sells ?: 0)
                            val fdv = bestPair.fdv ?: 0.0
                            val createdAt = bestPair.pairCreatedAt ?: 0L
                            
                            // Emulate ATL behavior using price history or initial price tracking
                            // For simplicity, if atlUsd is 0, we set it to current price as initial baseline
                            val atl = if (token.atlUsd == 0.0 && price > 0.0) price else token.atlUsd
                            
                            val updatedToken = token.copy(
                                liquidityUsd = liq,
                                priceUsd = price,
                                volume24hUsd = vol,
                                marketCapUsd = fdv,
                                txns24h = txns,
                                pairCreatedAt = createdAt,
                                atlUsd = atl
                            )
                            tokenDao.insertToken(updatedToken)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Worker B (DexScreener) chunk failed: \${e.message}")
                    logDebug("Worker B (DexScreener): Error fetching chunk")
                }
            }
            logDebug("Worker B (DexScreener): OK.")
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker B (DexScreener) Error: \${e.message}")
            logDebug("Worker B (DexScreener): Global Error \${e.message?.take(20)}...")
        }
    }

    private suspend fun workerRugCheckScan() {
        try {
            val tokensFlow = tokenDao.getAllTokens()
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            delay(100) // Wait for snapshot
            job.cancel()

            // Only check tokens that haven't been marked unsafe yet and haven't got a risk score
            // In a real app we would cache the "last checked" timestamp for RugCheck too
            val tokensToCheck = currentTokens.filter { it.isSafe && it.riskScore == 0 }
            
            if (tokensToCheck.isEmpty()) return
            logDebug("Worker C (RugCheck): Checking \${tokensToCheck.size} tokens...")

            for (token in tokensToCheck) {
                try {
                    val response = ApiProvider.rugCheckApi.getReportSummary(token.mintAddress)
                    val score = response.score ?: 0
                    
                    // Hard rule: if Score > 5000 or contains a "danger" risk, mark as unsafe
                    val hasDanger = response.risks?.any { it.level == "danger" } ?: false
                    val isSafe = score < 5000 && !hasDanger
                    
                    val updatedToken = token.copy(
                        riskScore = score,
                        isSafe = isSafe
                    )
                    tokenDao.insertToken(updatedToken)
                    
                    if (!isSafe) {
                        logDebug("Worker C (RugCheck): \${token.symbol} is DANGEROUS!")
                    }
                    
                    // Throttle requests to not hit rate limits on RugCheck
                    delay(1000)
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Worker C (RugCheck) failed for \${token.symbol}: \${e.message}")
                    // Don't log spam for 404s (some valid tokens just aren't scanned yet)
                }
            }
            logDebug("Worker C (RugCheck): OK.")
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker C (RugCheck) Error: \${e.message}")
            logDebug("Worker C (RugCheck): Global Error \${e.message?.take(20)}...")
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
    fun updateMinFdv(value: Float) {
        _settingsState.update { it.copy(minFdvUsd = value) }
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
