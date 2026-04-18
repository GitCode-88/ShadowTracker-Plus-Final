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
    val minFdvUsd: Float = 0f,
    val timeSelection: String = "24H", // 1H, 6H, 24H
    val minVolumeUsd: Float = 0f,
    val minTxns: Float = 0f,
    val minTraders: Float = 0f,
    val maxAgeHours: Float = 720f, // Up to 30 days default
    val maxPriceChangeFromAtl: Float = 500f, // in percentage
    val heliusApiKey: String = "",
    val birdeyeApiKey: String = "",
    val cryptoRankApiKey: String = "", // Kept for compatibility/settings layout
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

    // Extract key helper
    private fun extractKey(input: String): String {
        return if (input.contains("api-key=")) {
            input.substringAfterLast("api-key=")
        } else if (input.contains("/")) {
            input.substringAfterLast("/")
        } else {
            input
        }
    }

    // Derived state: Filter tokens from the database based on all filters
    val filteredTokens: StateFlow<List<TokenEntity>> = combine(
        tokenDao.getAllTokens(),
        _settingsState
    ) { tokens, settings ->
        tokens.filter { token ->
            // Hard safety filter first
            if (!token.isSafe || token.isMutable) return@filter false
            
            val passesLiquidity = token.liquidityUsd >= settings.liquidityFilterUsd
            val passesFdv = token.marketCapUsd >= settings.minFdvUsd
            val passesTraders = token.traders24h >= settings.minTraders

            // Handle time selection based filtering for volume and txns
            val (vol, txns) = when (settings.timeSelection) {
                "1H" -> token.volume1hUsd to token.txns1h
                "6H" -> token.volume6hUsd to token.txns6h
                else -> token.volume24hUsd to token.txns24h
            }
            val passesVolume = vol >= settings.minVolumeUsd
            val passesTxns = txns >= settings.minTxns

            // Age Check
            val ageMs = System.currentTimeMillis() - token.pairCreatedAt
            val ageHours = ageMs / (1000 * 60 * 60).toFloat()
            val passesAge = ageHours <= settings.maxAgeHours

            // ATL Check
            val priceChangePercent = if (token.atlUsd > 0) {
                ((token.priceUsd - token.atlUsd) / token.atlUsd) * 100.0
            } else {
                0.0
            }
            val passesAtlChange = priceChangePercent <= settings.maxPriceChangeFromAtl
            
            passesLiquidity && passesVolume && passesFdv && passesTxns && passesTraders && passesAge && passesAtlChange
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
        val heliusRaw = _settingsState.value.heliusApiKey
        val birdeyeRaw = _settingsState.value.birdeyeApiKey
        
        val heliusKey = extractKey(heliusRaw)
        val birdeyeKey = extractKey(birdeyeRaw)

        if (heliusKey.isBlank() && birdeyeKey.isBlank()) {
            logDebug("Worker A (Discovery): Keys missing, skipping.")
            return
        }

        logDebug("Worker A (Discovery): Scanning new mints...")
        var foundTokens = false

        // Try Helius first
        if (heliusKey.isNotBlank()) {
            try {
                val params = mapOf("limit" to 20, "displayOptions" to mapOf("showFungible" to true))
                val requestBody = HeliusRpcRequest(
                    method = "searchAssets",
                    params = params
                )
                val response = ApiProvider.heliusApi.searchAssets(heliusKey, requestBody)
                val tokens = response.result?.items ?: emptyList()
                
                for (item in tokens) {
                    val mintAddress = item.id ?: continue
                    insertNewToken(
                        mintAddress = mintAddress,
                        symbol = item.content?.metadata?.symbol ?: "UNKNOWN",
                        name = item.content?.metadata?.name ?: "Unknown",
                        isMutable = item.mutable ?: false
                    )
                }
                foundTokens = true
                logDebug("Worker A (Helius): OK. \${tokens.size} tokens.")
            } catch (e: Exception) {
                Log.e("ShadowViewModel", "Helius Error: \${e.message}")
                logDebug("Worker A (Helius) Error: \${e.message?.take(20)}")
            }
        }

        // Fallback to Birdeye Token List if Helius failed or wasn't provided
        if (!foundTokens && birdeyeKey.isNotBlank()) {
            try {
                logDebug("Worker A (Birdeye Fallback): Fetching...")
                val response = ApiProvider.birdeyeApi.getTokenList(apiKey = birdeyeKey)
                val tokens = response.data?.tokens ?: emptyList()
                
                for (item in tokens) {
                    val mintAddress = item.address ?: continue
                    insertNewToken(
                        mintAddress = mintAddress,
                        symbol = item.symbol ?: "UNKNOWN",
                        name = item.name ?: "Unknown",
                        isMutable = true // Assume mutable if unknown from Birdeye
                    )
                }
                logDebug("Worker A (Birdeye): OK. \${tokens.size} tokens.")
            } catch (e: Exception) {
                Log.e("ShadowViewModel", "Birdeye Discovery Error: \${e.message}")
                logDebug("Worker A (Birdeye) Error: \${e.message?.take(20)}")
            }
        }
    }

    private suspend fun insertNewToken(mintAddress: String, symbol: String, name: String, isMutable: Boolean) {
        val existingToken = tokenDao.getToken(mintAddress)
        if (existingToken == null) {
            val newToken = TokenEntity(
                mintAddress = mintAddress,
                name = name,
                symbol = symbol,
                liquidityUsd = 0.0,
                priceUsd = 0.0,
                marketCapUsd = 0.0,
                volume1hUsd = 0.0,
                volume6hUsd = 0.0,
                volume24hUsd = 0.0,
                txns1h = 0,
                txns6h = 0,
                txns24h = 0,
                traders24h = 0,
                pairCreatedAt = System.currentTimeMillis(),
                atlUsd = 0.0,
                athUsd = 0.0,
                atlLastUpdated = 0L,
                isSafe = true,
                isMutable = isMutable,
                riskScore = 0
            )
            tokenDao.insertToken(newToken)
        }
    }

    private suspend fun workerDexScreenerScan() {
        val birdeyeKey = extractKey(_settingsState.value.birdeyeApiKey)

        try {
            val tokensFlow = tokenDao.getAllTokens()
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            delay(100)
            job.cancel()
            
            if (currentTokens.isEmpty()) return
            
            val chunks = currentTokens.chunked(30)
            logDebug("Worker B (Market Data): Updating \${currentTokens.size} tokens...")

            for (chunk in chunks) {
                val addresses = chunk.joinToString(",") { it.mintAddress }
                try {
                    val response = ApiProvider.dexScreenerApi.getTokens(addresses)
                    val pairs = response.pairs ?: emptyList()
                    
                    for (token in chunk) {
                        val bestPair = pairs.firstOrNull { it.baseToken?.address == token.mintAddress }
                        if (bestPair != null) {
                            val liq = bestPair.liquidity?.usd ?: 0.0
                            val priceStr = bestPair.priceUsd ?: "0"
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            
                            val vol1h = bestPair.volume?.h1 ?: 0.0
                            val vol6h = bestPair.volume?.h6 ?: 0.0
                            val vol24h = bestPair.volume?.h24 ?: 0.0
                            
                            val txns1h = (bestPair.txns?.h1?.buys ?: 0) + (bestPair.txns?.h1?.sells ?: 0)
                            val txns6h = (bestPair.txns?.h6?.buys ?: 0) + (bestPair.txns?.h6?.sells ?: 0)
                            val txns24h = (bestPair.txns?.h24?.buys ?: 0) + (bestPair.txns?.h24?.sells ?: 0)
                            
                            val fdv = bestPair.fdv ?: 0.0
                            val createdAt = bestPair.pairCreatedAt ?: token.pairCreatedAt
                            
                            val atl = if (token.atlUsd == 0.0 && price > 0.0) price else token.atlUsd
                            val ath = if (price > token.athUsd) price else token.athUsd

                            // Optional: Fetch Traders from Birdeye if Key is present
                            var traders24h = token.traders24h
                            if (birdeyeKey.isNotBlank()) {
                                try {
                                    val beResponse = ApiProvider.birdeyeApi.getTokenOverview(birdeyeKey, token.mintAddress)
                                    traders24h = beResponse.data?.uniqueWallet24h ?: token.traders24h
                                } catch (e: Exception) {
                                    // Silently ignore individual birdeye fails to not spam log
                                }
                            }
                            
                            val updatedToken = token.copy(
                                liquidityUsd = liq,
                                priceUsd = price,
                                volume1hUsd = vol1h,
                                volume6hUsd = vol6h,
                                volume24hUsd = vol24h,
                                marketCapUsd = fdv,
                                txns1h = txns1h,
                                txns6h = txns6h,
                                txns24h = txns24h,
                                traders24h = traders24h,
                                pairCreatedAt = createdAt,
                                atlUsd = atl,
                                athUsd = ath
                            )
                            tokenDao.insertToken(updatedToken)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ShadowViewModel", "Worker B (Market) chunk failed: \${e.message}")
                    logDebug("Worker B (Market): Error fetching chunk")
                }
            }
            logDebug("Worker B (Market): OK.")
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Worker B (Market) Error: \${e.message}")
            logDebug("Worker B (Market): Global Error \${e.message?.take(20)}")
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
        _settingsState.update { it.copy(minVolumeUsd = value) }
    }
    fun updateMinTxns(value: Float) {
        _settingsState.update { it.copy(minTxns = value) }
    }
    fun updateMinTraders(value: Float) {
        _settingsState.update { it.copy(minTraders = value) }
    }
    fun updateMaxAgeHours(value: Float) {
        _settingsState.update { it.copy(maxAgeHours = value) }
    }
    fun updateMinFdv(value: Float) {
        _settingsState.update { it.copy(minFdvUsd = value) }
    }
    fun updateMaxAtlChange(value: Float) {
        _settingsState.update { it.copy(maxPriceChangeFromAtl = value) }
    }
    fun updateTimeSelection(selection: String) {
        _settingsState.update { it.copy(timeSelection = selection) }
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
