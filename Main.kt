package com.krakendesktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.prefs.Preferences
import kotlin.random.Random
import kotlin.math.*

// --- Enhanced Data Classes ---
data class SidebarItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
data class OhlcCandle(val time: Long, val close: Double, val high: Double, val low: Double, val volume: Double)
data class TradeActivity(val type: String, val asset: String, val amount: Double, val price: Double, val timestamp: String, val profit: Double = 0.0)

data class BotStats(
    val totalTrades: Int = 0,
    val winRate: Double = 0.0,
    val totalProfit: Double = 0.0,
    val dailyReturn: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val profitFactor: Double = 0.0,
    val avgWinLossRatio: Double = 0.0,
    val volatility: Double = 0.0,
    val currentStreak: Int = 0,
    val maxConsecutiveWins: Int = 0,
    val avgTradeTime: String = "0m",
    val roi: Double = 0.0,
    val calmarRatio: Double = 0.0,
    val sortinoRatio: Double = 0.0
)

data class TradingConfig(
    val maxAllocation: Double = 3500.0,
    val basePositionSize: Double = 0.08,
    val confidenceThreshold: Int = 85,
    val maxDailyTrades: Int = 8,
    val maxDailyLoss: Double = 0.04,
    val maxDailyGain: Double = 0.12,
    val stopLoss: Double = 0.03,
    val profitTarget: Double = 0.06,
    val trailingStop: Boolean = true,
    val riskLevel: String = "AGGRESSIVE",
    val useKellyFormula: Boolean = true,
    val maxCorrelatedTrades: Int = 3,
    val volatilityFilter: Boolean = true,
    val useTimeFilter: Boolean = true,
    val multiTimeframe: Boolean = true
)

data class MarketRegime(
    val type: String,
    val confidence: Double,
    val volatility: Double,
    val momentum: Double,
    val strength: Double = 0.0,
    val duration: Int = 0,
    val trendQuality: String = "UNKNOWN"
)

data class RiskMetrics(
    val var95: Double = 0.0,
    val expectedShortfall: Double = 0.0,
    val beta: Double = 0.0,
    val correlation: Double = 0.0,
    val informationRatio: Double = 0.0,
    val treynorRatio: Double = 0.0,
    val jensenAlpha: Double = 0.0
)

// Kraken API Interface
interface KrakenApiClientInterface {
    suspend fun getBalance(): Map<String, Double>
    suspend fun getCurrentPrice(pair: String): Double
    suspend fun getTicker(pair: String): Double?
    suspend fun getOHLC(pair: String): List<Pair<Long, Double>>
    suspend fun placeBuyOrder(pair: String, amount: Double): String
    suspend fun placeSellOrder(pair: String, amount: Double): String
}

// Real Kraken API Client (placeholder)
class RealKrakenApiClient(private val apiKey: String, private val apiSecret: String) : KrakenApiClientInterface {
    override suspend fun getBalance(): Map<String, Double> = mapOf("XXBT" to 0.0, "XETH" to 0.0, "ZGBP" to 0.0)
    override suspend fun getCurrentPrice(pair: String): Double = 31500.0
    override suspend fun getTicker(pair: String): Double? = 31500.0
    override suspend fun getOHLC(pair: String): List<Pair<Long, Double>> = emptyList()
    override suspend fun placeBuyOrder(pair: String, amount: Double): String = "Real trading not implemented"
    override suspend fun placeSellOrder(pair: String, amount: Double): String = "Real trading not implemented"
}

// --- Enhanced Paper Trading Client ---
class PaperKrakenApiClient : KrakenApiClientInterface {
    private var btc: Double = 1.2
    private var eth: Double = 3.5
    private var gbp: Double = 12_000.0
    private val tradeHistory = mutableListOf<TradeActivity>()
    private var totalTrades = 0
    private var winningTrades = 0
    private var totalWins = 0.0
    private var totalLosses = 0.0
    private var initialBalance = gbp
    private var dailyTrades = 0
    private var dailyPnL = 0.0
    private val priceHistory = mutableListOf<Double>()
    private val pnlHistory = mutableListOf<Double>()
    private var lastResetDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
    private var currentStreak = 0
    private var maxWinStreak = 0
    private val tradeTimestamps = mutableListOf<Long>()

    override suspend fun getBalance(): Map<String, Double> = mapOf(
        "XXBT" to btc,
        "XETH" to eth,
        "ZGBP" to gbp
    )

    override suspend fun getCurrentPrice(pair: String): Double {
        delay(100)
        val basePrice = when(pair) {
            "XXBTGBP", "XBTGBP" -> 31500.0
            "XETHGBP", "ETHGBP" -> 2100.0
            else -> 1000.0
        }
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val volatilityMultiplier = when (hour) {
            in 8..10, in 14..16 -> 1.8
            in 20..22 -> 1.4
            else -> 0.9
        }
        
        val variance = Random.nextDouble(-3.5, 3.5) / 100.0 * volatilityMultiplier
        val price = basePrice * (1 + variance)
        
        priceHistory.add(price)
        if (priceHistory.size > 250) priceHistory.removeAt(0)
        
        return price
    }

    override suspend fun getTicker(pair: String): Double? = getCurrentPrice(pair)

    override suspend fun getOHLC(pair: String): List<Pair<Long, Double>> {
        delay(200)
        val basePrice = getCurrentPrice(pair)
        val now = System.currentTimeMillis() / 1000
        return (0..120).map { i ->
            val time = now - (120 - i) * 600
            val price = basePrice * (1 + Random.nextDouble(-0.12, 0.12))
            Pair(time, price)
        }
    }

    private fun resetDailyCounters() {
        val today = SimpleDateFormat("yyyy-MM-dd").format(Date())
        if (today != lastResetDate) {
            dailyTrades = 0
            dailyPnL = 0.0
            lastResetDate = today
        }
    }

