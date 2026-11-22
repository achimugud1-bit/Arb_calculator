package com.arbmaster.pro.math

import java.math.BigInteger

object ArbMath {
    private const val SCALE = 24
    val Q: BigInteger = BigInteger.TEN.pow(SCALE)
    val H: BigInteger = BigInteger.valueOf(100L).multiply(Q)

    fun mul(a: BigInteger, b: BigInteger): BigInteger = a.multiply(b).divide(Q)

    fun div(a: BigInteger, b: BigInteger): BigInteger {
        require(b != BigInteger.ZERO) { "Division by zero in fixed-point math." }
        return a.multiply(Q).divide(b)
    }

    private fun ceilDiv(a: BigInteger, b: BigInteger): BigInteger {
        require(b > BigInteger.ZERO) { "Invalid divisor." }
        if (a <= BigInteger.ZERO) return BigInteger.ZERO
        return a.add(b).subtract(BigInteger.ONE).divide(b)
    }

    fun parseFixed(raw: String, label: String = "Value"): BigInteger {
        val s = raw.trim()
        require(Regex("^\\d+(\\.\\d+)?$").matches(s)) { "$label must be a non-negative number." }
        val parts = s.split('.')
        val whole = BigInteger(parts[0])
        val frac = if (parts.size > 1) parts[1] else ""
        require(frac.length <= SCALE) { "$label has too many decimal places." }
        val fracVal = BigInteger(frac.padEnd(SCALE, '0'))
        return whole.multiply(Q).add(fracVal)
    }

    fun positive(raw: String, label: String = "Value"): BigInteger {
        val n = parseFixed(raw, label)
        require(n > BigInteger.ZERO) { "$label must be greater than zero." }
        return n
    }

    fun parseOdds(raw: String, label: String = "Odds"): BigInteger {
        val n = parseFixed(raw, label)
        require(n > Q) { "$label must be greater than 1.00." }
        require(n <= BigInteger.valueOf(100_000L).multiply(Q)) { "$label exceeds supported range." }
        return n
    }

    fun parseRate(raw: String, label: String = "Commission"): BigInteger {
        val s = raw.trim()
        if (s.isEmpty()) return BigInteger.ZERO
        val n = parseFixed(s, label)
        require(n < H) { "$label must be below 100%." }
        return n.divide(BigInteger.valueOf(100L))
    }

    fun formatFixed(x: BigInteger, decimals: Int = 2): String {
        val neg = x < BigInteger.ZERO
        val absVal = x.abs()
        val scale = BigInteger.TEN.pow(SCALE - decimals)
        val rounded = absVal.add(scale.divide(BigInteger.TWO)).divide(scale)
        val raw = rounded.toString().padStart(decimals + 1, '0')
        val out = if (decimals > 0) {
            raw.dropLast(decimals) + "." + raw.takeLast(decimals)
        } else {
            raw
        }
        return (if (neg && rounded > BigInteger.ZERO) "-" else "") + out
    }

    fun formatMoney(x: BigInteger, currency: String = "NGN"): String {
        val symbol = when (currency) {
            "NGN" -> "₦"
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            else -> "$currency "
        }
        val formatted = formatFixed(x, 2)
        val neg = formatted.startsWith("-")
        val body = formatted.removePrefix("-")
        val parts = body.split(".")
        val wholeWithCommas = parts[0].reversed().chunked(3).joinToString(",").reversed()
        val decimals = if (parts.size > 1) ".${parts[1]}" else ""
        return (if (neg) "-" else "") + symbol + wholeWithCommas + decimals
    }

    data class LegInput(
        val odds: String,
        val name: String = "",
        val book: String = "",
        val commission: String = "",
        val fee: String = "",
        val fixed: String = ""
    )

    data class LegResult(
        val stake: BigInteger,
        val gross: BigInteger,
        val receipt: BigInteger,
        val profit: BigInteger,
        val probPct: BigInteger,
        val breakEven: BigInteger?
    )

