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
    val liquidityFilterUsd: Float = 5000f,
    val minFdvUsd: Float = 50000f,
    val timeSelection: String = "24H", // 1H, 6H, 24H
    val minVolumeUsd: Float = 1000f,
    val minTxns: Float = 50f,
    val maxPriceChangeFromAtl: Float = 500f, // in percentage (+% from bottom)
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
            if (!token.isSafe || token.isMutable == true) return@filter false
            
            val passesLiquidity = token.liquidityUsd >= settings.liquidityFilterUsd
            val passesFdv = token.marketCapUsd >= settings.minFdvUsd

            // Handle time selection based filtering for volume and txns
            val (vol, txns) = when (settings.timeSelection) {
                "1H" -> token.volume1hUsd to token.txns1h
                "6H" -> token.volume24hUsd to token.txns24h
                else -> token.volume24hUsd to token.txns24h
            }
            val passesVolume = vol >= settings.minVolumeUsd
            val passesTxns = txns >= settings.minTxns

            // ATL Check (Nur Token, die einen ATL gesetzt haben)
            val passesAtlChange = if (token.atlUsd > 0) {
                val change = ((token.priceUsd - token.atlUsd) / token.atlUsd) * 100.0
                change <= settings.maxPriceChangeFromAtl
            } else {
                // Tokens ohne gesetztes ATL (weil sie z.B. zu neu sind) werden von der "Price from ATL" Regel ausgeschlossen
                // und passieren den Filter temporär NICHT, da wir ja gezielt etablierte "gefallene Engel" suchen.
                false
            }
            
            passesLiquidity && passesVolume && passesFdv && passesTxns && passesAtlChange
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
        // Renamed internally, this is now Worker A (DexScreener Latest Profile Discovery)
        logDebug("Worker A (Discovery): Scanning DexScreener...")
        try {
            // Using a hacky but functional approach to fetch the latest tokens from DexScreener's public undocumented API
            // For production, using the documented Search/Latest APIs is preferred if this endpoint fails.
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://api.dexscreener.com/token-profiles/latest/v1")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonString = response.body?.string() ?: ""
                val jsonArray = org.json.JSONArray(jsonString)
                
                var newTokensCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val chainId = obj.optString("chainId")
                    
                    if (chainId == "solana") {
                        val mintAddress = obj.optString("tokenAddress")
                        if (mintAddress.isNotBlank()) {
                            val isNew = insertNewToken(
                                mintAddress = mintAddress,
                                symbol = "UNK", // Symbol will be populated by Worker B
                                name = obj.optString("description").take(20), 
                                isMutable = false // Handled later
                            )
                            if (isNew) newTokensCount++
                        }
                    }
                }
                logDebug("Worker A (Discovery): OK. Added \$newTokensCount new Solana Mints.")
            } else {
                logDebug("Worker A: DexScreener Profile API failed \${response.code}")
            }
        } catch (e: Exception) {
            Log.e("ShadowViewModel", "Discovery Error: \${e.message}")
            logDebug("Worker A Error: \${e.message?.take(20)}")
        }
    }

    private suspend fun insertNewToken(mintAddress: String, symbol: String, name: String, isMutable: Boolean): Boolean {
        val existingToken = tokenDao.getToken(mintAddress)
        if (existingToken == null) {
            val newToken = TokenEntity(
                mintAddress = mintAddress,
                name = name.ifBlank { "Unknown" },
                symbol = symbol,
                liquidityUsd = 0.0,
                priceUsd = 0.0,
                marketCapUsd = 0.0,
                volume1hUsd = 0.0,
                volume24hUsd = 0.0,
                txns1h = 0,
                txns24h = 0,
                pairCreatedAt = System.currentTimeMillis(),
                genesisBlockTime = null,
                atlUsd = 0.0,
                athUsd = 0.0,
                ageInDays = null,
                rvol = null,
                proximityToAtl = null,
                choppinessIndex = null,
                dormancyDays = null,
                isRugCheckComplete = false,
                isSafe = true,
                mintAuthorityRevoked = null,
                freezeAuthorityRevoked = null,
                isMutable = isMutable,
                narrativeTags = null,
                isSmartMoneyAccumulating = null,
                lpLockedPct = null,
                top10HolderPct = null,
                riskScore = 0,
                lastDiscoveredAt = System.currentTimeMillis(),
                lastAnalysisAt = 0L,
                lastRugCheckAt = 0L
            )
            tokenDao.insertToken(newToken)
            return true
        }
        return false
    }

    private suspend fun workerDexScreenerScan() {
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

            // Helper to get true ATL if missing from DexScreener using CoinGecko (Free/Public search)
            suspend fun fetchAtlFallback(symbol: String): Double {
                try {
                    val client = okhttp3.OkHttpClient()
                    // Search for the coin ID
                    val searchReq = okhttp3.Request.Builder()
                        .url("https://api.coingecko.com/api/v3/search?query=\$symbol")
                        .build()
                    val searchRes = client.newCall(searchReq).execute()
                    if (searchRes.isSuccessful) {
                        val searchJson = org.json.JSONObject(searchRes.body?.string() ?: "{}")
                        val coins = searchJson.optJSONArray("coins")
                        if (coins != null && coins.length() > 0) {
                            val coinId = coins.getJSONObject(0).optString("id")
                            
                            // Fetch coin details for ATL
                            val detailsReq = okhttp3.Request.Builder()
                                .url("https://api.coingecko.com/api/v3/coins/\$coinId")
                                .build()
                            val detailsRes = client.newCall(detailsReq).execute()
                            if (detailsRes.isSuccessful) {
                                val detailsJson = org.json.JSONObject(detailsRes.body?.string() ?: "{}")
                                val marketData = detailsJson.optJSONObject("market_data")
                                val atlObj = marketData?.optJSONObject("atl")
                                val atlUsd = atlObj?.optDouble("usd") ?: 0.0
                                return atlUsd
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore CoinGecko rate limits/errors silently so we don't spam logs
                }
                return 0.0
            }

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
                            
                            val symbol = bestPair.baseToken?.symbol ?: token.symbol
                            
                            // Get ATL. If currently 0, try CoinGecko Fallback. If that fails, set it to the first seen price.
                            var atl = token.atlUsd
                            if (atl == 0.0 && price > 0.0) {
                                atl = fetchAtlFallback(symbol)
                                if (atl == 0.0) {
                                    atl = price // Set initial price as local ATL
                                }
                            }
                            
                            val ath = if (price > token.athUsd) price else token.athUsd
                            
                            val name = bestPair.baseToken?.name ?: token.name
                            
                            val updatedToken = token.copy(
                                name = name.ifBlank { "Unknown" },
                                symbol = symbol,
                                liquidityUsd = liq,
                                priceUsd = price,
                                volume1hUsd = vol1h,
                                
                                volume24hUsd = vol24h,
                                marketCapUsd = fdv,
                                txns1h = txns1h,
                                
                                txns24h = txns24h,
                                
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
    fun updateMinFdv(value: Float) {
        _settingsState.update { it.copy(minFdvUsd = value) }
    }
    fun updateMaxAtlChange(value: Float) {
        _settingsState.update { it.copy(maxPriceChangeFromAtl = value) }
    }
    fun updateTimeSelection(selection: String) {
        _settingsState.update { it.copy(timeSelection = selection) }
    }
}