    override suspend fun placeBuyOrder(pair: String, amount: Double): String {
        resetDailyCounters()
        
        delay(300)
        val price = getCurrentPrice(pair)
        val cost = amount * price
        val timestamp = System.currentTimeMillis()

        when (pair.substringBefore("GBP")) {
            "XXBT", "XBT" -> {
                if (gbp >= cost) {
                    gbp -= cost
                    btc += amount
                    dailyTrades++
                    tradeTimestamps.add(timestamp)
                    
                    val marketCondition = Random.nextDouble()
                    val profit = when {
                        marketCondition < 0.72 -> Random.nextDouble(cost * 0.015, cost * 0.18)
                        marketCondition < 0.88 -> Random.nextDouble(-cost * 0.025, cost * 0.03)
                        else -> Random.nextDouble(-cost * 0.12, -cost * 0.01)
                    }
                    
                    dailyPnL += profit
                    pnlHistory.add(profit)
                    if (pnlHistory.size > 150) pnlHistory.removeAt(0)
                    
                    val trade = TradeActivity(
                        type = "BUY",
                        asset = "BTC",
                        amount = amount,
                        price = price,
                        timestamp = SimpleDateFormat("HH:mm:ss").format(Date()),
                        profit = profit
                    )
                    tradeHistory.add(trade)
                    totalTrades++
                    
                    if (profit > 0) {
                        winningTrades++
                        totalWins += profit
                        currentStreak = if (currentStreak > 0) currentStreak + 1 else 1
                        maxWinStreak = maxOf(maxWinStreak, currentStreak)
                    } else {
                        totalLosses += abs(profit)
                        currentStreak = if (currentStreak < 0) currentStreak - 1 else -1
                    }
                    
                    return "✓ BUY ${"%.6f".format(amount)} BTC @ £${"%.2f".format(price)} | P&L: £${"%.2f".format(profit)} | ROI: ${"%.2f".format((profit/cost)*100)}%"
                }
                throw Exception("Insufficient GBP: Need £${"%.2f".format(cost)}, have £${"%.2f".format(gbp)}")
            }
            "XETH", "ETH" -> {
                if (gbp >= cost) {
                    gbp -= cost
                    eth += amount
                    dailyTrades++
                    tradeTimestamps.add(timestamp)
                    
                    val marketCondition = Random.nextDouble()
                    val profit = when {
                        marketCondition < 0.72 -> Random.nextDouble(cost * 0.015, cost * 0.18)
                        marketCondition < 0.88 -> Random.nextDouble(-cost * 0.025, cost * 0.03)
                        else -> Random.nextDouble(-cost * 0.12, -cost * 0.01)
                    }
                    
                    dailyPnL += profit
                    pnlHistory.add(profit)
                    if (pnlHistory.size > 150) pnlHistory.removeAt(0)
                    
                    val trade = TradeActivity(
                        type = "BUY",
                        asset = "ETH",
                        amount = amount,
                        price = price,
                        timestamp = SimpleDateFormat("HH:mm:ss").format(Date()),
                        profit = profit
                    )
                    tradeHistory.add(trade)
                    totalTrades++
                    
                    if (profit > 0) {
                        winningTrades++
                        totalWins += profit
                        currentStreak = if (currentStreak > 0) currentStreak + 1 else 1
                        maxWinStreak = maxOf(maxWinStreak, currentStreak)
                    } else {
                        totalLosses += abs(profit)
                        currentStreak = if (currentStreak < 0) currentStreak - 1 else -1
                    }
                    
                    return "✓ BUY ${"%.6f".format(amount)} ETH @ £${"%.2f".format(price)} | P&L: £${"%.2f".format(profit)} | ROI: ${"%.2f".format((profit/cost)*100)}%"
                }
                throw Exception("Insufficient GBP: Need £${"%.2f".format(cost)}, have £${"%.2f".format(gbp)}")
            }
        }
        throw Exception("Unsupported trading pair: $pair")
    }

    override suspend fun placeSellOrder(pair: String, amount: Double): String {
        resetDailyCounters()
        
        delay(300)
        val price = getCurrentPrice(pair)
        val timestamp = System.currentTimeMillis()

        when (pair.substringBefore("GBP")) {
            "XXBT", "XBT" -> {
                if (btc >= amount) {
                    val proceeds = amount * price
                    btc -= amount
                    gbp += proceeds
                    dailyTrades++
                    tradeTimestamps.add(timestamp)
                    
                    val marketCondition = Random.nextDouble()
                    val profit = when {
                        marketCondition < 0.72 -> Random.nextDouble(proceeds * 0.015, proceeds * 0.18)
                        marketCondition < 0.88 -> Random.nextDouble(-proceeds * 0.025, proceeds * 0.03)
                        else -> Random.nextDouble(-proceeds * 0.12, -proceeds * 0.01)
                    }
                    
                    dailyPnL += profit
                    pnlHistory.add(profit)
                    if (pnlHistory.size > 150) pnlHistory.removeAt(0)
                    
                    val trade = TradeActivity(
                        type = "SELL",
                        asset = "BTC",
                        amount = amount,
                        price = price,
                        timestamp = SimpleDateFormat("HH:mm:ss").format(Date()),
                        profit = profit
                    )
                    tradeHistory.add(trade)
                    totalTrades++
                    
                    if (profit > 0) {
                        winningTrades++
                        totalWins += profit
                        currentStreak = if (currentStreak > 0) currentStreak + 1 else 1
                        maxWinStreak = maxOf(maxWinStreak, currentStreak)
                    } else {
                        totalLosses += abs(profit)
                        currentStreak = if (currentStreak < 0) currentStreak - 1 else -1
                    }
                    
                    return "✓ SELL ${"%.6f".format(amount)} BTC @ £${"%.2f".format(price)} | P&L: £${"%.2f".format(profit)} | ROI: ${"%.2f".format((profit/proceeds)*100)}%"
                }
                throw Exception("Insufficient BTC: Need ${"%.6f".format(amount)}, have ${"%.6f".format(btc)}")
            }
            "XETH", "ETH" -> {
                if (eth >= amount) {
                    val proceeds = amount * price
                    eth -= amount
                    gbp += proceeds
                    dailyTrades++
                    tradeTimestamps.add(timestamp)
                    
                    val marketCondition = Random.nextDouble()
                    val profit = when {
                        marketCondition < 0.72 -> Random.nextDouble(proceeds * 0.015, proceeds * 0.18)
                        marketCondition < 0.88 -> Random.nextDouble(-proceeds * 0.025, proceeds * 0.03)
                        else -> Random.nextDouble(-proceeds * 0.12, -proceeds * 0.01)
                    }
                    
                    dailyPnL += profit
                    pnlHistory.add(profit)
                    if (pnlHistory.size > 150) pnlHistory.removeAt(0)
                    
                    val trade = TradeActivity(
                        type = "SELL",
                        asset = "ETH",
                        amount = amount,
                        price = price,
                        timestamp = SimpleDateFormat("HH:mm:ss").format(Date()),
                        profit = profit
                    )
                    tradeHistory.add(trade)
                    totalTrades++
                    
                    if (profit > 0) {
                        winningTrades++
                        totalWins += profit
                        currentStreak = if (currentStreak > 0) currentStreak + 1 else 1
                        maxWinStreak = maxOf(maxWinStreak, currentStreak)
                    } else {
                        totalLosses += abs(profit)
                        currentStreak = if (currentStreak < 0) currentStreak - 1 else -1
                    }
                    
                    return "✓ SELL ${"%.6f".format(amount)} ETH @ £${"%.2f".format(price)} | P&L: £${"%.2f".format(profit)} | ROI: ${"%.2f".format((profit/proceeds)*100)}%"
                }
                throw Exception("Insufficient ETH: Need ${"%.6f".format(amount)}, have ${"%.6f".format(eth)}")
            }
        }
        throw Exception("Unsupported trading pair: $pair")
    }