    data class ArbResult(
        val legs: List<LegResult>,
        val cost: BigInteger,
        val worstProfit: BigInteger,
        val bestProfit: BigInteger,
        val roiPct: BigInteger,
        val combinedImpliedPct: BigInteger,
        val gapPct: BigInteger
    )

    fun arbitrate(
        budgetStr: String,
        rows: List<LegInput>,
        incrementStr: String = "1",
        minorUnitStr: String = "0.01"
    ): ArbResult {
        require(rows.size >= 2) { "Enter at least two outcomes." }
        val step = positive(incrementStr, "Stake increment")
        val unit = positive(minorUnitStr, "Currency unit")
        require(step.mod(Q) == BigInteger.ZERO) { "Use a whole-number stake increment." }

        val fixedStrs = rows.map { it.fixed.trim() }
        val hasFixed = fixedStrs.any { it.isNotEmpty() }
        val automatic = budgetStr.trim().isEmpty() && hasFixed

        data class ParsedLeg(
            val o: BigInteger,
            val c: BigInteger,
            val fee: BigInteger,
            val fixedVal: BigInteger?,
            val effective: BigInteger
        )

        val parsed = rows.mapIndexed { idx, row ->
            val label = "Outcome ${idx + 1}"
            val o = parseOdds(row.odds, "$label odds")
            val c = parseRate(row.commission, "$label commission")
            val fee = if (row.fee.trim().isNotEmpty()) parseFixed(row.fee, "$label fee") else BigInteger.ZERO
            val fixedVal = if (fixedStrs[idx].isNotEmpty()) positive(fixedStrs[idx], "$label locked stake") else null
            if (fixedVal != null) {
                require(fixedVal.mod(step) == BigInteger.ZERO) { "$label locked stake must match increment." }
            }
            val effective = Q.add(mul(o.subtract(Q), Q.subtract(c)))
            ParsedLeg(o, c, fee, fixedVal, effective)
        }

        val fees = parsed.fold(BigInteger.ZERO) { acc, l -> acc.add(l.fee) }
        val budget = if (automatic) {
            val lockedTarget = parsed.filter { it.fixedVal != null }
                .maxOf { mul(it.fixedVal!!, it.effective) }
            val needed = parsed.fold(BigInteger.ZERO) { acc, l ->
                val legStake = l.fixedVal ?: ceilDiv(lockedTarget.multiply(Q), l.effective.multiply(step)).multiply(step)
                acc.add(legStake)
            }
            needed.add(fees)
        } else {
            positive(budgetStr, "Budget")
        }

        require(budget > fees) { "Budget must exceed fixed fees." }
        val cap = budget.subtract(fees).divide(step)

        data class BoundedLeg(val leg: ParsedLeg, val lo: BigInteger, val hi: BigInteger)

        val legs = parsed.mapIndexed { idx, l ->
            val lo = if (l.fixedVal != null) l.fixedVal.divide(step) else BigInteger.ZERO
            val hi = if (l.fixedVal != null) l.fixedVal.divide(step) else cap
            require(lo <= hi) { "Outcome ${idx + 1} exceeds available budget." }
            BoundedLeg(l, lo, hi)
        }
        require(legs.fold(BigInteger.ZERO) { acc, l -> acc.add(l.lo) } <= cap) {
            "Locked stakes exceed available budget."
        }

        fun payout(bl: BoundedLeg, k: BigInteger): BigInteger =
            mul(k.multiply(step), bl.leg.effective).divide(unit).multiply(unit)

        fun targetVector(target: BigInteger): List<BigInteger> = legs.map { bl ->
            val needed = ceilDiv(target.multiply(Q), bl.leg.effective.multiply(step))
            val k = if (bl.lo > needed) bl.lo else needed
            if (payout(bl, k) < target) k.add(BigInteger.ONE) else k
        }

        fun possible(v: List<BigInteger>): Boolean {
            val sum = v.fold(BigInteger.ZERO) { acc, k -> acc.add(k) }
            return sum <= cap && v.indices.all { i -> v[i] <= legs[i].hi }
        }

        val priceSum = legs.fold(BigInteger.ZERO) { acc, l -> acc.add(div(Q, l.leg.o)) }
        val effSum = legs.fold(BigInteger.ZERO) { acc, l -> acc.add(div(Q, l.leg.effective)) }

        var bottom = BigInteger.ZERO
        var top = legs.minOf { payout(it, it.hi) }.divide(unit)
        while (bottom < top) {
            val mid = bottom.add(top).add(BigInteger.ONE).divide(BigInteger.TWO)
            if (possible(targetVector(mid.multiply(unit)))) {
                bottom = mid
            } else {
                top = mid.subtract(BigInteger.ONE)
            }
        }

        val peak = bottom.multiply(unit)
        var bestVec: List<BigInteger>? = null
        var bestNet: List<BigInteger> = emptyList()
        var bestWorst = BigInteger.ZERO
        var bestSpread = BigInteger.ZERO
        var bestCost = BigInteger.ZERO

        fun consider(v: List<BigInteger>) {
            if (!possible(v)) return
            val cost = v.fold(BigInteger.ZERO) { acc, k -> acc.add(k) }.multiply(step).add(fees)
            if (cost <= BigInteger.ZERO) return
            val net = legs.mapIndexed { i, bl -> payout(bl, v[i]).subtract(cost) }
            val worst = net.min()
            val spread = net.max().subtract(worst)
            val better = bestVec == null ||
                worst > bestWorst ||
                (worst == bestWorst && (spread < bestSpread || (spread == bestSpread && cost > bestCost)))
            if (better) {
                bestVec = v
                bestNet = net
                bestWorst = worst
                bestSpread = spread
                bestCost = cost
            }
        }

        val anchors = if (effSum >= Q || hasFixed) {
            listOf(peak)
        } else {
            listOf(BigInteger.ZERO, peak) + legs.map { payout(it, it.lo) }
        }

        for (anchor in anchors) {
            for (delta in -5..5) {
                val t = anchor.add(BigInteger.valueOf(delta.toLong()).multiply(step))
                if (t >= BigInteger.ZERO && t <= peak && ((effSum < Q && !hasFixed) || t == peak)) {
                    consider(targetVector(t))
                }
            }
            if (anchor <= peak) consider(targetVector(anchor))
        }

        if (!hasFixed && effSum < Q && cap <= BigInteger.valueOf(160L)) {
            for (bl in legs) {
                var k = bl.lo
                val limit = if (bl.hi < cap) bl.hi else cap
                while (k <= limit) {
                    val p = payout(bl, k)
                    if (p <= peak) consider(targetVector(p))
                    k = k.add(BigInteger.ONE)
                }
            }
        }

        val chosen = bestVec ?: throw IllegalStateException("No valid stake allocation found.")
        val out = legs.mapIndexed { i, bl ->
            val stake = chosen[i].multiply(step)
            val gross = mul(stake, bl.leg.o).divide(unit).multiply(unit)
            val receipt = payout(bl, chosen[i])
            val denom = mul(stake, Q.subtract(bl.leg.c))
            val breakEven = if (stake > BigInteger.ZERO && denom > BigInteger.ZERO) {
                Q.add(div(bestCost.subtract(stake), denom))
            } else {
                null
            }
            LegResult(
                stake = stake,
                gross = gross,
                receipt = receipt,
                profit = bestNet[i],
                probPct = div(H, bl.leg.o),
                breakEven = breakEven
            )
        }

        val roi = if (bestCost > BigInteger.ZERO) div(bestWorst, bestCost).multiply(BigInteger.valueOf(100L)) else BigInteger.ZERO
        return ArbResult(
            legs = out,
            cost = bestCost,
            worstProfit = bestWorst,
            bestProfit = bestNet.max(),
            roiPct = roi,
            combinedImpliedPct = priceSum.multiply(BigInteger.valueOf(100L)),
            gapPct = Q.subtract(priceSum).multiply(BigInteger.valueOf(100L))
        )
    }
}
