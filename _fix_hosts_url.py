import io, re
p = r'app\src\main\java\com\animexstream\app\data\MovieBox.kt'
s = io.open(p, encoding='utf-8').read()

# 1) Expand HOSTS to all working mirrors
old_hosts = '''    private val HOSTS = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api.inmoviebox.com",
    )'''
new_hosts = '''    private val HOSTS = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api3.aoneroom.com",
        "https://api.inmoviebox.com",
    )'''
if old_hosts in s:
    s = s.replace(old_hosts, new_hosts)
    print('HOSTS: OK')
else:
    print('HOSTS: MISS')

# 2) Add import for HttpUrl
if 'import okhttp3.HttpUrl' not in s:
    s = s.replace('import okhttp3.MediaType.Companion.toMediaType',
                  'import okhttp3.HttpUrl.Companion.toHttpUrlOrNull\nimport okhttp3.MediaType.Companion.toMediaType', 1)
    print('import HttpUrl: OK')
else:
    print('import HttpUrl: already')

# 3) Replace rawRequest with version that tries ALL hosts + signs on OkHttp's exact URL
old = s[s.index('    // ---------- transport ----------'):s.index('    private fun ensureToken():')]
new = '''    // ---------- transport ----------
    private fun rawRequest(method: String, path: String, body: String?, auth: String?): JSONObject? {
        val start = activeHost
        var lastMsg: String? = null
        for (i in HOSTS.indices) {
            val idx = (start + i) % HOSTS.size
            val base = HOSTS[idx]
            val rawUrl = base + path
            val httpUrl = rawUrl.toHttpUrlOrNull()
            if (httpUrl == null) {
                lastMsg = "bad url: $rawUrl"
                continue
            }
            // Sign the EXACT string OkHttp will send (handles normalization).
            val signedUrl = httpUrl.toString()
            val ts = System.currentTimeMillis()
            val request = Request.Builder()
                .url(httpUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Connection", "keep-alive")
                .header("x-client-token", xClientToken(ts))
                .header("x-tr-signature", sign(method, signedUrl, body, ts))
                .header("x-client-info", clientInfo)
                .header("x-client-status", "0")
                .header("x-forwarded-for", spoofIp())
                .apply { if (auth != null) header("Authorization", "Bearer $auth") }
                .apply {
                    if (method == "POST") post((body ?: "{}").toRequestBody(jsonType)) else get()
                }
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastMsg = "HTTP ${resp.code} @ ${base.removePrefix("https://")} ${path.take(50)}"
                        return@use
                    }
                    val text = resp.body?.string()
                    if (text.isNullOrBlank()) {
                        lastMsg = "empty body @ ${base.removePrefix("https://")}"
                        return@use
                    }
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (json == null) {
                        lastMsg = "bad JSON @ ${base.removePrefix("https://")}"
                        return@use
                    }
                    if (json.has("code")) {
                        val code = json.optInt("code", -1)
                        if (code != 0 && code != 200) {
                            lastMsg = "code=$code ${json.optString("message").take(40)}"
                            return@use
                        }
                    }
                    activeHost = idx
                    lastError = null
                    return json
                }
            } catch (e: Exception) {
                lastMsg = "${e.javaClass.simpleName} @ ${base.removePrefix("https://")}: ${e.message?.take(50)}"
            }
        }
        activeHost = 0
        lastError = lastMsg ?: "all hosts failed"
        return null
    }

'''
s = s.replace(old, new)
print('rawRequest: replaced')

io.open(p, 'w', encoding='utf-8').write(s)
print('SAVED')