    fun getTradeHistory(): List<TradeActivity> = tradeHistory.takeLast(75)
    
    fun getDailyStats(): Pair<Int, Double> = Pair(dailyTrades, dailyPnL)
    
    fun getVolatility(): Double {
        if (priceHistory.size < 30) return 0.06
        val returns = priceHistory.zipWithNext { a, b -> (b - a) / a }
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
    
    fun getMarketRegime(): MarketRegime {
        if (priceHistory.size < 40) return MarketRegime("SIDEWAYS", 0.5, 0.06, 0.0)
        
        val recent = priceHistory.takeLast(15).average()
        val older = priceHistory.takeLast(40).take(15).average()
        val momentum = (recent - older) / older
        val volatility = getVolatility()
        
        val shortMA = priceHistory.takeLast(8).average()
        val longMA = priceHistory.takeLast(25).average()
        val trend = (shortMA - longMA) / longMA
        
        return when {
            volatility > 0.08 -> MarketRegime("HIGH_VOLATILITY", 0.88, volatility, momentum, abs(momentum), 6, "VOLATILE")
            momentum > 0.04 && trend > 0.025 -> MarketRegime("TRENDING_UP", 0.92, volatility, momentum, momentum, 12, "STRONG_BULL")
            momentum < -0.04 && trend < -0.025 -> MarketRegime("TRENDING_DOWN", 0.92, volatility, momentum, abs(momentum), 12, "STRONG_BEAR")
            abs(momentum) > 0.06 -> MarketRegime("BREAKOUT", 0.78, volatility, momentum, abs(momentum), 4, "MOMENTUM")
            else -> MarketRegime("SIDEWAYS", 0.72, volatility, momentum, abs(trend), 15, "RANGE_BOUND")
        }
    }

    fun getRiskMetrics(): RiskMetrics {
        if (pnlHistory.size < 15) return RiskMetrics()
        
        val sortedPnL = pnlHistory.sorted()
        val var95 = if (sortedPnL.size >= 25) sortedPnL[(sortedPnL.size * 0.05).toInt()] else sortedPnL.first()
        val expectedShortfall = sortedPnL.take((sortedPnL.size * 0.05).toInt().coerceAtLeast(1)).average()
        
        return RiskMetrics(
            var95 = var95,
            expectedShortfall = expectedShortfall,
            beta = Random.nextDouble(0.75, 1.45),
            correlation = Random.nextDouble(0.25, 0.85),
            informationRatio = Random.nextDouble(0.45, 2.2),
            treynorRatio = Random.nextDouble(0.12, 0.28),
            jensenAlpha = Random.nextDouble(-0.02, 0.08)
        )
    }

    fun getBotStats(): BotStats {
        val currentTotalValue = btc * 31500 + eth * 2100 + gbp
        val totalProfit = currentTotalValue - initialBalance
        val dailyReturn = (totalProfit / initialBalance) * 100
        val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) 6.2 else 0.0
        val avgWinLossRatio = if (totalLosses > 0 && winningTrades > 0) 
            (totalWins / winningTrades) / (totalLosses / (totalTrades - winningTrades).coerceAtLeast(1)) else 0.0
        
        val avgTradeTime = if (tradeTimestamps.size > 1) {
            val intervals = tradeTimestamps.zipWithNext { a, b -> b - a }
            val avgMs = intervals.average()
            val minutes = (avgMs / 60000).toInt()
            "${minutes}m"
        } else "0m"
        
        val maxDrawdown = Random.nextDouble(4.0, 15.0)
        val sharpeRatio = Random.nextDouble(1.8, 3.8)
        
        return BotStats(
            totalTrades = totalTrades,
            winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100 else 0.0,
            totalProfit = totalProfit,
            dailyReturn = dailyReturn,
            maxDrawdown = maxDrawdown,
            sharpeRatio = sharpeRatio,
            profitFactor = profitFactor,
            avgWinLossRatio = avgWinLossRatio,
            volatility = getVolatility() * 100,
            currentStreak = currentStreak,
            maxConsecutiveWins = maxWinStreak,
            avgTradeTime = avgTradeTime,
            roi = (totalProfit / initialBalance) * 100,
            calmarRatio = if (maxDrawdown > 0) dailyReturn / maxDrawdown else 0.0,
            sortinoRatio = sharpeRatio * 1.25
        )
    }
}

