package com.arbmaster.pro.data

import android.content.Context
import com.arbmaster.pro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class LiveLeg(
    val name: String,
    val book: String,
    val odds: String
)

data class LiveOpportunityItem(
    val id: String,
    val event: String,
    val sport: String,
    val league: String,
    val market: String,
    val commenceTime: String,
    val impliedSumPct: Double,
    val arbMarginPct: Double,
    val isArbitrage: Boolean,
    val notes: String,
    val legs: List<LiveLeg>
)

data class LiveScanResult(
    val opportunities: List<LiveOpportunityItem>,
    val notice: String,
    val scannedAt: Long
)

data class BetEntry(
    val id: String = UUID.randomUUID().toString(),
    val event: String,
    val selection: String,
    val book: String,
    val odds: String,
    val stake: String,
    val payout: String,
    val status: String,
    val currency: String = "NGN"
)

data class CashEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val amount: String,
    val currency: String = "NGN",
    val timestamp: Long = System.currentTimeMillis()
)

data class AuditItem(
    val id: String,
    val severity: String,
    val category: String,
    val title: String,
    val location: String,
    val summary: String,
    val resolution: String
)

class ArbRepository(context: Context) {
    private val prefs = context.getSharedPreferences("arbmaster_pro_v1", Context.MODE_PRIVATE)

    fun getScannerBaseUrl(): String =
        prefs.getString("scanner_url", BuildConfig.DEFAULT_SCANNER_BASE_URL)
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()
            .ifEmpty { BuildConfig.DEFAULT_SCANNER_BASE_URL }

    fun saveScannerBaseUrl(url: String) {
        prefs.edit().putString("scanner_url", url.trim().trimEnd('/')).apply()
    }

    fun loadBets(): List<BetEntry> {
        val raw = prefs.getString("bets_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BetEntry(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    event = o.optString("event"),
                    selection = o.optString("selection"),
                    book = o.optString("book"),
                    odds = o.optString("odds"),
                    stake = o.optString("stake"),
                    payout = o.optString("payout"),
                    status = o.optString("status", "Pending"),
                    currency = o.optString("currency", "NGN")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveBets(bets: List<BetEntry>) {
        val arr = JSONArray()
        bets.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("event", b.event)
                    put("selection", b.selection)
                    put("book", b.book)
                    put("odds", b.odds)
                    put("stake", b.stake)
                    put("payout", b.payout)
                    put("status", b.status)
                    put("currency", b.currency)
                }
            )
        }
        prefs.edit().putString("bets_json", arr.toString()).apply()
    }

    fun loadCashMovements(): List<CashEntry> {
        val raw = prefs.getString("cash_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CashEntry(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    kind = o.optString("kind", "Deposit"),
                    amount = o.optString("amount", "0"),
                    currency = o.optString("currency", "NGN"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCashMovements(items: List<CashEntry>) {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("kind", c.kind)
                    put("amount", c.amount)
                    put("currency", c.currency)
                    put("timestamp", c.timestamp)
                }
            )
        }
        prefs.edit().putString("cash_json", arr.toString()).apply()
    }

    suspend fun scanUpcomingArbitrage(
        sport: String,
        market: String,
        query: String,
        bookmakers: String,
        engine: String = "web"
    ): LiveScanResult = withContext(Dispatchers.IO) {
        val baseUrl = getScannerBaseUrl()
        val endpoint = URL("$baseUrl/api/live-arb-search")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        val payload = JSONObject().apply {
            put("engine", engine)
            put("sport", sport)
            put("market", market)
            put("query", query)
            put("bookmakers", bookmakers)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        if (code !in 200..299) {
            throw IllegalStateException(json.optString("error", "Live scan failed (HTTP $code)."))
        }

        val opps = mutableListOf<LiveOpportunityItem>()
        val arr = json.optJSONArray("opportunities") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val legsArr = o.optJSONArray("legs") ?: JSONArray()
            val legs = (0 until legsArr.length()).map { j ->
                val l = legsArr.getJSONObject(j)
                LiveLeg(
                    name = l.optString("name"),
                    book = l.optString("book"),
                    odds = l.optString("odds")
                )
            }
            opps.add(
                LiveOpportunityItem(
                    id = o.optString("id"),
                    event = o.optString("event"),
                    sport = o.optString("sport"),
                    league = o.optString("league"),
                    market = o.optString("market"),
                    commenceTime = o.optString("commenceTime"),
                    impliedSumPct = o.optDouble("impliedSumPct", 100.0),
                    arbMarginPct = o.optDouble("arbMarginPct", 0.0),
                    isArbitrage = o.optBoolean("isArbitrage", false),
                    notes = o.optString("notes", ""),
                    legs = legs
                )
            )
        }

        val meta = json.optJSONObject("meta")
        LiveScanResult(
            opportunities = opps,
            notice = meta?.optString("notice", "") ?: "",
            scannedAt = json.optLong("scannedAt", System.currentTimeMillis())
        )
    }

    fun getStaticAuditFindings(): List<AuditItem> = listOf(
        AuditItem(
            id = "AUD-01",
            severity = "HIGH",
            category = "Mobile & Android",
            title = "No Native Android APK & Incomplete Manifest",
            location = "dist/manifest.webmanifest, dist/index.html",
            summary = "Original archive shipped static HTML/JS only with relative manifest paths and no Android packaging.",
            resolution = "Rebuilt as this native Kotlin + Jetpack Compose Android app with automated GitHub Actions APK build."
        ),
        AuditItem(
            id = "AUD-02",
            severity = "HIGH",
            category = "Live Feed",
            title = "Live Scanner Fell Back to Fictional Demo Games",
            location = "dist/provider.js:4-9, scripts/live-games.mjs",
            summary = "Without external OddsPapi slugs, Live Games showed 4 fictional demo matches.",
            resolution = "Connected Live Arb Search to active upcoming match discovery with server-side 24-decimal math verification."
        ),
        AuditItem(
            id = "AUD-03",
            severity = "MEDIUM",
            category = "Security",
            title = "Raw innerHTML String Assembly Across 687-Line Client",
            location = "dist/app.js:134, 315, 465",
            summary = "UI templates concatenated HTML strings manually, leaving brittle unescaped slots.",
            resolution = "Replaced entirely with declarative Jetpack Compose UI and React JSX."
        ),
        AuditItem(
            id = "AUD-04",
            severity = "MEDIUM",
            category = "Math",
            title = "Division-by-Zero Edge Case in Break-Even & ROI",
            location = "dist/math.js:111-113",
            summary = "Zero-lot legs or zero-cost vectors could trigger unhandled BigInt division errors.",
            resolution = "Guarded stake > 0, denom > 0, and cost > 0 in ArbMath.kt and arbMath.ts."
        ),
        AuditItem(
            id = "AUD-05",
            severity = "PASS",
            category = "Math",
            title = "Verified 24-Decimal Fixed-Point Precision",
            location = "dist/math.js:1-115",
            summary = "Eliminates floating-point drift using 10^24 BigInt scaling and minor-unit floor rounding.",
            resolution = "Preserved natively via java.math.BigInteger in ArbMath.kt."
        )
    )
}
