package com.example.data.network

import com.example.data.session.SecureSessionManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object BandwidthBypassProxyManager {
    // Disabled: Direct connection is strictly enforced to eliminate proxy latency and dead socket hangs
    private val isBypassActive = java.util.concurrent.atomic.AtomicBoolean(false)

    var onBypassActivated: (() -> Unit)? = null

    fun isBypassActive(): Boolean = false

    fun activateBypass() {
        // No-op: Public proxies are permanently decommissioned for video streaming reliability
    }

    fun deactivateBypass() {
        isBypassActive.set(false)
    }

    fun getCurrentProxy(): java.net.Proxy? = null

    fun rotateProxy(): java.net.Proxy? = null

    fun markFailed(sa: java.net.SocketAddress) {}

    val proxySelector = object : java.net.ProxySelector() {
        override fun select(uri: java.net.URI): List<java.net.Proxy> {
            return listOf(java.net.Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: java.net.URI, sa: java.net.SocketAddress, ioe: java.io.IOException) {}
    }
}

open class NetworkClient(
    private val sessionManager: SecureSessionManager,
    customOkHttpClient: OkHttpClient? = null
) {

    // Fallback DNS that resolves system DNS first and falls back to verified CDN/origin Anycast IPs
    private val resilientDns = object : Dns {
        private val fallbackIps = mapOf(
            "myvj.net" to listOf(InetAddress.getByAddress("myvj.net", byteArrayOf(103.toByte(), 13.toByte(), 211.toByte(), 126.toByte()))),
            "ugawatch.com" to listOf(InetAddress.getByAddress("ugawatch.com", byteArrayOf(103.toByte(), 13.toByte(), 211.toByte(), 126.toByte()))),
            "munowatch.com" to listOf(InetAddress.getByAddress("munowatch.com", byteArrayOf(98.toByte(), 88.toByte(), 20.toByte(), 40.toByte()))),
            "twolekede.com" to listOf(
                InetAddress.getByAddress("twolekede.com", byteArrayOf(104.toByte(), 21.toByte(), 28.toByte(), 132.toByte())),
                InetAddress.getByAddress("twolekede.com", byteArrayOf(172.toByte(), 67.toByte(), 146.toByte(), 52.toByte()))
            ),
            "cdn.movviez.com" to listOf(
                InetAddress.getByAddress("cdn.movviez.com", byteArrayOf(104.toByte(), 21.toByte(), 53.toByte(), 166.toByte())),
                InetAddress.getByAddress("cdn.movviez.com", byteArrayOf(172.toByte(), 67.toByte(), 215.toByte(), 169.toByte()))
            ),
            "dl.movviez.com" to listOf(
                InetAddress.getByAddress("dl.movviez.com", byteArrayOf(104.toByte(), 21.toByte(), 53.toByte(), 166.toByte())),
                InetAddress.getByAddress("dl.movviez.com", byteArrayOf(172.toByte(), 67.toByte(), 215.toByte(), 169.toByte()))
            ),
            "nkuba.b-cdn.net" to listOf(InetAddress.getByAddress("nkuba.b-cdn.net", byteArrayOf(143.toByte(), 244.toByte(), 56.toByte(), 49.toByte()))),
            "harvetz.b-cdn.net" to listOf(InetAddress.getByAddress("harvetz.b-cdn.net", byteArrayOf(143.toByte(), 244.toByte(), 56.toByte(), 54.toByte()))),
            "munotech2.b-cdn.net" to listOf(InetAddress.getByAddress("munotech2.b-cdn.net", byteArrayOf(143.toByte(), 244.toByte(), 56.toByte(), 53.toByte()))),
            "munotech3.b-cdn.net" to listOf(InetAddress.getByAddress("munotech3.b-cdn.net", byteArrayOf(79.toByte(), 127.toByte(), 134.toByte(), 225.toByte())))
        )

        override fun lookup(hostname: String): List<InetAddress> {
            val targetHost = hostname.lowercase().trim()
            // Try standard system DNS first to let Anycast route to closest regional edge node
            try {
                val systemIps = Dns.SYSTEM.lookup(targetHost)
                val ipv4List = systemIps.filterIsInstance<java.net.Inet4Address>()
                if (ipv4List.isNotEmpty()) {
                    return ipv4List
                }
                if (systemIps.isNotEmpty()) {
                    return systemIps
                }
            } catch (_: Throwable) {}

            // Fallback to verified Anycast/origin IPs only if local system DNS fails
            val known = fallbackIps[targetHost]
            if (known != null && known.isNotEmpty()) {
                return known
            }
            return resolveFallback(targetHost)
        }

        private fun resolveFallback(hostname: String): List<InetAddress> {
            return when {
                hostname.contains("b-cdn.net") -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(143.toByte(), 244.toByte(), 56.toByte(), 49.toByte()))
                )
                hostname.contains("movviez.com") -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 21.toByte(), 53.toByte(), 166.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(172.toByte(), 67.toByte(), 215.toByte(), 169.toByte()))
                )
                hostname.contains("twolekede.com") -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 21.toByte(), 28.toByte(), 132.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(172.toByte(), 67.toByte(), 146.toByte(), 52.toByte()))
                )
                hostname.contains("myvj.net") || hostname.contains("ugawatch.com") -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(103.toByte(), 13.toByte(), 211.toByte(), 126.toByte()))
                )
                hostname.contains("munowatch.com") -> listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(98.toByte(), 88.toByte(), 20.toByte(), 40.toByte()))
                )
                else -> {
                    try {
                        val all = InetAddress.getAllByName(hostname).toList()
                        all.filterIsInstance<java.net.Inet4Address>().ifEmpty { all }
                    } catch (_: Exception) {
                        listOf(InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 21.toByte(), 28.toByte(), 132.toByte())))
                    }
                }
            }
        }
    }

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Media delivery requests should NEVER save or associate cookies
            if (isMediaDeliveryRequest(url)) {
                return
            }
            synchronized(globalCookieStore) {
                val list = globalCookieStore.getOrPut(url.host) { mutableListOf() }
                for (cookie in cookies) {
                    list.removeAll { it.name == cookie.name }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Media delivery requests (cdn.movviez.com, dl.movviez.com, .mp4, .m3u8, or ?file=)
            // MUST remain completely clean without session or device cookies to maximize edge-caching
            // and eliminate per-device/per-session rate-limiting headers.
            if (isMediaDeliveryRequest(url)) {
                return emptyList()
            }
            val cookies = synchronized(globalCookieStore) {
                globalCookieStore[url.host]?.toMutableList() ?: mutableListOf()
            }
            // Ensure stored session cookie is attached solely for HTML page navigation on munowatch.com
            if (url.host.contains("munowatch.com")) {
                val storedSessionId = sessionManager.getSessionId()?.takeIf { it.isNotBlank() }
                    ?: com.example.data.session.SecureSessionManager.DEFAULT_SESSION_ID
                if (!storedSessionId.isNullOrBlank()) {
                    cookies.removeAll { it.name == "PHPSESSID" || it.name == "_ga" || it.name.startsWith("_ga_") }
                    cookies.add(
                        Cookie.Builder()
                            .domain(url.host)
                            .path("/")
                            .name("PHPSESSID")
                            .value(storedSessionId)
                            .build()
                    )
                    cookies.add(
                        Cookie.Builder()
                            .domain(url.host)
                            .path("/")
                            .name("_ga")
                            .value(com.example.data.session.SecureSessionManager.DEFAULT_GA)
                            .build()
                    )
                    cookies.add(
                        Cookie.Builder()
                            .domain(url.host)
                            .path("/")
                            .name("_ga_1TPS2Y78PS")
                            .value(com.example.data.session.SecureSessionManager.DEFAULT_GA_SESSION)
                            .build()
                    )
                }
            }
            // Ensure stable persistent device ID is attached solely for HTML page navigation on twolekede / movviez
            if (url.host.contains("twolekede.com") || url.host.contains("movviez.com")) {
                val devId = sessionManager.getDeviceId()
                cookies.removeAll { it.name == "mvz_device_id" }
                val devCookie = Cookie.Builder()
                    .domain(url.host)
                    .path("/")
                    .name("mvz_device_id")
                    .value(devId)
                    .build()
                cookies.add(devCookie)
            }
            return cookies
        }
    }

    open val okHttpClient: OkHttpClient = customOkHttpClient ?: OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .dns(resilientDns)
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val reqUrl = original.url
            val host = reqUrl.host
            val isTwolekede = host.contains("twolekede.com") || host.contains("movviez.com")
            val referer = if (isTwolekede) {
                "https://twolekede.com/"
            } else {
                "https://munowatch.com/"
            }
            val requestBuilder = original.newBuilder()
                .url(reqUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Referer", referer)

            chain.proceed(requestBuilder.build())
        }
        .build()

    // Dedicated client for ExoPlayer streaming that follows redirects
    val streamOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) // HTTP/1.1 prevents CDN HTTP/2 multiplex stream idle timeouts
        .proxy(java.net.Proxy.NO_PROXY)
        .dns(resilientDns)
        .cookieJar(cookieJar)
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val reqUrl = normalizeStreamUrl(original.url)
            val host = reqUrl.host
            val isTwolekede = host.contains("twolekede.com") || host.contains("movviez.com")
            val existingReferer = original.header("Referer")
            val referer = if (isTwolekede) {
                "https://twolekede.com/"
            } else if (!existingReferer.isNullOrBlank()) {
                existingReferer
            } else {
                "https://munowatch.com/"
            }
            val requestBuilder = original.newBuilder()
                .url(reqUrl)
                .removeHeader("Origin") // Critical: CDN drops requests when Origin header is present
                .removeHeader("X-Title")
                .removeHeader("Sec-Fetch-Dest")
                .removeHeader("Sec-Fetch-Mode")
                .removeHeader("Sec-Fetch-Site")
                .removeHeader("Sec-Fetch-User")
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "*/*")

            if (isTwolekede) {
                val (_, devId) = getClientIdentity()
                requestBuilder.header("Cookie", "mvz_device_id=$devId")
            } else if (isMediaDeliveryRequest(reqUrl)) {
                requestBuilder.removeHeader("Cookie")
            }

            // Critical fix for MP4 streaming with trailing moov atom / chunked CDN:
            // When ExoPlayer requests the start of the file without an explicit Range header,
            // servers like cdn.movviez.com / dl.movviez.com respond with HTTP 200 OK and
            // Transfer-Encoding: chunked without Content-Length.
            // This forces ExoPlayer to attempt sequential download of the entire file to find
            // the trailing moov atom, causing an endless loading spinner.
            // By enforcing "Range: bytes=0-", the server is forced to respond with
            // HTTP 206 Partial Content and Content-Range: bytes 0-.../TOTAL_SIZE, allowing
            // ExoPlayer to immediately seek to the moov atom at the end of the file in <1s.
            if (original.header("Range") == null) {
                requestBuilder.header("Range", "bytes=0-")
            }

            val finalReq = requestBuilder.build()
            val rangeHeader = finalReq.header("Range") ?: "none"
            android.util.Log.d("StreamHttp", "--> ${finalReq.method} ${finalReq.url} (Range: $rangeHeader)")
            onStreamRequestStarted()
            val start = System.currentTimeMillis()
            var handedOffToSource = false
            try {
                val response = chain.proceed(finalReq)
                onStreamActivity()
                val duration = System.currentTimeMillis() - start
                val len = response.header("Content-Length") ?: "unknown"
                val ct = response.header("Content-Type") ?: "unknown"
                val cr = response.header("Content-Range") ?: "none"
                android.util.Log.d("StreamHttp", "<-- ${response.code} ${response.message} (${duration}ms, len=$len, type=$ct, range=$cr)")

                val originalBody = response.body
                if (originalBody != null) {
                    val wrappedSource = object : ForwardingSource(originalBody.source()) {
                        private var isClosed = false

                        override fun read(sink: Buffer, byteCount: Long): Long {
                            val bytesRead = super.read(sink, byteCount)
                            if (bytesRead > 0) {
                                onStreamBytesRead(bytesRead)
                            }
                            return bytesRead
                        }

                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                if (!isClosed) {
                                    isClosed = true
                                    onStreamRequestEnded()
                                }
                            }
                        }
                    }
                    val wrappedBody = object : ResponseBody() {
                        override fun contentType(): MediaType? = originalBody.contentType()
                        override fun contentLength(): Long = originalBody.contentLength()
                        override fun source(): BufferedSource = wrappedSource.buffer()
                    }
                    handedOffToSource = true
                    response.newBuilder().body(wrappedBody).build()
                } else {
                    response
                }
            } catch (e: Exception) {
                onStreamActivity()
                val duration = System.currentTimeMillis() - start
                android.util.Log.e("StreamHttp", "<-- FAILED after ${duration}ms: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            } finally {
                if (!handedOffToSource) {
                    onStreamRequestEnded()
                }
            }
        }
        .build()

    init {
        BandwidthBypassProxyManager.onBypassActivated = {
            try {
                streamOkHttpClient.connectionPool.evictAll()
                okHttpClient.connectionPool.evictAll()
            } catch (_: Throwable) {}
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        private val ugandaIpPrefixes = arrayOf(
            intArrayOf(41, 210),   // MTN Uganda
            intArrayOf(197, 239),  // MTN Uganda
            intArrayOf(196, 43),   // MTN Uganda
            intArrayOf(102, 134),  // Airtel Uganda
            intArrayOf(196, 201),  // Airtel Uganda
            intArrayOf(105, 234),  // Liquid Telecom
            intArrayOf(154, 72),   // Liquid Telecom
            intArrayOf(197, 157),  // Roke Telkom
            intArrayOf(195, 171)   // Africell / Uganda Telecom
        )

        private val globalCookieStore = mutableMapOf<String, MutableList<Cookie>>()

        fun isMediaDeliveryRequest(url: HttpUrl): Boolean {
            val host = url.host.lowercase()
            val path = url.encodedPath.lowercase()
            val query = url.encodedQuery?.lowercase()
            return host.contains("cdn.movviez.com") ||
                    host.contains("dl.movviez.com") ||
                    host.contains("b-cdn.net") ||
                    host.startsWith("cdn.") ||
                    host.startsWith("dl.") ||
                    path.endsWith(".mp4") ||
                    path.endsWith(".m3u8") ||
                    path.contains(".mp4") ||
                    path.contains(".m3u8") ||
                    query?.contains(".mp4") == true ||
                    query?.contains(".m3u8") == true ||
                    url.queryParameterNames.any { it.removePrefix("amp;").equals("file", ignoreCase = true) }
        }

        fun normalizeStreamUrl(url: HttpUrl): HttpUrl {
            if (!isMediaDeliveryRequest(url)) return url

            val extraneousParamNames = setOf(
                "device_id", "deviceid", "mvz_device_id", "dev_id", "devid", "device",
                "client_id", "clientid", "client_ip", "clientip",
                "session_id", "sessionid", "phpsessid", "user_id", "userid", "uid", "uuid",
                "app_version", "appversion", "_", "rnd", "nocache", "cachebust"
            )

            val queryParamNames = url.queryParameterNames
            val hasExtraneous = queryParamNames.any { it.removePrefix("amp;").lowercase() in extraneousParamNames }
            if (!hasExtraneous) return url

            val builder = url.newBuilder()
            for (name in queryParamNames) {
                if (name.removePrefix("amp;").lowercase() in extraneousParamNames) {
                    builder.removeAllQueryParameters(name)
                    builder.removeAllEncodedQueryParameters(name)
                }
            }
            return builder.build()
        }

        fun normalizeStreamUrl(urlString: String): String {
            val clean = urlString.replace("&amp;", "&").replace("&#38;", "&").trim()
            val parsed = clean.toHttpUrlOrNull() ?: return clean
            return normalizeStreamUrl(parsed).toString()
        }

        fun clearTwolekedeCookies() {
            synchronized(globalCookieStore) {
                globalCookieStore.keys.removeAll { it.contains("twolekede") || it.contains("movviez") }
            }
        }

        fun generateSyntheticClientIp(): String {
            val prefix = ugandaIpPrefixes[java.util.concurrent.ThreadLocalRandom.current().nextInt(ugandaIpPrefixes.size)]
            val o3 = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 255)
            val o4 = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 255)
            return "${prefix[0]}.${prefix[1]}.$o3.$o4"
        }

        @Volatile
        private var currentSyntheticIp: String = generateSyntheticClientIp()
        @Volatile
        private var currentDeviceId: String = java.util.UUID.randomUUID().toString()

        fun rotateClientIdentity(): Pair<String, String> {
            currentDeviceId = java.util.UUID.randomUUID().toString()
            clearTwolekedeCookies()
            try {
                BandwidthBypassProxyManager.onBypassActivated?.invoke()
            } catch (_: Throwable) {}
            return Pair(currentSyntheticIp, currentDeviceId)
        }

        fun getClientIdentity(): Pair<String, String> = Pair(currentSyntheticIp, currentDeviceId)

        @Volatile
        var lastStreamActivityTimeMs: Long = System.currentTimeMillis()
            private set

        private val activeStreamRequests = AtomicInteger(0)
        private val totalStreamBytes = AtomicLong(0L)

        val activeRequests: Int get() = activeStreamRequests.get()
        val totalBytes: Long get() = totalStreamBytes.get()

        fun onStreamRequestStarted() {
            activeStreamRequests.incrementAndGet()
            lastStreamActivityTimeMs = System.currentTimeMillis()
        }

        fun onStreamRequestEnded() {
            activeStreamRequests.updateAndGet { if (it > 0) it - 1 else 0 }
            lastStreamActivityTimeMs = System.currentTimeMillis()
        }

        fun onStreamActivity() {
            lastStreamActivityTimeMs = System.currentTimeMillis()
        }

        fun onStreamBytesRead(bytes: Long) {
            lastStreamActivityTimeMs = System.currentTimeMillis()
            totalStreamBytes.addAndGet(bytes)
        }
    }
}