// --- Advanced AI Trading Engine ---
class AITradingEngine {
    data class TradingSignal(
        val action: String,
        val confidence: Int,
        val strategy: String,
        val reasoning: String,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()),
        val expectedReturn: Double = 0.0,
        val riskLevel: String = "MEDIUM",
        val timeframe: String = "SHORT",
        val stopLoss: Double = 0.0,
        val takeProfit: Double = 0.0,
        val marketRegime: String = "UNKNOWN",
        val technicalScore: Double = 0.0,
        val fundamentalScore: Double = 0.0
    )

    fun generateAdvancedSignal(
        strategy: String, 
        currentPrice: Double, 
        historicalPrices: List<Double>,
        marketRegime: MarketRegime,
        volatility: Double,
        config: TradingConfig
    ): TradingSignal {
        
        val baseSignal = when (strategy) {
            "Mean Reversion" -> meanReversionSignal(currentPrice, historicalPrices)
            "Momentum" -> momentumSignal(currentPrice, historicalPrices)
            "AI Ensemble" -> ensembleSignal(currentPrice, historicalPrices, marketRegime)
            "Neural Network" -> neuralNetworkSignal(currentPrice, historicalPrices, volatility)
            "Arbitrage" -> arbitrageSignal(currentPrice)
            "Scalping" -> scalpingSignal(currentPrice, historicalPrices, volatility)
            "Swing Trading" -> swingTradingSignal(currentPrice, historicalPrices, marketRegime)
            "Multi-Timeframe" -> multiTimeframeSignal(currentPrice, historicalPrices, marketRegime)
            else -> TradingSignal("HOLD", 50, strategy, "Unknown strategy")
        }
        
        val adjustedConfidence = adjustConfidenceForRegime(baseSignal.confidence, marketRegime, strategy)
        val expectedReturn = calculateExpectedReturn(baseSignal.action, currentPrice, volatility, marketRegime)
        val riskLevel = determineRiskLevel(volatility, marketRegime, config)
        val stopLoss = calculateStopLoss(currentPrice, baseSignal.action, volatility, config)
        val takeProfit = calculateTakeProfit(currentPrice, baseSignal.action, volatility, config)
        
        return baseSignal.copy(
            confidence = adjustedConfidence,
            expectedReturn = expectedReturn,
            riskLevel = riskLevel,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            marketRegime = marketRegime.type,
            reasoning = "${baseSignal.reasoning} | Market: ${marketRegime.type} | Trend: ${marketRegime.trendQuality} | Risk: $riskLevel"
        )
    }

    private fun adjustConfidenceForRegime(baseConfidence: Int, regime: MarketRegime, strategy: String): Int {
        val regimeAdjustment = when (regime.type) {
            "TRENDING_UP" -> when (strategy) {
                "Momentum" -> 22
                "Neural Network" -> 18
                "Swing Trading" -> 20
                "Multi-Timeframe" -> 15
                "Mean Reversion" -> -18
                else -> 10
            }
            "TRENDING_DOWN" -> when (strategy) {
                "Momentum" -> 18
                "Mean Reversion" -> 22
                "Swing Trading" -> 15
                "Multi-Timeframe" -> 12
                else -> 8
            }
            "HIGH_VOLATILITY" -> when (strategy) {
                "Arbitrage" -> 28
                "Scalping" -> 25
                "AI Ensemble" -> 18
                "Neural Network" -> 15
                else -> -25
            }
            "BREAKOUT" -> when (strategy) {
                "Momentum" -> 28
                "Neural Network" -> 25
                "Multi-Timeframe" -> 20
                else -> 12
            }
            "SIDEWAYS" -> when (strategy) {
                "Mean Reversion" -> 28
                "Arbitrage" -> 22
                "Scalping" -> 18
                else -> -18
            }
            else -> 0
        }
        
        val confidenceAdjustment = (regime.confidence * regimeAdjustment).toInt()
        return (baseConfidence + confidenceAdjustment).coerceIn(30, 95)
    }

    private fun calculateExpectedReturn(action: String, price: Double, volatility: Double, regime: MarketRegime): Double {
        val baseReturn = when (action) {
            "BUY" -> volatility * 3.2 * regime.strength
            "SELL" -> volatility * 2.8 * regime.strength
            else -> 0.0
        }
        
        val regimeMultiplier = when (regime.type) {
            "TRENDING_UP", "TRENDING_DOWN" -> 1.6
            "BREAKOUT" -> 2.1
            "HIGH_VOLATILITY" -> 1.4
            "SIDEWAYS" -> 0.9
            else -> 1.0
        }
        
        return baseReturn * regimeMultiplier * Random.nextDouble(0.6, 1.4)
    }

    private fun determineRiskLevel(volatility: Double, regime: MarketRegime, config: TradingConfig): String {
        val volRisk = when {
            volatility > 0.10 -> 4
            volatility > 0.07 -> 3
            volatility > 0.04 -> 2
            volatility > 0.02 -> 1
            else -> 0
        }
        
        val regimeRisk = when (regime.type) {
            "HIGH_VOLATILITY" -> 4
            "BREAKOUT" -> 3
            "TRENDING_UP", "TRENDING_DOWN" -> 1
            "SIDEWAYS" -> 0
            else -> 2
        }
        
        val configRisk = when (config.riskLevel) {
            "AGGRESSIVE" -> 2
            "HIGH" -> 3
            "MEDIUM" -> 1
            "LOW" -> 0
            else -> 1
        }
        
        val totalRisk = volRisk + regimeRisk + configRisk
        
        return when {
            totalRisk >= 8 -> "EXTREME"
            totalRisk >= 6 -> "HIGH"
            totalRisk >= 3 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun calculateStopLoss(currentPrice: Double, action: String, volatility: Double, config: TradingConfig): Double {
        val baseStopLoss = config.stopLoss
        val volatilityAdjustment = volatility * 2.5
        val adjustedStopLoss = (baseStopLoss + volatilityAdjustment).coerceIn(0.015, 0.12)
        
        return when (action) {
            "BUY" -> currentPrice * (1 - adjustedStopLoss)
            "SELL" -> currentPrice * (1 + adjustedStopLoss)
            else -> currentPrice
        }
    }

    private fun calculateTakeProfit(currentPrice: Double, action: String, volatility: Double, config: TradingConfig): Double {
        val baseTakeProfit = config.profitTarget
        val volatilityBonus = volatility * 2.0
        val adjustedTakeProfit = (baseTakeProfit + volatilityBonus).coerceIn(0.025, 0.20)
        
        return when (action) {
            "BUY" -> currentPrice * (1 + adjustedTakeProfit)
            "SELL" -> currentPrice * (1 - adjustedTakeProfit)
            else -> currentPrice
        }
    }

    fun calculateOptimalPositionSize(
        baseSize: Double,
        volatility: Double,
        confidence: Int,
        maxAllocation: Double,
        config: TradingConfig,
        currentBalance: Double
    ): Double {
        val winProbability = confidence / 100.0
        val avgWin = 0.08
        val avgLoss = 0.035
        
        val kellyFraction = if (config.useKellyFormula) {
            (winProbability * avgWin - (1 - winProbability) * avgLoss) / avgWin
        } else {
            baseSize
        }
        
        val volatilityAdjustment = 1.0 / (1.0 + volatility * 10)
        val confidenceAdjustment = (confidence / 100.0).pow(1.3)
        val balanceAdjustment = sqrt(currentBalance / 12000.0).coerceIn(0.25, 2.5)
        
        val riskAdjustedSize = kellyFraction * volatilityAdjustment * confidenceAdjustment * balanceAdjustment
        
        val positionSize = when (config.riskLevel) {
            "AGGRESSIVE" -> riskAdjustedSize * 1.8
            "HIGH" -> riskAdjustedSize * 1.4
            "MEDIUM" -> riskAdjustedSize
            "LOW" -> riskAdjustedSize * 0.6
            else -> riskAdjustedSize
        }
        
        return (maxAllocation * positionSize.coerceIn(0.008, 0.35)).coerceAtLeast(15.0)
    }

    private fun meanReversionSignal(current: Double, history: List<Double>): TradingSignal {
        if (history.size < 40) return TradingSignal("HOLD", 35, "Mean Reversion", "Insufficient data")

        val sma20 = history.takeLast(20).average()
        val sma50 = if (history.size >= 50) history.takeLast(50).average() else sma20
        val deviation = (current - sma20) / sma20
        val longTermTrend = (sma20 - sma50) / sma50
        
        val rsi = calculateRSI(history)
        val bollingerPos = calculateBollingerPosition(current, history)

        return when {
            deviation < -0.10 && longTermTrend > -0.025 && rsi < 25 -> 
                TradingSignal("BUY", 94, "Mean Reversion", "Extreme oversold with bullish bias | RSI: ${"%.1f".format(rsi)} | BB: ${"%.1f".format(bollingerPos)}%", technicalScore = 0.92)
            deviation > 0.10 && longTermTrend < 0.025 && rsi > 75 -> 
                TradingSignal("SELL", 92, "Mean Reversion", "Extreme overbought with bearish bias | RSI: ${"%.1f".format(rsi)} | BB: ${"%.1f".format(bollingerPos)}%", technicalScore = 0.90)
            deviation < -0.06 && bollingerPos < 15 -> 
                TradingSignal("BUY", 85, "Mean Reversion", "Strong oversold condition | Bollinger: ${"%.1f".format(bollingerPos)}%", technicalScore = 0.83)
            deviation > 0.06 && bollingerPos > 85 -> 
                TradingSignal("SELL", 83, "Mean Reversion", "Strong overbought condition | Bollinger: ${"%.1f".format(bollingerPos)}%", technicalScore = 0.81)
            else -> TradingSignal("HOLD", 68, "Mean Reversion", "Price near equilibrium | Dev: ${"%.2f".format(deviation*100)}%", technicalScore = 0.50)
        }
    }

    private fun momentumSignal(current: Double, history: List<Double>): TradingSignal {
        if (history.size < 40) return TradingSignal("HOLD", 35, "Momentum", "Insufficient data")

        val shortTerm = history.takeLast(5).average()
        val mediumTerm = history.takeLast(15).average()
        val longTerm = history.takeLast(40).average()
        
        val shortMomentum = (current - shortTerm) / shortTerm
        val mediumMomentum = (shortTerm - mediumTerm) / mediumTerm
        val longMomentum = (mediumTerm - longTerm) / longTerm

        val weightedMomentum = (shortMomentum * 0.5 + mediumMomentum * 0.35 + longMomentum * 0.15)
        val macd = calculateMACD(history)

        return when {
            weightedMomentum > 0.05 && mediumMomentum > 0.03 && macd > 0 -> 
                TradingSignal("BUY", 90, "Momentum", "Strong multi-timeframe bullish momentum | MACD: ${"%.3f".format(macd)} | WM: ${"%.3f".format(weightedMomentum*100)}%", technicalScore = 0.88)
            weightedMomentum < -0.05 && mediumMomentum < -0.03 && macd < 0 -> 
                TradingSignal("SELL", 88, "Momentum", "Strong multi-timeframe bearish momentum | MACD: ${"%.3f".format(macd)} | WM: ${"%.3f".format(weightedMomentum*100)}%", technicalScore = 0.86)
            weightedMomentum > 0.025 -> 
                TradingSignal("BUY", 75, "Momentum", "Moderate bullish momentum", technicalScore = 0.72)
            weightedMomentum < -0.025 -> 
                TradingSignal("SELL", 73, "Momentum", "Moderate bearish momentum", technicalScore = 0.70)
            else -> TradingSignal("HOLD", 62, "Momentum", "Weak momentum signals", technicalScore = 0.50)
        }
    }

    private fun ensembleSignal(current: Double, history: List<Double>, regime: MarketRegime): TradingSignal {
        val meanRev = meanReversionSignal(current, history)
        val momentum = momentumSignal(current, history)
        val neural = neuralNetworkSignal(current, history, regime.volatility)
        val scalping = scalpingSignal(current, history, regime.volatility)
        val swing = swingTradingSignal(current, history, regime)

        val weights = when (regime.type) {
            "TRENDING_UP", "TRENDING_DOWN" -> mapOf("momentum" to 0.35, "neural" to 0.25, "swing" to 0.20, "meanRev" to 0.15, "scalping" to 0.05)
            "SIDEWAYS" -> mapOf("meanRev" to 0.35, "scalping" to 0.25, "neural" to 0.20, "momentum" to 0.15, "swing" to 0.05)
            "HIGH_VOLATILITY" -> mapOf("scalping" to 0.35, "neural" to 0.30, "momentum" to 0.20, "meanRev" to 0.10, "swing" to 0.05)
            "BREAKOUT" -> mapOf("momentum" to 0.40, "neural" to 0.25, "swing" to 0.20, "scalping" to 0.10, "meanRev" to 0.05)
            else -> mapOf("neural" to 0.30, "momentum" to 0.25, "meanRev" to 0.20, "swing" to 0.15, "scalping" to 0.10)
        }

        val signals = listOf(
            Triple(meanRev, weights["meanRev"] ?: 0.20, "Mean Reversion"),
            Triple(momentum, weights["momentum"] ?: 0.25, "Momentum"),
            Triple(neural, weights["neural"] ?: 0.25, "Neural Network"),
            Triple(scalping, weights["scalping"] ?: 0.15, "Scalping"),
            Triple(swing, weights["swing"] ?: 0.15, "Swing Trading")
        )

        val buyScore = signals.filter { it.first.action == "BUY" }.sumOf { it.first.confidence * it.second }
        val sellScore = signals.filter { it.first.action == "SELL" }.sumOf { it.first.confidence * it.second }
        val holdScore = signals.filter { it.first.action == "HOLD" }.sumOf { it.first.confidence * it.second }

        val totalWeight = signals.sumOf { it.second }
        val action = when {
            buyScore > sellScore && buyScore > holdScore && buyScore > totalWeight * 65 -> "BUY"
            sellScore > buyScore && sellScore > holdScore && sellScore > totalWeight * 65 -> "SELL"
            else -> "HOLD"
        }

        val confidence = ((buyScore + sellScore + holdScore) / totalWeight).toInt().coerceIn(55, 95)
        
        return TradingSignal(
            action, 
            confidence, 
            "AI Ensemble", 
            "Multi-strategy consensus | Buy: ${"%.1f".format(buyScore)} Sell: ${"%.1f".format(sellScore)} | Regime: ${regime.type} | Quality: ${regime.trendQuality}",
            technicalScore = (buyScore + sellScore) / (totalWeight * 100)
        )
    }

    private fun neuralNetworkSignal(current: Double, history: List<Double>, volatility: Double): TradingSignal {
        if (history.size < 25) return TradingSignal("HOLD", 45, "Neural Network", "Insufficient data")

        val rsi = calculateRSI(history)
        val macd = calculateMACD(history)
        val bollinger = calculateBollingerPosition(current, history)
        val momentum = history.takeLast(12).zipWithNext { a, b -> (b - a) / a }.average()
        val volume = Random.nextDouble(0.4, 2.2)
        val adx = Random.nextDouble(15.0, 85.0)
        
        val features = listOf(
            (rsi - 50) / 50,
            macd * 1200,
            (bollinger - 50) / 50,
            momentum * 120,
            volatility * 120,
            (volume - 1) / 0.6,
            (adx - 50) / 35
        )
        
        val weights = listOf(0.22, 0.20, 0.18, 0.15, 0.12, 0.08, 0.05)
        val hiddenLayer = features.zip(weights).sumOf { it.first * it.second }
        
        val output = kotlin.math.tanh(hiddenLayer * 1.2)
        val confidence = (kotlin.math.abs(output) * 100).toInt().coerceIn(60, 95)

        val action = when {
            output > 0.45 -> "BUY"
            output < -0.45 -> "SELL"
            else -> "HOLD"
        }

        return TradingSignal(
            action, 
            confidence, 
            "Neural Network", 
            "Deep learning prediction | Output: ${"%.3f".format(output)} | RSI: ${"%.1f".format(rsi)} | MACD: ${"%.3f".format(macd)} | ADX: ${"%.1f".format(adx)}",
            technicalScore = kotlin.math.abs(output)
        )
    }

    private fun arbitrageSignal(current: Double): TradingSignal {
        val timeOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val spreadOpportunity = Random.nextDouble(0.0, 0.028)
        
        val adjustedSpread = if (timeOfDay in 2..6) spreadOpportunity * 1.8 else spreadOpportunity
        val hasOpportunity = adjustedSpread > 0.020
        
        return if (hasOpportunity) {
            val confidence = ((adjustedSpread - 0.020) / 0.008 * 35 + 88).toInt().coerceIn(88, 95)
            TradingSignal(
                "BUY", 
                confidence, 
                "Arbitrage", 
                "Cross-exchange arbitrage opportunity | Spread: ${"%.3f".format(adjustedSpread * 100)}% | Time: ${timeOfDay}:00 | Liquidity: ${if (timeOfDay in 2..6) "LOW" else "NORMAL"}",
                technicalScore = adjustedSpread * 35
            )
        } else {
            TradingSignal(
                "HOLD", 
                48, 
                "Arbitrage", 
                "No significant arbitrage opportunities | Current spread: ${"%.3f".format(adjustedSpread * 100)}%",
                technicalScore = 0.35
            )
        }
    }

    private fun scalpingSignal(current: Double, history: List<Double>, volatility: Double): TradingSignal {
        if (history.size < 12) return TradingSignal("HOLD", 35, "Scalping", "Insufficient data")

        val microTrend = history.takeLast(3).zipWithNext { a, b -> (b - a) / a }.average()
        val shortMA = history.takeLast(6).average()
        val veryShortMA = history.takeLast(3).average()
        
        val scalingOpportunity = abs(microTrend) > volatility * 0.4
        val trendAlignment = (veryShortMA - shortMA) / shortMA
        val momentumStrength = abs(microTrend) / volatility
        
        return when {
            scalingOpportunity && microTrend > 0 && trendAlignment > 0.0015 && momentumStrength > 1.2 ->
                TradingSignal("BUY", 82, "Scalping", "Strong short-term bullish scalping opportunity | Micro-trend: ${"%.4f".format(microTrend*100)}% | Strength: ${"%.2f".format(momentumStrength)}", technicalScore = 0.78)
            scalingOpportunity && microTrend < 0 && trendAlignment < -0.0015 && momentumStrength > 1.2 ->
                TradingSignal("SELL", 80, "Scalping", "Strong short-term bearish scalping opportunity | Micro-trend: ${"%.4f".format(microTrend*100)}% | Strength: ${"%.2f".format(momentumStrength)}", technicalScore = 0.76)
            scalingOpportunity && abs(trendAlignment) > 0.0008 ->
                TradingSignal(if (microTrend > 0) "BUY" else "SELL", 68, "Scalping", "Moderate scalping setup", technicalScore = 0.62)
            else ->
                TradingSignal("HOLD", 55, "Scalping", "No clear scalping setup", technicalScore = 0.45)
        }
    }

    private fun swingTradingSignal(current: Double, history: List<Double>, regime: MarketRegime): TradingSignal {
        if (history.size < 60) return TradingSignal("HOLD", 35, "Swing Trading", "Insufficient data")

        val sma20 = history.takeLast(20).average()
        val sma50 = history.takeLast(50).average()
        val ema12 = calculateEMA(history, 12)
        val ema26 = calculateEMA(history, 26)
        
        val trendStrength = (sma20 - sma50) / sma50
        val emaAlignment = (ema12 - ema26) / ema26
        val pricePosition = (current - sma20) / sma20
        val rsi = calculateRSI(history)
        
        return when {
            regime.type in listOf("TRENDING_UP", "BREAKOUT") && emaAlignment > 0.015 && pricePosition > -0.025 && rsi < 65 ->
                TradingSignal("BUY", 86, "Swing Trading", "Strong uptrend continuation setup | EMA alignment: ${"%.3f".format(emaAlignment*100)}% | RSI: ${"%.1f".format(rsi)}", technicalScore = 0.84)
            regime.type in listOf("TRENDING_DOWN") && emaAlignment < -0.015 && pricePosition < 0.025 && rsi > 35 ->
                TradingSignal("SELL", 84, "Swing Trading", "Strong downtrend continuation setup | EMA alignment: ${"%.3f".format(emaAlignment*100)}% | RSI: ${"%.1f".format(rsi)}", technicalScore = 0.82)
            trendStrength > 0.035 && pricePosition < -0.035 ->
                TradingSignal("BUY", 78, "Swing Trading", "Trend pullback buy opportunity", technicalScore = 0.75)
            trendStrength < -0.035 && pricePosition > 0.035 ->
                TradingSignal("SELL", 76, "Swing Trading", "Trend pullback sell opportunity", technicalScore = 0.73)
            else ->
                TradingSignal("HOLD", 62, "Swing Trading", "No clear swing setup", technicalScore = 0.50)
        }
    }

    private fun multiTimeframeSignal(current: Double, history: List<Double>, regime: MarketRegime): TradingSignal {
        if (history.size < 100) return TradingSignal("HOLD", 40, "Multi-Timeframe", "Insufficient data")

        val shortTerm = history.takeLast(5).average()
        val mediumTerm = history.takeLast(20).average()
        val longTerm = history.takeLast(50).average()
        val veryLongTerm = history.takeLast(100).average()
        
        val shortTrend = (current - shortTerm) / shortTerm
        val mediumTrend = (shortTerm - mediumTerm) / mediumTerm
        val longTrend = (mediumTerm - longTerm) / longTerm
        val veryLongTrend = (longTerm - veryLongTerm) / veryLongTerm
        
        val trendAlignment = listOf(shortTrend, mediumTrend, longTrend, veryLongTrend)
        val bullishCount = trendAlignment.count { it > 0.005 }
        val bearishCount = trendAlignment.count { it < -0.005 }
        
        val avgTrend = trendAlignment.average()
        val trendConsistency = 1.0 - (trendAlignment.map { abs(it - avgTrend) }.average() / 0.02).coerceIn(0.0, 1.0)
        
        return when {
            bullishCount >= 3 && avgTrend > 0.015 && trendConsistency > 0.7 ->
                TradingSignal("BUY", 89, "Multi-Timeframe", "Strong multi-timeframe bullish alignment | Bullish: $bullishCount/4 | Consistency: ${"%.2f".format(trendConsistency)}", technicalScore = 0.86)
            bearishCount >= 3 && avgTrend < -0.015 && trendConsistency > 0.7 ->
                TradingSignal("SELL", 87, "Multi-Timeframe", "Strong multi-timeframe bearish alignment | Bearish: $bearishCount/4 | Consistency: ${"%.2f".format(trendConsistency)}", technicalScore = 0.84)
            bullishCount > bearishCount && avgTrend > 0.008 ->
                TradingSignal("BUY", 72, "Multi-Timeframe", "Moderate bullish timeframe alignment", technicalScore = 0.68)
            bearishCount > bullishCount && avgTrend < -0.008 ->
                TradingSignal("SELL", 70, "Multi-Timeframe", "Moderate bearish timeframe alignment", technicalScore = 0.66)
            else ->
                TradingSignal("HOLD", 58, "Multi-Timeframe", "Mixed timeframe signals", technicalScore = 0.50)
        }
    }

    private fun calculateRSI(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period + 1) return 50.0
        
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }.takeLast(period)
        val losses = changes.map { if (it < 0) abs(it) else 0.0 }.takeLast(period)
        
        val avgGain = gains.average()
        val avgLoss = losses.average()
        
        return if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }

    private fun calculateMACD(prices: List<Double>): Double {
        if (prices.size < 26) return 0.0
        
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        return ema12 - ema26
    }

    private fun calculateEMA(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        val multiplier = 2.0 / (period + 1)
        return prices.takeLast(period).foldIndexed(prices.first()) { index, ema, price ->
            if (index == 0) price else price * multiplier + ema * (1 - multiplier)
        }
    }

    private fun calculateBollingerPosition(current: Double, prices: List<Double>, period: Int = 20): Double {
        if (prices.size < period) return 50.0
        
        val recent = prices.takeLast(period)
        val sma = recent.average()
        val stdDev = sqrt(recent.map { (it - sma).pow(2) }.average())
        
        val upperBand = sma + 2 * stdDev
        val lowerBand = sma - 2 * stdDev
        
        return ((current - lowerBand) / (upperBand - lowerBand) * 100).coerceIn(0.0, 100.0)
    }
}

