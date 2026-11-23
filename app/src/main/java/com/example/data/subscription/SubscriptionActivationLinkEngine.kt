package com.example.data.subscription

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.net.URLDecoder
import java.net.URLEncoder

data class ActivationDeepLinkPayload(
    val planId: String,
    val targetUserId: String? = null,
    val referenceCode: String? = null,
    val actorId: String? = null,
    val rawUrl: String = ""
)

object SubscriptionActivationLinkEngine {

    const val SCHEME = "tmsvpn"
    const val HOST = "activate"

    fun buildActivationUrl(
        planId: String,
        targetUserId: String? = null,
        referenceCode: String? = null,
        actorId: String? = null
    ): String {
        val params = mutableListOf<String>()
        params.add("plan=${urlEncode(planId.trim())}")
        targetUserId?.trim()?.takeIf { it.isNotBlank() }?.let {
            params.add("uid=${urlEncode(it)}")
        }
        referenceCode?.trim()?.takeIf { it.isNotBlank() }?.let {
            params.add("ref=${urlEncode(it)}")
        }
        actorId?.trim()?.takeIf { it.isNotBlank() }?.let {
            params.add("actor=${urlEncode(it)}")
        }
        return "$SCHEME://$HOST?${params.joinToString("&")}"
    }

    fun parseActivationUrl(rawInput: String?): ActivationDeepLinkPayload? {
        val text = rawInput?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Extract first tmsvpn://activate... token if pasted inside a message
        val candidate = Regex("""tmsvpn://activate[^\s]+""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.value
            ?: text

        if (!candidate.startsWith("$SCHEME://$HOST", ignoreCase = true)) {
            return null
        }

        val query = candidate.substringAfter("?", "")
        if (query.isBlank()) return null

        val map = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val k = urlDecode(pair.substring(0, eqIdx)).lowercase()
            val v = urlDecode(pair.substring(eqIdx + 1))
            if (v.isNotBlank()) map[k] = v
        }

        val planId = map["plan"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return ActivationDeepLinkPayload(
            planId = planId,
            targetUserId = map["uid"]?.trim()?.takeIf { it.isNotBlank() },
            referenceCode = map["ref"]?.trim()?.takeIf { it.isNotBlank() },
            actorId = map["actor"]?.trim()?.takeIf { it.isNotBlank() },
            rawUrl = candidate
        )
    }

    fun parseActivationUri(uri: Uri?): ActivationDeepLinkPayload? {
        return parseActivationUrl(uri?.toString())
    }

    private fun urlEncode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}

/**
 * Pure-Kotlin ISO 18004 QR Code Matrix Generator (Byte mode, Error Correction Level L, Versions 1..10).
 * Requires zero external libraries and emits genuine scannable QR matrices.
 */
object QrMatrixEncoder {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val group1Blocks: Int,
        val group1DataCodewords: Int,
        val group2Blocks: Int = 0,
        val group2DataCodewords: Int = 0,
        val alignmentCenters: IntArray = intArrayOf()
    ) {
        val totalDataCodewords: Int
            get() = (group1Blocks * group1DataCodewords) + (group2Blocks * group2DataCodewords)
    }

    // ISO 18004 Level L specs for Versions 1..10 (supports up to 271 UTF-8 bytes)
    private val SPECS_L = listOf(
        VersionSpec(1, 26, 7, 1, 19, alignmentCenters = intArrayOf()),
        VersionSpec(2, 44, 10, 1, 34, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(3, 70, 15, 1, 55, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(4, 100, 20, 1, 80, alignmentCenters = intArrayOf(6, 26)),
        VersionSpec(5, 134, 26, 1, 108, alignmentCenters = intArrayOf(6, 30)),
        VersionSpec(6, 172, 18, 2, 68, alignmentCenters = intArrayOf(6, 34)),
        VersionSpec(7, 196, 20, 2, 78, alignmentCenters = intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 24, 2, 97, alignmentCenters = intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 30, 2, 116, alignmentCenters = intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 18, 2, 68, 2, 69, alignmentCenters = intArrayOf(6, 28, 50))
    )

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) {
                x = x xor 0x11D
            }
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun computeGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val factor = intArrayOf(1, gfExp[i])
            val next = IntArray(poly.size + 1)
            for (pIdx in poly.indices) {
                for (fIdx in factor.indices) {
                    next[pIdx + fIdx] = next[pIdx + fIdx] xor gfMul(poly[pIdx], factor[fIdx])
                }
            }
            poly = next
        }
        return poly
    }

    private fun computeEcBytes(data: IntArray, ecLen: Int): IntArray {
        val gen = computeGeneratorPoly(ecLen)
        val buffer = IntArray(data.size + ecLen)
        System.arraycopy(data, 0, buffer, 0, data.size)
        for (i in data.indices) {
            val coef = buffer[i]
            if (coef != 0) {
                for (j in gen.indices) {
                    buffer[i + j] = buffer[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        return buffer.copyOfRange(data.size, buffer.size)
    }

    fun encode(content: String): Array<BooleanArray> {
        val rawBytes = content.toByteArray(Charsets.UTF_8)
        // Byte mode header for V1..V9 takes 4 (mode) + 8 (len) = 12 bits -> need room for ceil((12 + 8*n)/8) codewords
        val neededBytes = rawBytes.size + 2
        val spec = SPECS_L.firstOrNull { it.totalDataCodewords >= neededBytes }
            ?: SPECS_L.last()

        val maxPayload = (spec.totalDataCodewords - 2).coerceAtLeast(0)
        val payload = if (rawBytes.size > maxPayload) rawBytes.copyOf(maxPayload) else rawBytes

        val bits = ArrayList<Int>(spec.totalDataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (b in count - 1 downTo 0) {
                bits.add((value ushr b) and 1)
            }
        }

        // Byte mode indicator (0100) + 8-bit character count (V1..V9)
        appendBits(0b0100, 4)
        appendBits(payload.size, 8)
        for (byte in payload) {
            appendBits(byte.toInt() and 0xFF, 8)
        }
        val maxDataBits = spec.totalDataCodewords * 8
        val terminatorBits = minOf(4, maxDataBits - bits.size)
        repeat(terminatorBits) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val dataCodewords = IntArray(spec.totalDataCodewords)
        var cwIdx = 0
        var i = 0
        while (i < bits.size && cwIdx < dataCodewords.size) {
            var byteVal = 0
            for (b in 0 until 8) {
                byteVal = (byteVal shl 1) or bits[i + b]
            }
            dataCodewords[cwIdx++] = byteVal
            i += 8
        }
        var padToggle = true
        while (cwIdx < dataCodewords.size) {
            dataCodewords[cwIdx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        // Split into RS blocks and interleave
        val dataBlocks = mutableListOf<IntArray>()
        val ecBlocks = mutableListOf<IntArray>()
        var offset = 0
        repeat(spec.group1Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.group1DataCodewords)
            offset += spec.group1DataCodewords
            dataBlocks.add(block)
            ecBlocks.add(computeEcBytes(block, spec.ecCodewordsPerBlock))
        }
        repeat(spec.group2Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.group2DataCodewords)
            offset += spec.group2DataCodewords
            dataBlocks.add(block)
            ecBlocks.add(computeEcBytes(block, spec.ecCodewordsPerBlock))
        }

        val stream = ArrayList<Int>(spec.totalCodewords * 8 + 8)
        val maxDataLen = dataBlocks.maxOf { it.size }
        for (col in 0 until maxDataLen) {
            for (blk in dataBlocks) {
                if (col < blk.size) {
                    val v = blk[col]
                    for (b in 7 downTo 0) stream.add((v ushr b) and 1)
                }
            }
        }
        for (col in 0 until spec.ecCodewordsPerBlock) {
            for (blk in ecBlocks) {
                if (col < blk.size) {
                    val v = blk[col]
                    for (b in 7 downTo 0) stream.add((v ushr b) and 1)
                }
            }
        }

        val size = 17 + spec.version * 4
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setFunctionModule(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                matrix[r][c] = dark
                reserved[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    val inSeparator = dr == -1 || dr == 7 || dc == -1 || dc == 7
                    val dark = if (inSeparator) {
                        false
                    } else {
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    }
                    setFunctionModule(r, c, dark)
                }
            }
        }

        fun drawAlignment(centerR: Int, centerC: Int) {
            for (dr in -2..2) {
                for (dc in -2..2) {
                    val dark = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                    setFunctionModule(centerR + dr, centerC + dc, dark)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (idx in 8 until size - 8) {
            val dark = (idx % 2 == 0)
            if (!reserved[6][idx]) setFunctionModule(6, idx, dark)
            if (!reserved[idx][6]) setFunctionModule(idx, 6, dark)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (rCenter in centers) {
            for (cCenter in centers) {
                if ((rCenter < 9 && cCenter < 9) ||
                    (rCenter < 9 && cCenter > size - 9) ||
                    (rCenter > size - 9 && cCenter < 9)
                ) continue
                drawAlignment(rCenter, cCenter)
            }
        }

        // Dark module
        setFunctionModule(size - 8, 8, true)

        // Reserve format info strips
        for (k in 0..8) {
            if (k != 6) {
                reserved[8][k] = true
                reserved[k][8] = true
            }
        }
        for (k in size - 8 until size) {
            reserved[8][k] = true
            reserved[k][8] = true
        }

        // Reserve version info for Version >= 7
        if (spec.version >= 7) {
            var vBits = spec.version shl 12
            var rem = vBits
            for (shift in 5 downTo 0) {
                if ((rem ushr (shift + 12)) and 1 != 0) {
                    rem = rem xor (0x1F25 shl shift)
                }
            }
            vBits = (spec.version shl 12) or (rem and 0xFFF)
            for (k in 0 until 18) {
                val dark = ((vBits ushr k) and 1) != 0
                val a = size - 11 + (k % 3)
                val b = k / 3
                setFunctionModule(b, a, dark)
                setFunctionModule(a, b, dark)
            }
        }

        // Zigzag data bit placement + Mask 0 ((r + c) % 2 == 0)
        var bitIdx = 0
        var col = size - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until size) {
                val r = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val c = col - dx
                    if (!reserved[r][c]) {
                        val rawBit = if (bitIdx < stream.size) stream[bitIdx] == 1 else false
                        bitIdx++
                        val masked = if ((r + c) % 2 == 0) !rawBit else rawBit
                        matrix[r][c] = masked
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        // Write Format Information (Level L = 01, Mask 0 = 000 -> data = 01000)
        val formatData = 0b01000
        var fRem = formatData shl 10
        for (shift in 4 downTo 0) {
            if ((fRem ushr (shift + 10)) and 1 != 0) {
                fRem = fRem xor (0x537 shl shift)
            }
        }
        val formatBits = ((formatData shl 10) or (fRem and 0x3FF)) xor 0x5412

        // Places format bits from LSB=0 to MSB=14 per ISO 18004
        for (k in 0..14) {
            val dark = ((formatBits ushr k) and 1) != 0
            // Strip 1
            when {
                k < 6 -> matrix[8][k] = dark
                k == 6 -> matrix[8][7] = dark
                k == 7 -> matrix[8][8] = dark
                k == 8 -> matrix[7][8] = dark
                else -> matrix[14 - k][8] = dark
            }
            // Strip 2
            if (k < 8) {
                matrix[size - 1 - k][8] = dark
            } else {
                matrix[8][size - 15 + k] = dark
            }
        }

        return matrix
    }
}

@Composable
fun ActivationQrCodeBox(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp
) {
    val matrix = remember(payload) { QrMatrixEncoder.encode(payload) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp)
            .semantics {
                contentDescription = "کد QR فعال‌سازی اشتراک"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val moduleCount = matrix.size.coerceAtLeast(1)
            val cellSize = this.size.minDimension / moduleCount.toFloat()
            val qrColor = Color(0xFF0A0F1E)
            for (r in 0 until moduleCount) {
                for (c in 0 until moduleCount) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = qrColor,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize + 0.5f, cellSize + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
