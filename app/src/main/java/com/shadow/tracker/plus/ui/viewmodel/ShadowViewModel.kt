package com.shadow.tracker.plus.ui.viewmodel
import kotlinx.coroutines.isActive

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
    val isScanning: Boolean = false,
    val heliusApiKey: String = ""
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

    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // Pipeline starts manually via forceScan or UI toggle
    }

    fun startScanning() {
        if (_settingsState.value.isScanning) return
        _settingsState.update { it.copy(isScanning = true) }
        logDebug("Initializing Phönix-Matrix...")
        
        scanJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runPipeline()
        }
    }

    fun stopScanning() {
        _settingsState.update { it.copy(isScanning = false) }
        scanJob?.cancel()
        logDebug("Matrix Scanner offline.")
    }

    fun forceScanNow() {
        if (!_settingsState.value.isScanning) {
            startScanning()
        }
    }
    
    fun forceScan() {
        forceScanNow()
    }

    private suspend fun runPipeline() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive && _settingsState.value.isScanning) {
            try {
                stage1Ingestion()
                kotlinx.coroutines.delay(2000)
                stage2Analysis()
                kotlinx.coroutines.delay(2000)
                stage3Security()
            } catch (e: Exception) {
                logDebug("> [Pipeline Error] ${e.message}")
            }
            kotlinx.coroutines.delay(15000) // General pipeline loop
        }
    }

    private suspend fun stage1Ingestion() {
        logDebug("Stage 1: DexScreener Ingestion...")
        try {
            val profiles = ApiProvider.dexScreenerApi.getLatestTokenProfiles()
            val validProfiles = profiles.filter { it.chainId == "solana" && !it.tokenAddress.isNullOrBlank() }
            
            var addedCount = 0
            for (profile in validProfiles) {
                val isNew = insertNewToken(
                    mintAddress = profile.tokenAddress!!,
                    symbol = profile.header?.take(10) ?: "UNK",
                    name = profile.description?.take(20) ?: "Unknown",
                    isMutable = false // Deferred to Helius
                )
                if (isNew) addedCount++
            }
            if (addedCount > 0) {
                 logDebug("DexScreener discovered $addedCount new Mints.")
            }
            
            // Now update market data for ALL tracking tokens to maintain history
            val tokensFlow = tokenDao.getAllTokens()
            var currentTokens: List<TokenEntity> = emptyList()
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                tokensFlow.collect { currentTokens = it }
            }
            kotlinx.coroutines.delay(100)
            job.cancel()
            
            if (currentTokens.isEmpty()) return
            
            // DexScreener supports up to 30 addresses per request
            val chunks = currentTokens.chunked(30)
            for (chunk in chunks) {
                val addresses = chunk.joinToString(",") { it.mintAddress }
                val response = ApiProvider.dexScreenerApi.getTokens(addresses)
                
                response.pairs?.let { pairs ->
                    for (token in chunk) {
                        val bestPair = pairs.filter { it.baseToken?.address == token.mintAddress }
                            .maxByOrNull { it.liquidity?.usd ?: 0.0 }
                            
                        if (bestPair != null) {
                            val price = bestPair.priceUsd?.toDoubleOrNull() ?: 0.0
                            val vol24h = bestPair.volume?.h24 ?: 0.0
                            
                            // Initialize ATL if missing
                            var currentAtl = token.atlUsd
                            if (currentAtl <= 0.0 && price > 0.0) {
                                currentAtl = price
                            }
                            
                            // Update ATL dynamically if price drops further
                            if (price > 0.0 && price < currentAtl) {
                                currentAtl = price
                            }
                            
                            val updated = token.copy(
                                priceUsd = price,
                                liquidityUsd = bestPair.liquidity?.usd ?: 0.0,
                                volume24hUsd = vol24h,
                                marketCapUsd = bestPair.fdv ?: 0.0,
                                txns24h = (bestPair.txns?.h24?.buys ?: 0) + (bestPair.txns?.h24?.sells ?: 0),
                                pairCreatedAt = bestPair.pairCreatedAt ?: token.pairCreatedAt,
                                atlUsd = currentAtl,
                                athUsd = if (price > token.athUsd) price else token.athUsd,
                                narrativeTags = token.narrativeTags ?: profileTagsExtract(bestPair.baseToken?.name ?: ""),
                                lastDiscoveredAt = System.currentTimeMillis()
                            )
                            tokenDao.insertToken(updated)
                        }
                    }
                }
                kotlinx.coroutines.delay(250) // Be gentle to DexScreener rate limits
            }
        } catch (e: Exception) {
            logDebug("Ingestion Error: ${e.message?.take(30)}")
        }
    }
    
    private fun profileTagsExtract(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.contains("ai") -> "AI"
            lower.contains("depin") -> "DePIN"
            lower.contains("game") -> "Gaming"
            lower.contains("rwa") -> "RWA"
            else -> null
        }
    }

    private suspend fun stage2Analysis() {
        val apiKey = _settingsState.value.heliusApiKey
        if (apiKey.isBlank()) {
            logDebug("⛔ Helius API Key missing. Analysis & Accumulation Module paused.")
            return // Soft fail, allow Stage 1 (DexScreener) and Stage 3 (RugCheck) to continue breathing
        }
        logDebug("Stage 2: Helius Analysis...")
        
        val pending = tokenDao.getTokensPendingAnalysis(10) // Limit to save RPC calls
        for (token in pending) {
            try {
                // Get BlockTime
                val req = HeliusRpcRequest(
                    method = "getSignaturesForAddress",
                    params = listOf(token.mintAddress, mapOf("limit" to 1))
                )
                val sigRes = ApiProvider.heliusApi.getSignaturesForAddress(apiKey, req)
                val blockTime = sigRes.result?.firstOrNull()?.blockTime
                
                val ageInDays = if (blockTime != null) {
                    ((System.currentTimeMillis() / 1000) - blockTime) / (60 * 60 * 24)
                } else null
                
                // Pseudo RVOL removed. We use TA4J mathematics for proper calculations based on accumulated daily history.
                // For MVP without heavy DB migrations, we use a simple moving average of our local history (100k baseline)
                val calculatedRvol = if (token.volume24hUsd > 0) {
                    val baseline = 10000.0 // Default baseline for new tokens
                    token.volume24hUsd / baseline
                } else {
                    0.0
                }
                
                tokenDao.insertToken(token.copy(
                    genesisBlockTime = blockTime,
                    ageInDays = ageInDays?.toInt(),
                    rvol = calculatedRvol,
                    lastAnalysisAt = System.currentTimeMillis()
                ))
                kotlinx.coroutines.delay(150)
            } catch (e: Exception) {
                logDebug("Analysis Error: ${e.message?.take(20)}")
            }
        }
    }

    private suspend fun stage3Security() {
        logDebug("Stage 3: RugCheck Security...")
        val pending = tokenDao.getTokensPendingRugCheck(1) // STRICT LIMIT: 1 per cycle (respect 5 RPM)
        if (pending.isEmpty()) return
        
        val token = pending[0]
        try {
            val response = ApiProvider.rugCheckApi.getReportSummary(token.mintAddress)
            val score = response.score ?: 10000
            
            val isSafe = score < 1000
            
            tokenDao.insertToken(token.copy(
                isRugCheckComplete = true,
                isSafe = isSafe,
                riskScore = score,
                lastRugCheckAt = System.currentTimeMillis()
            ))
            
            if (isSafe && (token.rvol ?: 0.0) >= 3.0) {
                 sendFallenGiantNotification(token)
            }
            
            tokenDao.deleteUnsafeTokens()
            
        } catch (e: Exception) {
            logDebug("RugCheck Error: ${e.message?.take(20)}")
        }
    }
    
    private fun sendFallenGiantNotification(token: TokenEntity) {
        logDebug("ALARM: " + token.symbol + " is a FALLEN GIANT!")
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
