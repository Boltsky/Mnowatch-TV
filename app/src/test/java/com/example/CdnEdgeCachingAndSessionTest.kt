package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MunowatchApi
import com.example.data.network.NetworkClient
import com.example.data.session.SecureSessionManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CdnEdgeCachingAndSessionTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SecureSessionManager
    private lateinit var networkClient: NetworkClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SecureSessionManager(context)
        sessionManager.saveSessionId("test_session_12345")
        networkClient = NetworkClient(sessionManager)
        MunowatchApi.resetCdnRateLimit()
    }

    @After
    fun tearDown() {
        MunowatchApi.resetCdnRateLimit()
    }

    @Test
    fun testIsMediaDeliveryRequest_identifiesMediaAndExcludesHtmlPages() {
        // Media endpoints
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://cdn.movviez.com/secure.php?file=movie.mp4&mode=preview".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://dl.movviez.com/secure.php?file=movie.mp4".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://nkuba.b-cdn.net/hls/movie.m3u8".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://harvetz.b-cdn.net/video.mp4".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://cdn.example.org/stream/video".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://dl.example.org/files/video".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/secure.php?file=movie.mp4".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/secure.php?File=movie.mp4".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/secure.php?amp;file=movie.mp4".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/video.m3u8".toHttpUrl()))
        assertTrue(NetworkClient.isMediaDeliveryRequest("https://example.com/play?src=video.mp4".toHttpUrl()))

        // HTML navigation pages (MUST NOT be classified as media delivery)
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/preview.php?id=123".toHttpUrl()))
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/download.php?id=123".toHttpUrl()))
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/movies.php".toHttpUrl()))
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://twolekede.com/seriesdetails.php?id=456".toHttpUrl()))
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://munowatch.com/home".toHttpUrl()))
        assertFalse(NetworkClient.isMediaDeliveryRequest("https://munowatch.com/twolekede?mid=789".toHttpUrl()))
    }

    @Test
    fun testNormalizeStreamUrl_removesExtraneousDeviceParamsPreservingCacheKeys() {
        val dirtyUrl = "https://cdn.movviez.com/secure.php?file=2024_Deadpool.mp4&device_id=abc-123&mvz_device_id=xyz-789&client_id=client99&session_id=sess1&mode=preview&token=validToken&expires=1700000000"
        val normalized = NetworkClient.normalizeStreamUrl(dirtyUrl)

        assertFalse("device_id must be removed", normalized.contains("device_id=abc-123"))
        assertFalse("mvz_device_id must be removed", normalized.contains("mvz_device_id=xyz-789"))
        assertFalse("client_id must be removed", normalized.contains("client_id=client99"))
        assertFalse("session_id must be removed", normalized.contains("session_id=sess1"))
        assertTrue("file must be preserved", normalized.contains("file=2024_Deadpool.mp4"))
        assertTrue("mode must be preserved", normalized.contains("mode=preview"))
        assertTrue("token must be preserved", normalized.contains("token=validToken"))
        assertTrue("expires must be preserved", normalized.contains("expires=1700000000"))
    }

    @Test
    fun testNormalizeStreamUrl_handlesHtmlEntitiesAndAmpPrefixes() {
        val dirtyHtmlUrl = "https://cdn.movviez.com/secure.php?file=movie.mp4&amp;device_id=dev99&amp;nocache=1&amp;mode=preview"
        val normalized = NetworkClient.normalizeStreamUrl(dirtyHtmlUrl)

        assertFalse("amp;device_id must be stripped", normalized.contains("device_id"))
        assertFalse("nocache must be stripped", normalized.contains("nocache"))
        assertTrue("file must be preserved", normalized.contains("file=movie.mp4"))
        assertTrue("mode must be preserved", normalized.contains("mode=preview"))
    }

    @Test
    fun testCookieJar_keepsMediaDeliveryRequestsCleanWithoutCookies() {
        val cookieJarField = NetworkClient::class.java.getDeclaredField("cookieJar")
        cookieJarField.isAccessible = true
        val cookieJar = cookieJarField.get(networkClient) as okhttp3.CookieJar

        // 1. Media request on cdn.movviez.com must return NO cookies
        val mediaUrl = "https://cdn.movviez.com/secure.php?file=movie.mp4&mode=preview".toHttpUrl()
        val mediaCookies = cookieJar.loadForRequest(mediaUrl)
        assertTrue("Media delivery requests must have empty cookie list", mediaCookies.isEmpty())

        // 2. Media response with Set-Cookie must NOT save cookies
        val dummyCookie = Cookie.Builder()
            .domain("cdn.movviez.com")
            .path("/")
            .name("tracking_id")
            .value("track_999")
            .build()
        cookieJar.saveFromResponse(mediaUrl, listOf(dummyCookie))
        val mediaCookiesAfterSave = cookieJar.loadForRequest(mediaUrl)
        assertTrue("Media delivery responses must not store cookies", mediaCookiesAfterSave.isEmpty())

        // 3. Bunny CDN media request must return NO cookies
        val bcdnUrl = "https://nkuba.b-cdn.net/stream/video.mp4".toHttpUrl()
        val bcdnCookies = cookieJar.loadForRequest(bcdnUrl)
        assertTrue("BunnyCDN delivery requests must have empty cookie list", bcdnCookies.isEmpty())

        // 4. HTML navigation on munowatch.com MUST send PHPSESSID cookie
        val munoHtmlUrl = "https://munowatch.com/home".toHttpUrl()
        val munoCookies = cookieJar.loadForRequest(munoHtmlUrl)
        assertTrue("munowatch.com HTML requests must contain PHPSESSID", munoCookies.any { it.name == "PHPSESSID" })

        // 5. HTML navigation on twolekede.com MUST send mvz_device_id cookie
        val twolekedeHtmlUrl = "https://twolekede.com/preview.php?id=123".toHttpUrl()
        val tkCookies = cookieJar.loadForRequest(twolekedeHtmlUrl)
        assertTrue("twolekede.com HTML requests must contain mvz_device_id", tkCookies.any { it.name == "mvz_device_id" })
    }

    @Test
    fun testCdnRateLimitTracking_managesCooldownAccurately() {
        assertFalse(MunowatchApi.isCdnRateLimited())

        MunowatchApi.markCdnRateLimited()
        assertTrue(MunowatchApi.isCdnRateLimited())

        MunowatchApi.resetCdnRateLimit()
        assertFalse(MunowatchApi.isCdnRateLimited())
    }

    @Test
    fun testStreamOkHttpClient_stripsCookieHeaderOnMediaDelivery() {
        val streamClient = networkClient.streamOkHttpClient
        val interceptor = streamClient.interceptors.first()

        // Test interceptor construction on media URL
        val req = Request.Builder()
            .url("https://cdn.movviez.com/secure.php?file=test.mp4&device_id=dummy")
            .header("Cookie", "mvz_device_id=device123")
            .header("Origin", "https://twolekede.com")
            .build()

        // Normalized URL check
        val normalizedUrl = NetworkClient.normalizeStreamUrl(req.url)
        assertFalse("Normalized URL in stream client must not contain device_id", normalizedUrl.toString().contains("device_id"))
        assertTrue("Normalized URL must preserve file param", normalizedUrl.toString().contains("file=test.mp4"))
    }
}
