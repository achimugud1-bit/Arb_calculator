package com.arbmaster.pro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbmaster.pro.data.*
import com.arbmaster.pro.math.ArbMath
import kotlinx.coroutines.launch
import java.math.BigInteger

private val CanvasBg = Color(0xFF090C10)
private val SurfaceBg = Color(0xFF11161D)
private val ElevatedBg = Color(0xFF171E28)
private val BorderCol = Color(0xFF222C3A)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B97A8)
private val Emerald = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)
private val Crimson = Color(0xFFEF4444)

private val TerminalColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF04140E),
    background = CanvasBg,
    onBackground = TextPrimary,
    surface = SurfaceBg,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedBg,
    onSurfaceVariant = TextMuted,
    outline = BorderCol,
    error = Crimson
)

enum class AppTab(val label: String) {
    CALC("Calculator"),
    LIVE("Live Arb"),
    TRACKER("Tracker"),
    MONEY("Money"),
    AUDIT("Audit")
}

@Composable
fun ArbMasterApp(repo: ArbRepository) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.CALC) }

    // Shared calculator state so Live Arb Search can hand off directly into staking
    var modeCount by remember { mutableIntStateOf(2) }
    var budget by remember { mutableStateOf("10000") }
    var currency by remember { mutableStateOf("NGN") }
    var eventLabel by remember { mutableStateOf("") }
    var legInputs by remember {
        mutableStateOf(
            listOf(
                ArbMath.LegInput(odds = "2.15", name = "Outcome A", book = "SportyBet"),
                ArbMath.LegInput(odds = "2.05", name = "Outcome B", book = "Bet9ja"),
                ArbMath.LegInput(odds = "3.60", name = "Draw", book = "Bet365"),
                ArbMath.LegInput(odds = "", name = "Outcome D"),
                ArbMath.LegInput(odds = "", name = "Outcome E"),
                ArbMath.LegInput(odds = "", name = "Outcome F")
            )
        )
    }

    MaterialTheme(colorScheme = TerminalColorScheme) {
        Scaffold(
            containerColor = CanvasBg,
            topBar = {
                Surface(color = SurfaceBg, tonalElevation = 0.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ARBMASTER PRO",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = currentTab.label,
                            color = Emerald,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = SurfaceBg, tonalElevation = 0.dp) {
                    val items = listOf(
                        Triple(AppTab.CALC, "Calc", Icons.Default.Calculate),
                        Triple(AppTab.LIVE, "Live Arb", Icons.Default.Radar),
                        Triple(AppTab.TRACKER, "Tracker", Icons.Default.List),
                        Triple(AppTab.MONEY, "Money", Icons.Default.AccountBalanceWallet),
                        Triple(AppTab.AUDIT, "Audit", Icons.Default.Shield)
                    )
                    items.forEach { (tab, label, icon) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Emerald,
                                selectedTextColor = Emerald,
                                indicatorColor = ElevatedBg,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(CanvasBg)
            ) {
                when (currentTab) {
                    AppTab.CALC -> CalculatorTab(
                        modeCount = modeCount,
                        onModeChange = { modeCount = it },
                        budget = budget,
                        onBudgetChange = { budget = it },
                        currency = currency,
                        onCurrencyChange = { currency = it },
                        eventLabel = eventLabel,
                        legs = legInputs,
                        onLegChange = { idx, updated ->
                            legInputs = legInputs.toMutableList().also { it[idx] = updated }
                        },
                        onCopy = { text -> copyToClipboard(context, text) }
                    )

                    AppTab.LIVE -> LiveArbTab(
                        repo = repo,
                        budget = budget,
                        currency = currency,
                        onLoadToCalculator = { opp ->
                            modeCount = opp.legs.size.coerceIn(2, 6)
                            eventLabel = "${opp.event} · ${opp.market}"
                            legInputs = List(6) { idx ->
                                val src = opp.legs.getOrNull(idx)
                                if (src != null) {
                                    ArbMath.LegInput(odds = src.odds, name = src.name, book = src.book)
                                } else {
                                    ArbMath.LegInput(odds = "", name = "Outcome ${'A' + idx}")
                                }
                            }
                            currentTab = AppTab.CALC
                            Toast.makeText(context, "Loaded ${opp.event} into Calculator", Toast.LENGTH_SHORT).show()
                        }
                    )

                    AppTab.TRACKER -> BetTrackerTab(repo = repo)
                    AppTab.MONEY -> MoneyTrackerTab(repo = repo)
                    AppTab.AUDIT -> AuditTab(repo = repo)
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Stake", text))
    Toast.makeText(context, "Copied stake: $text", Toast.LENGTH_SHORT).show()
}

@Composable
private fun CalculatorTab(
    modeCount: Int,
    onModeChange: (Int) -> Unit,
    budget: String,
    onBudgetChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    eventLabel: String,
    legs: List<ArbMath.LegInput>,
    onLegChange: (Int, ArbMath.LegInput) -> Unit,
    onCopy: (String) -> Unit
) {
    val activeLegs = legs.take(modeCount)
    val calcResult = remember(budget, activeLegs) {
        runCatching { ArbMath.arbitrate(budgetStr = budget, rows = activeLegs) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (eventLabel.isNotBlank()) {
            item {
                Text(
                    text = eventLabel,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Result Hero Summary Card
        item {
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val res = calcResult.getOrNull()
                    val err = calcResult.exceptionOrNull()
                    if (res != null) {
                        val isPositive = res.worstProfit > BigInteger.ZERO
                        val tone = if (isPositive) Emerald else Crimson
                        Text(
                            text = if (isPositive) "Guaranteed Arbitrage Profit" else "Negative Yield (Overround)",
                            color = tone,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = (if (res.worstProfit > BigInteger.ZERO) "+" else "") +
                                ArbMath.formatMoney(res.worstProfit, currency),
                            color = tone,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricPair("Net ROI", "${ArbMath.formatFixed(res.roiPct, 2)}%", tone)
                            MetricPair("Implied Sum", "${ArbMath.formatFixed(res.combinedImpliedPct, 2)}%", TextPrimary)
                            MetricPair("Total Stake", ArbMath.formatMoney(res.cost, currency), TextPrimary)
                        }
                    } else {
                        Text(
                            text = err?.message ?: "Enter decimal odds > 1.00 to calculate stakes.",
                            color = Amber,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Mode & Currency Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2 to "2-Way", 3 to "3-Way", 4 to "4-Way").forEach { (n, lbl) ->
                    val selected = modeCount == n
                    OutlinedButton(
                        onClick = { onModeChange(n) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) ElevatedBg else Color.Transparent,
                            contentColor = if (selected) Emerald else TextMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) Emerald else BorderCol),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Budget & Currency Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = onBudgetChange,
                    label = { Text("Total Budget ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                OutlinedButton(
                    onClick = {
                        val next = when (currency) {
                            "NGN" -> "USD"
                            "USD" -> "GBP"
                            "GBP" -> "EUR"
                            else -> "NGN"
                        }
                        onCurrencyChange(next)
                    },
                    modifier = Modifier.height(56.dp),
                    border = BorderStroke(1.dp, BorderCol),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(currency, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Outcome Leg Inputs + Calculated Whole Stakes
        items(modeCount) { idx ->
            val row = legs[idx]
            val legRes = calcResult.getOrNull()?.legs?.getOrNull(idx)
            val stakeWhole = legRes?.let { ArbMath.formatFixed(it.stake, 0) } ?: "—"

            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${'A' + idx} · ${row.name.ifBlank { "Outcome ${'A' + idx}" }}${if (row.book.isNotBlank()) " (${row.book})" else ""}",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (legRes != null) {
                            TextButton(
                                onClick = { onCopy(stakeWhole) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Copy $stakeWhole", color = Emerald, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = row.odds,
                            onValueChange = { onLegChange(idx, row.odds.let { _ -> row.copy(odds = it) }) },
                            label = { Text("Decimal Odds") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        )
                        OutlinedTextField(
                            value = row.fixed,
                            onValueChange = { onLegChange(idx, row.copy(fixed = it)) },
                            label = { Text("Lock Stake") },
                            placeholder = { Text(stakeWhole) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        )
                    }

                    if (legRes != null) {
                        Text(
                            text = "Stake: ${ArbMath.formatMoney(legRes.stake, currency)}  ·  Return: ${ArbMath.formatMoney(legRes.receipt, currency)}  ·  Net: ${ArbMath.formatMoney(legRes.profit, currency)}",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveArbTab(
    repo: ArbRepository,
    budget: String,
    currency: String,
    onLoadToCalculator: (LiveOpportunityItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sport by remember { mutableStateOf("Football") }
    var query by remember { mutableStateOf("") }
    var bookmakers by remember { mutableStateOf("SportyBet, Bet9ja, Bet365, 1xBet, Pinnacle") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("Tap Scan Upcoming Games to find real pre-match odds and verified stake splits.") }
    var results by remember { mutableStateOf<List<LiveOpportunityItem>>(emptyList()) }

    fun triggerScan() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                repo.scanUpcomingArbitrage(
                    sport = sport,
                    market = "any",
                    query = query,
                    bookmakers = bookmakers,
                    engine = "web"
                )
            }.onSuccess { res ->
                results = res.opportunities
                notice = res.notice.ifBlank { "Found ${res.opportunities.size} upcoming fixtures." }
            }.onFailure { ex ->
                error = ex.message ?: "Live scan failed."
            }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Live Upcoming Arbitrage Search", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all", "Football", "Basketball", "Tennis").forEach { sp ->
                            val active = sport == sp
                            OutlinedButton(
                                onClick = { sport = sp },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (active) ElevatedBg else Color.Transparent,
                                    contentColor = if (active) Emerald else TextMuted
                                ),
                                border = BorderStroke(1.dp, if (active) Emerald else BorderCol)
                            ) {
                                Text(if (sp == "all") "All" else sp, fontSize = 12.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("League or Team (optional)") },
                        placeholder = { Text("e.g. Premier League, Champions League, NBA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bookmakers,
                        onValueChange = { bookmakers = it },
                        label = { Text("Bookmakers to Compare") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { triggerScan() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF04140E))
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF04140E))
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning Upcoming Matches...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Scan Upcoming Games Now", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (error != null) {
                        Text(error!!, color = Crimson, fontSize = 12.sp)
                    } else {
                        Text(notice, color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        items(results, key = { it.id }) { opp ->
            val previewPlan = remember(opp, budget) {
                runCatching {
                    ArbMath.arbitrate(
                        budgetStr = budget,
                        rows = opp.legs.map { ArbMath.LegInput(odds = it.odds, name = it.name, book = it.book) }
                    )
                }.getOrNull()
            }
            val tone = if (opp.isArbitrage) Emerald else Amber
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (opp.isArbitrage) Emerald.copy(alpha = 0.55f) else BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(opp.event, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                            Text(
                                "${opp.sport} · ${opp.league} · ${opp.market}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (opp.isArbitrage) "+${String.format("%.2f", opp.arbMarginPct)}% ARB" else "${String.format("%.2f", opp.impliedSumPct)}% Sum",
                                color = tone,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (previewPlan != null && previewPlan.worstProfit > BigInteger.ZERO) {
                                Text(
                                    text = "+${ArbMath.formatMoney(previewPlan.worstProfit, currency)}",
                                    color = Emerald,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    opp.legs.forEachIndexed { idx, leg ->
                        val st = previewPlan?.legs?.getOrNull(idx)?.stake
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElevatedBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(leg.book, color = Emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(leg.name, color = TextPrimary, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(leg.odds, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                if (st != null) {
                                    Text(
                                        "Stake ${ArbMath.formatMoney(st, currency)}",
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onLoadToCalculator(opp) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedBg, contentColor = Emerald)
                    ) {
                        Text("Load & Stake in Calculator", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BetTrackerTab(repo: ArbRepository) {
    var bets by remember { mutableStateOf(repo.loadBets()) }
    var event by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var book by remember { mutableStateOf("SportyBet") }
    var odds by remember { mutableStateOf("") }
    var stake by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log New Bet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(value = event, onValueChange = { event = it }, label = { Text("Event") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = selection, onValueChange = { selection = it }, label = { Text("Pick") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = book, onValueChange = { book = it }, label = { Text("Book") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = odds, onValueChange = { odds = it }, label = { Text("Odds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = stake, onValueChange = { stake = it }, label = { Text("Stake (NGN)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (event.isNotBlank() && odds.isNotBlank() && stake.isNotBlank()) {
                                val next = listOf(
                                    BetEntry(event = event.trim(), selection = selection.trim(), book = book.trim(), odds = odds.trim(), stake = stake.trim(), payout = "", status = "Pending")
                                ) + bets
                                bets = next
                                repo.saveBets(next)
                                event = ""; selection = ""; odds = ""; stake = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF04140E))
                    ) {
                        Text("Save Bet to Phone", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(bets, key = { it.id }) { b ->
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.event, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${b.book} · ${b.selection} @ ${b.odds} · Stake ${b.stake} ${b.currency}", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = {
                        val next = bets.filterNot { it.id == b.id }
                        bets = next
                        repo.saveBets(next)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyTrackerTab(repo: ArbRepository) {
    var moves by remember { mutableStateOf(repo.loadCashMovements()) }
    var kind by remember { mutableStateOf("Deposit") }
    var amount by remember { mutableStateOf("") }

    val balance = moves.sumOf {
        val v = it.amount.toDoubleOrNull() ?: 0.0
        if (it.kind == "Withdrawal") -v else v
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Net Bankroll Balance (NGN)", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = "₦${String.format("%,.2f", balance)}",
                        color = if (balance >= 0) Emerald else Crimson,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                }
            }
        }
        item {
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Deposit", "Withdrawal").forEach { k ->
                            val active = kind == k
                            OutlinedButton(
                                onClick = { kind = k },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, if (active) Emerald else BorderCol),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (active) ElevatedBg else Color.Transparent,
                                    contentColor = if (active) Emerald else TextMuted
                                )
                            ) {
                                Text(k)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (NGN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if ((amount.toDoubleOrNull() ?: 0.0) > 0.0) {
                                val next = listOf(CashEntry(kind = kind, amount = amount.trim())) + moves
                                moves = next
                                repo.saveCashMovements(next)
                                amount = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF04140E))
                    ) {
                        Text("Record $kind", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(moves, key = { it.id }) { m ->
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.kind, fontWeight = FontWeight.Medium)
                    Text(
                        text = (if (m.kind == "Withdrawal") "-" else "+") + "₦${m.amount}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (m.kind == "Withdrawal") Crimson else Emerald
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditTab(repo: ArbRepository) {
    val findings = remember { repo.getStaticAuditFindings() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Static Analysis · ARBMASTER-PRO-source (1).zip (18 files · 2,920 LOC)",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        items(findings, key = { it.id }) { item ->
            val tone = when (item.severity) {
                "HIGH" -> Crimson
                "MEDIUM" -> Amber
                else -> Emerald
            }
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderCol),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.id} · ${item.severity} · ${item.category}", color = tone, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(item.location, color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(item.summary, color = TextMuted, fontSize = 12.sp)
                    Text("Fix: ${item.resolution}", color = Emerald, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricPair(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
