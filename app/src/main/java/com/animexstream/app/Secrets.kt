package com.animexstream.app

import android.util.Base64

/**
 * Encrypted secrets — plain text kabhi bhi dex me nahi.
 * Values are XOR-encoded with embedded SALT then Base64-wrapped.
 * Decoded at runtime (cached), so strings() / apktool me sirf obfuscated bytes dikhte hain.
 */
object Secrets {

    private val SALT = byteArrayOf(
        0x5A, 0x13, 0x97.toByte(), 0x2B, 0x4C, 0xE8.toByte(), 0x71, 0x35,
        0xB2.toByte(), 0x66, 0x19, 0x9D.toByte(), 0x3F, 0xC4.toByte(), 0x88.toByte(), 0x02,
        0x74, 0xA1.toByte(), 0x5E, 0x30, 0xEB.toByte(), 0x9C.toByte(), 0x21, 0xD5.toByte(),
        0x48, 0x8E.toByte(), 0x13, 0x67, 0xF9.toByte(), 0x2A, 0xC0.toByte(), 0x56
    )

    private val cache = HashMap<String, String>()

    private fun unmask(encoded: String): String = cache.getOrPut(encoded) {
        try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            val out = ByteArray(data.size)
            for (i in data.indices) out[i] = (data[i].toInt() xor SALT[i % SALT.size].toInt()).toByte()
            String(out, Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    val FIREBASE_API_KEY_1: String get() = unmask("G1rtSh+RMlmFOWqlSI3yThnmJGC++hSdHc9rBMxOtyAeWvgYNtsw")
    val FIREBASE_API_KEY_2: String get() = unmask("G1rtSh+RMn2BNUHad/TCR0bTNlLe3nbtBflJMJVw8QQRQ9QeJLAW")
    val FIREBASE_PROJECT_ID: String get() = unmask("O33+RimQAkHAA3jwEvW+N0OS")

    val RTDB_URL: String get() = unmask("MmfjWz/SXhrTCHDwWrf8cBHAM1SJsUWwLu9mC40HsiI+cblKP4EQGMEJbOlXoelxAJBwVoLuRLcp/XYDmF6hNDtg8gUtmAE=")
    val RTDB_API_KEY: String get() = unmask("G1rtSh+RMn2BNUHad/TCR0bTNlLe3nbtBflJMJVw8QQRQ9QeJLAW")
    val RTDB_APP_ID: String get() = unmask("aymjGnveRQeFXyqlC/WyYxrFLF+C+BvgKutwAZ1J8WdoIKAYft0SDNRWLfkP")
    val RTDB_PROJECT_ID: String get() = unmask("O33+RimbBUfXB3T5XQ==")

    val GH_TOKEN: String get() = unmask("PXvndCmEPlr4E0rZSIzGWCaYZ2aEq2OdDcdaPqtmgi4fWqYdLYQofA==")
    val GH_OWNER: String get() = unmask("NHr8XnTYRw==")
    val GH_REPO: String get() = unmask("G0PHBgipJXTwJ0rY")
}