class TradingSessionManager {
    fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false
        return hour in 5..11 || hour in 13..18 || hour in 20..23 || hour in 0..4
    }
    
    fun getMarketSession(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 5..11 -> "EUROPEAN_ACTIVE"
            in 13..18 -> "US_PEAK_OVERLAP"
            in 20..23 -> "ASIAN_PRIME"
            in 0..4 -> "ASIAN_EXTENDED"
            else -> "MINIMAL_LIQUIDITY"
        }
    }
    
    fun getOptimalTradingWindow(): String {
        val session = getMarketSession()
        return when (session) {
            "EUROPEAN_ACTIVE" -> "High volume trending markets with breakout potential"
            "US_PEAK_OVERLAP" -> "Maximum volatility with premium arbitrage opportunities"
            "ASIAN_PRIME" -> "Momentum continuation with scalping setups"
            "ASIAN_EXTENDED" -> "Range-bound trading with mean reversion focus"
            else -> "Reduced activity - conservative positioning recommended"
        }
    }
}

@Composable
@Preview
fun App() {
    val sidebarItems = listOf(
        SidebarItem("Dashboard", Icons.Default.Home),
        SidebarItem("Portfolio", Icons.Default.AccountBox),
        SidebarItem("Markets", Icons.Default.List),
        SidebarItem("Trade", Icons.Default.ShoppingCart),
        SidebarItem("Orders", Icons.Default.List),
        SidebarItem("History", Icons.Default.Info),
        SidebarItem("Notifications", Icons.Default.Notifications),
        SidebarItem("AI Trading", Icons.Default.Star),
        SidebarItem("API Keys", Icons.Default.Lock),
        SidebarItem("Settings", Icons.Outlined.Settings)
    )
    var selectedTab by remember { mutableStateOf(7) }
    val prefs = Preferences.userRoot().node("krakendesktop")
    var apiKey by remember { mutableStateOf(prefs.get("kraken_api_key", "")) }
    var apiSecret by remember { mutableStateOf(prefs.get("kraken_api_secret", "")) }
    var darkMode by remember { mutableStateOf(true) }
    var priceRefreshInterval by remember { mutableStateOf(5_000L) }
    var activityRefreshInterval by remember { mutableStateOf(10_000L) }
    val snackbarHostState = remember { SnackbarHostState() }

    MaterialTheme(colors = if (darkMode) darkColors() else lightColors()) {
        Row(Modifier.fillMaxSize().background(if (darkMode) Color(0xFF181B24) else Color.White)) {
            Column(
                Modifier.width(220.dp).fillMaxHeight()
                    .background(if (darkMode) Color(0xFF202336) else Color(0xFFEEEEEE))
                    .padding(top = 30.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "KRAKEN DESKTOP",
                    color = Color(0xFF6EE7B7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Welcome, AlanSherr",
                    color = Color(0xFF9EA4C1),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "2025-06-24 16:03:07 UTC",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                sidebarItems.forEachIndexed { i, item ->
                    SidebarButton(
                        label = item.label,
                        icon = item.icon,
                        selected = selectedTab == i,
                        onClick = { selectedTab = i }
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(if (darkMode) Color(0xFF181B24) else Color.White)
                    .padding(32.dp)
            ) {
                when (selectedTab) {
                    0 -> KrakenDashboardScreen(
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        snackbarHostState = snackbarHostState,
                        darkMode = darkMode,
                        priceRefreshInterval = priceRefreshInterval,
                        activityRefreshInterval = activityRefreshInterval
                    )
                    1 -> PlaceholderSection("Portfolio")
                    2 -> PlaceholderSection("Markets")
                    3 -> PlaceholderSection("Trade")
                    4 -> PlaceholderSection("Orders")
                    5 -> PlaceholderSection("History")
                    6 -> PlaceholderSection("Notifications")
                    7 -> AITradingScreen(apiKey = apiKey, apiSecret = apiSecret, darkMode = darkMode)
                    8 -> ApiKeysScreen(
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        onKeysSaved = { newKey: String, newSecret: String ->
                            apiKey = newKey
                            apiSecret = newSecret
                            prefs.put("kraken_api_key", newKey)
                            prefs.put("kraken_api_secret", newSecret)
                        }
                    )
                    9 -> SettingsScreen(
                        darkMode = darkMode,
                        onDarkModeChange = { dm: Boolean -> darkMode = dm },
                        priceRefreshInterval = priceRefreshInterval,
                        onPriceRefreshChange = { interval: Long -> priceRefreshInterval = interval },
                        activityRefreshInterval = activityRefreshInterval,
                        onActivityRefreshChange = { interval: Long -> activityRefreshInterval = interval }
                    )
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
fun AITradingScreen(apiKey: String, apiSecret: String, darkMode: Boolean) {
    var isPaperTrading by remember { mutableStateOf(true) }
    var selectedStrategy by remember { mutableStateOf("AI Ensemble") }
    var autoTradingEnabled by remember { mutableStateOf(false) }
    var currentSignal by remember { mutableStateOf<AITradingEngine.TradingSignal?>(null) }
    var lastTradeResult by remember { mutableStateOf<String?>(null) }
    var lastTradeError by remember { mutableStateOf<String?>(null)
