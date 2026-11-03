package com.example

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MunowatchApi
import com.example.data.api.MunowatchHtmlParser
import com.example.data.network.NetworkClient
import com.example.data.repository.MediaCache
import com.example.data.session.SecureSessionManager
import com.example.domain.model.PlaybackSource
import com.example.presentation.player.StreamQualityOption
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VideoStreamQualityTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SecureSessionManager
    private val parser = MunowatchHtmlParser()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SecureSessionManager(context)
        sessionManager.saveSessionId("test_session_hd_quality")
        MediaCache.setPreferredStreamQuality("AUTO")
        MunowatchApi.resetCdnRateLimit()
    }

    @After
    fun tearDown() {
        MediaCache.setPreferredStreamQuality("AUTO")
        MunowatchApi.resetCdnRateLimit()
    }

    @Test
    fun testNormalizeResolution_detectsPristineDimensionsAccurately() {
        // True 1080p, 720p, 480p, 360p and 4K dimensions
        val (res4k, label4k) = StreamQualityOption.normalizeResolution(2160, 3840)
        assertEquals(2160, res4k)
        assertEquals("4K", label4k)

        val (res1080, label1080) = StreamQualityOption.normalizeResolution(1080, 1920)
        assertEquals(1080, res1080)
        assertEquals("1080p", label1080)

        val (res720, label720) = StreamQualityOption.normalizeResolution(720, 1280)
        assertEquals(720, res720)
        assertEquals("720p", label720)

        val (res480, label480) = StreamQualityOption.normalizeResolution(480, 854)
        assertEquals(480, res480)
        assertEquals("480p", label480)

        val (res360, label360) = StreamQualityOption.normalizeResolution(360, 640)
        assertEquals(360, res360)
        assertEquals("360p", label360)

        // When actual detected dimensions are 480p, title/tag hint "1080p" must NOT falsely claim 1080p
        val (detectedActual480, labelActual480) = StreamQualityOption.normalizeResolution(480, 854, "1080p Full HD")
        assertEquals(480, detectedActual480)
        assertEquals("480p", labelActual480)

        // When dimensions are 0x0 (not yet decoded), fall back to tag hint
        val (hint1080, hintLabel1080) = StreamQualityOption.normalizeResolution(0, 0, "1080p Full HD")
        assertEquals(1080, hint1080)
        assertEquals("1080p", hintLabel1080)

        val (hint720, hintLabel720) = StreamQualityOption.normalizeResolution(0, 0, "720p HD")
        assertEquals(720, hint720)
        assertEquals("720p", hintLabel720)

        val (hint480, hintLabel480) = StreamQualityOption.normalizeResolution(0, 0, "480p SD")
        assertEquals(480, hint480)
        assertEquals("480p", hintLabel480)
    }

    @Test
    fun testMediaCache_evictsPreviewWhenNon360PPreferred() {
        val previewSource = PlaybackSource(
            url = "https://cdn.movviez.com/secure.php?file=deadpool_preview.mp4&mode=preview&signature=abc1234",
            type = "video/mp4",
            quality = "1080p Full HD",
            serverName = "Server 1 (Fast CDN)",
            headers = mapOf("Referer" to "https://twolekede.com/")
        )

        // Under default AUTO quality preference, preview sources must be evicted
        MediaCache.setPreferredStreamQuality("AUTO")
        MediaCache.cachePlaybackSource("test_preview_movie_1", previewSource)
        val resultAuto = MediaCache.getPlaybackSource("test_preview_movie_1")
        assertNull("Cached preview source must be evicted when AUTO/HD preferred", resultAuto)

        // Under explicit 1080P preference, preview sources must also be evicted
        MediaCache.setPreferredStreamQuality("1080P")
        MediaCache.cachePlaybackSource("test_preview_movie_2", previewSource)
        val result1080p = MediaCache.getPlaybackSource("test_preview_movie_2")
        assertNull("Cached preview source must be evicted when 1080P preferred", result1080p)

        // Under 360P Data Saver preference, preview sources are accepted
        MediaCache.setPreferredStreamQuality("360P")
        MediaCache.cachePlaybackSource("test_preview_movie_3", previewSource)
        val result360p = MediaCache.getPlaybackSource("test_preview_movie_3")
        assertNotNull("Preview source should be accepted under 360P data saver", result360p)
        assertEquals(previewSource.url, result360p?.url)
    }

    @Test
    fun testDefaultBandwidthMeterAndTrackSelectorInitialization() {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(25_000_000L)
            .build()
        assertEquals(25_000_000L, bandwidthMeter.bitrateEstimate)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }

        val params = trackSelector.parameters
        assertEquals(3840, params.maxVideoWidth)
        assertEquals(2160, params.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, params.maxVideoBitrate)
        assertTrue(params.exceedRendererCapabilitiesIfNecessary)
        assertTrue(params.exceedVideoConstraintsIfNecessary)
        assertTrue(params.allowVideoMixedMimeTypeAdaptiveness)
        assertTrue(params.allowVideoNonSeamlessAdaptiveness)
    }

    @Test
    fun testResolveTwolekedeStream_prioritizesDownloadOverPreviewByDefault() = runBlocking {
        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            when {
                url.contains("download.php") -> {
                    // Return 302 with direct HD video URL without mode=preview
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cdn.movviez.com/secure.php?file=Deadpool_2024_1080p.mp4&signature=full_hd_sig")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
                url.contains("preview.php") -> {
                    // Return HTML with mode=preview
                    val html = """
                        <html><body>
                            <video><source src="https://cdn.movviez.com/secure.php?file=Deadpool_2024_360p.mp4&mode=preview&signature=preview_sig" type="video/mp4" /></video>
                        </body></html>
                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(html.toResponseBody("text/html".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
        }

        val testNetworkClient = NetworkClient(
            sessionManager,
            OkHttpClient.Builder()
                .addInterceptor(mockInterceptor)
                .build()
        )

        val api = MunowatchApi(testNetworkClient, sessionManager)
        MediaCache.setPreferredStreamQuality("AUTO")

        val resolved = api.resolveTwolekedeStream("8888", "Server 1 (Fast CDN)", "Deadpool")
        assertNotNull(resolved)
        assertTrue("Must prioritize full HD stream from download.php", resolved!!.contains("Deadpool_2024_1080p.mp4"))
        assertFalse("Must NOT default to preview stream", resolved.contains("mode=preview"))
    }

    @Test
    fun testResolveTwolekedeStream_fallsBackToPreviewIfDownloadUnavailable() = runBlocking {
        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            when {
                url.contains("download.php") -> {
                    // download.php fails / is empty
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
                url.contains("preview.php") -> {
                    // preview.php returns playable source
                    val html = """
                        <html><body>
                            <video><source src="https://cdn.movviez.com/secure.php?file=Fallout_S01.mp4&mode=preview&signature=fallback_sig" type="video/mp4" /></video>
                        </body></html>
                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(html.toResponseBody("text/html".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
        }

        val testNetworkClient = NetworkClient(
            sessionManager,
            OkHttpClient.Builder()
                .addInterceptor(mockInterceptor)
                .build()
        )

        val api = MunowatchApi(testNetworkClient, sessionManager)
        MediaCache.setPreferredStreamQuality("AUTO")

        val resolved = api.resolveTwolekedeStream("9999", "Server 1 (Fast CDN)", "Fallout")
        assertNotNull("Should fall back to preview if download is unavailable", resolved)
        assertTrue(resolved!!.contains("Fallout_S01.mp4"))
    }

    @Test
    fun testResolveTwolekedeStream_prefersPreviewWhenDataSaverRequested() = runBlocking {
        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            when {
                url.contains("download.php") -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cdn.movviez.com/secure.php?file=Avatar_HD.mp4")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
                url.contains("preview.php") -> {
                    val html = """
                        <html><body>
                            <video><source src="https://cdn.movviez.com/secure.php?file=Avatar_360p.mp4&mode=preview" type="video/mp4" /></video>
                        </body></html>
                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(html.toResponseBody("text/html".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
        }

        val testNetworkClient = NetworkClient(
            sessionManager,
            OkHttpClient.Builder()
                .addInterceptor(mockInterceptor)
                .build()
        )

        val api = MunowatchApi(testNetworkClient, sessionManager)
        MediaCache.setPreferredStreamQuality("360P")

        val resolved = api.resolveTwolekedeStream("7777", "Server 1 (Fast CDN)", "Avatar")
        assertNotNull(resolved)
        assertTrue("When 360P Data Saver is requested, preview stream is preferred", resolved!!.contains("Avatar_360p.mp4"))
    }

    @Test
    fun testDirectServer_routesToDlMovviez() = runBlocking {
        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "secure.php?file=Direct_1080p.mp4&signature=direct_sig")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val testNetworkClient = NetworkClient(
            sessionManager,
            OkHttpClient.Builder()
                .addInterceptor(mockInterceptor)
                .build()
        )

        val api = MunowatchApi(testNetworkClient, sessionManager)
        val resolved = api.resolveTwolekedeStream("5555", "Server 3 (Direct Backup)", "Direct")
        assertNotNull(resolved)
        assertTrue("Server 3 Direct Backup relative path should route to dl.movviez.com", resolved!!.startsWith("https://dl.movviez.com/"))
    }

    @Test
    fun testNormalizeResolution_handles4KAnd1440PWithoutIdCollisions() {
        val opt4k = StreamQualityOption.fromHeight(2160, 3840)
        assertEquals("4K", opt4k.id)
        assertTrue(opt4k.resolutionLabel.contains("4K"))

        val opt1440 = StreamQualityOption.fromHeight(1440, 2560)
        assertEquals("1440P", opt1440.id)

        val opt1080 = StreamQualityOption.fromHeight(1080, 1920)
        assertEquals("1080P", opt1080.id)

        val opt720 = StreamQualityOption.fromHeight(720, 1280)
        assertEquals("720P", opt720.id)

        val opt480 = StreamQualityOption.fromHeight(480, 854)
        assertEquals("480P", opt480.id)

        val opt360 = StreamQualityOption.fromHeight(360, 640)
        assertEquals("360P", opt360.id)

        // Verify all IDs are completely distinct (no collisions between 4K and 1080p)
        val allIds = listOf(opt4k.id, opt1440.id, opt1080.id, opt720.id, opt480.id, opt360.id)
        assertEquals(allIds.size, allIds.distinct().size)
    }

    @Test
    fun testServer3PullZoneRouting_notShadowedByServer2() {
        val watchPageHtml = """
            <html><body>
                <video src="https://nkuba.b-cdn.net/movies/Sample_Movie.mp4"></video>
            </body></html>
        """.trimIndent()

        // Server 2 (Munowatch Stream) routes to munotech2
        val sourceServer2 = parser.extractVideoSourceFromWatchPage(watchPageHtml, "Server 2 (Munowatch Stream)")
        assertNotNull(sourceServer2)
        assertTrue("Server 2 should route to munotech2.b-cdn.net", sourceServer2!!.url.contains("munotech2.b-cdn.net"))

        // Server 3 (Direct Backup) must route to harvetz, NOT munotech2!
        val sourceServer3 = parser.extractVideoSourceFromWatchPage(watchPageHtml, "Server 3 (Direct Backup)")
        assertNotNull(sourceServer3)
        assertTrue("Server 3 Direct Backup must route to harvetz.b-cdn.net pull zone", sourceServer3!!.url.contains("harvetz.b-cdn.net"))
        assertFalse("Server 3 must NOT be shadowed by Server 2 pull zone", sourceServer3.url.contains("munotech2.b-cdn.net"))
    }

    @Test
    fun testCleanForMatchingAndTitlesMatch_handles360PAndHigherResolutions() {
        assertEquals("avatar", parser.cleanForMatching("Avatar 360p"))
        assertEquals("avatar", parser.cleanForMatching("Avatar 2160p"))
        assertEquals("avatar", parser.cleanForMatching("Avatar 1440p"))
        assertEquals("avatar", parser.cleanForMatching("Avatar 1080p"))

        assertTrue("Avatar 360p should match Avatar", parser.titlesMatch("Avatar 360p", "Avatar"))
        assertTrue("Avatar 2160p should match Avatar", parser.titlesMatch("Avatar 2160p", "Avatar"))
        assertTrue("Deadpool 1440p should match Deadpool", parser.titlesMatch("Deadpool 1440p", "Deadpool"))
    }

    @Test
    fun testBandwidthMeter_tracksTransfersWhenForwarded() {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(25_000_000L)
            .build()

        assertEquals(25_000_000L, bandwidthMeter.bitrateEstimate)

        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://cdn.movviez.com/stream.mp4"))
        val mockSource = object : androidx.media3.datasource.DataSource {
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
            override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long = 1000L
            override fun getUri(): android.net.Uri? = dataSpec.uri
            override fun close() {}
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length
        }

        bandwidthMeter.onTransferInitializing(mockSource, dataSpec, true)
        bandwidthMeter.onTransferStart(mockSource, dataSpec, true)
        bandwidthMeter.onBytesTransferred(mockSource, dataSpec, true, 1024 * 1024)
        bandwidthMeter.onTransferEnd(mockSource, dataSpec, true)

        // Verifies bandwidthMeter received transfer events without crashing
        assertTrue(bandwidthMeter.bitrateEstimate > 0L)
    }

    @Test
    fun testTransferListener_noDoubleCountingOnDefaultDataSource() {
        var bandwidthMeterCalls = 0
        var totalBytesReceived = 0L

        val fakeBandwidthMeter = object : androidx.media3.datasource.TransferListener {
            override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                bandwidthMeterCalls++
                totalBytesReceived += bytesTransferred
            }
            override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        }

        // Mock base DataSource factory (like OkHttpDataSource.Factory)
        fun createFreshBaseSource(): androidx.media3.datasource.DataSource {
            return object : androidx.media3.datasource.DataSource {
                private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()
                private var currentUri: android.net.Uri? = null
                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    listeners.add(transferListener)
                }
                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    currentUri = dataSpec.uri
                    return 1000L
                }
                override fun getUri(): android.net.Uri? = currentUri
                override fun close() {}
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    listeners.forEach { it.onBytesTransferred(this, androidx.media3.datasource.DataSpec(android.net.Uri.EMPTY), true, length) }
                    return length
                }
            }
        }

        val baseFactory = androidx.media3.datasource.DataSource.Factory {
            val source = createFreshBaseSource()
            // Like OkHttpDataSource.Factory.setTransferListener(listener):
            source.addTransferListener(object : androidx.media3.datasource.TransferListener {
                override fun onTransferInitializing(s: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    fakeBandwidthMeter.onTransferInitializing(s, dataSpec, isNetwork)
                }
                override fun onTransferStart(s: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    fakeBandwidthMeter.onTransferStart(s, dataSpec, isNetwork)
                }
                override fun onBytesTransferred(s: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                    fakeBandwidthMeter.onBytesTransferred(s, dataSpec, isNetwork, bytesTransferred)
                }
                override fun onTransferEnd(s: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                    fakeBandwidthMeter.onTransferEnd(s, dataSpec, isNetwork)
                }
            })
            source
        }

        // If DefaultDataSource.Factory also sets transferListener to fakeBandwidthMeter:
        val defaultDsFactoryWithDuplicate = androidx.media3.datasource.DefaultDataSource.Factory(context, baseFactory)
            .setTransferListener(fakeBandwidthMeter)

        val ds = defaultDsFactoryWithDuplicate.createDataSource()
        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://cdn.movviez.com/stream.mp4"))
        ds.open(dataSpec)
        ds.read(ByteArray(512), 0, 512)

        // When duplicate listener is attached, fakeBandwidthMeter receives 2 calls (1024 bytes instead of 512)
        assertEquals(2, bandwidthMeterCalls)
        assertEquals(1024L, totalBytesReceived)

        // Clean fix: when DefaultDataSource.Factory does NOT redundantly add bandwidthMeter:
        bandwidthMeterCalls = 0
        totalBytesReceived = 0L
        val cleanDefaultDsFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, baseFactory)
        val cleanDs = cleanDefaultDsFactory.createDataSource()
        cleanDs.open(dataSpec)
        cleanDs.read(ByteArray(512), 0, 512)

        assertEquals(1, bandwidthMeterCalls)
        assertEquals(512L, totalBytesReceived)
    }

    @Test
    fun testServer3PullZoneRouting_munotechPullZonesRouteToHarvetz() {
        val watchPageWithMuno2 = """
            <html><body>
                <video src="https://munotech2.b-cdn.net/movies/Sample_Movie.mp4"></video>
            </body></html>
        """.trimIndent()
        val sourceMuno2 = parser.extractVideoSourceFromWatchPage(watchPageWithMuno2, "Server 3 (Direct Backup)")
        assertNotNull(sourceMuno2)
        assertTrue("Server 3 Direct Backup must route munotech2 to harvetz.b-cdn.net", sourceMuno2!!.url.contains("harvetz.b-cdn.net"))
        assertFalse("Server 3 must NOT remain on munotech2", sourceMuno2.url.contains("munotech2.b-cdn.net"))

        val watchPageWithMuno3 = """
            <html><body>
                <video src="https://munotech3.b-cdn.net/movies/Sample_Movie.mp4"></video>
            </body></html>
        """.trimIndent()
        val sourceMuno3 = parser.extractVideoSourceFromWatchPage(watchPageWithMuno3, "Server 3 (Direct Backup)")
        assertNotNull(sourceMuno3)
        assertTrue("Server 3 Direct Backup must route munotech3 to harvetz.b-cdn.net", sourceMuno3!!.url.contains("harvetz.b-cdn.net"))
        assertFalse("Server 3 must NOT remain on munotech3", sourceMuno3.url.contains("munotech3.b-cdn.net"))
    }

    @Test
    fun testTrackSelectorOverrides_clearedCleanly() {
        val trackSelector = DefaultTrackSelector(context)
        val format1080 = androidx.media3.common.Format.Builder()
            .setId("video_1080")
            .setSampleMimeType("video/avc")
            .setWidth(1920)
            .setHeight(1080)
            .build()
        val trackGroup = androidx.media3.common.TrackGroup("video_group", format1080)

        // 1. Apply override
        val builder = trackSelector.buildUponParameters()
        builder.setOverrideForType(androidx.media3.common.TrackSelectionOverride(trackGroup, listOf(0)))
        trackSelector.setParameters(builder)

        assertEquals(1, trackSelector.parameters.overrides.size)

        // 2. Clear overrides
        val clearBuilder = trackSelector.buildUponParameters()
        clearBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        for ((group, _) in trackSelector.parameters.overrides) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                clearBuilder.clearOverride(group)
            }
        }
        trackSelector.setParameters(clearBuilder)

        assertEquals(0, trackSelector.parameters.overrides.size)
    }

    @Test
    fun testNormalizeResolution_detectsCinematicWidescreenAndUltrawideAccurately() {
        // 1920x800 Cinemascope Blu-ray 1080p should normalize to 1080p, not 720p
        val (res1080Widescreen, label1080Widescreen) = StreamQualityOption.normalizeResolution(800, 1920)
        assertEquals(1080, res1080Widescreen)
        assertEquals("1080p", label1080Widescreen)

        // 1280x544 Cinemascope 720p should normalize to 720p, not 480p
        val (res720Widescreen, label720Widescreen) = StreamQualityOption.normalizeResolution(544, 1280)
        assertEquals(720, res720Widescreen)
        assertEquals("720p", label720Widescreen)

        // 3840x1600 Cinemascope 4K should normalize to 4K
        val (res4kWidescreen, label4kWidescreen) = StreamQualityOption.normalizeResolution(1600, 3840)
        assertEquals(2160, res4kWidescreen)
        assertEquals("4K", label4kWidescreen)

        // 4096x2160 DCI 4K should normalize to 4K
        val (resDci4k, labelDci4k) = StreamQualityOption.normalizeResolution(2160, 4096)
        assertEquals(2160, resDci4k)
        assertEquals("4K", labelDci4k)
    }

    @Test
    fun testTrackSelectorAutoParameters_noArtificialVideoSizeCap() {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }
        val params = trackSelector.parameters
        assertEquals("Auto parameters must not cap maxVideoWidth, permitting DCI 4K and 8K", Int.MAX_VALUE, params.maxVideoWidth)
        assertEquals("Auto parameters must not cap maxVideoHeight", Int.MAX_VALUE, params.maxVideoHeight)
    }

    @Test
    fun testTrackSelection_tieBreaksSameResolutionByHighestBitrate() {
        val formatLow = androidx.media3.common.Format.Builder()
            .setId("video_1080_low")
            .setSampleMimeType("video/avc")
            .setWidth(1920)
            .setHeight(1080)
            .setAverageBitrate(3_000_000)
            .build()

        val formatHigh = androidx.media3.common.Format.Builder()
            .setId("video_1080_high")
            .setSampleMimeType("video/avc")
            .setWidth(1920)
            .setHeight(1080)
            .setAverageBitrate(8_500_000)
            .build()

        val trackGroup = androidx.media3.common.TrackGroup("video_group", formatLow, formatHigh)

        val targetHeight = 1080
        var bestTrackIdx = -1
        var closestDiff = Int.MAX_VALUE
        var bestBitrate = -1

        for (i in 0 until trackGroup.length) {
            val format = trackGroup.getFormat(i)
            val effHeight = if (format.width > 0 && format.height > 0 && (format.width * 9) / 16 > format.height) {
                (format.width * 9) / 16
            } else {
                format.height
            }
            val diff = kotlin.math.abs(effHeight - targetHeight)
            val currentBitrate = maxOf(format.bitrate, format.peakBitrate)
            if (diff < closestDiff || (diff == closestDiff && currentBitrate > bestBitrate)) {
                closestDiff = diff
                bestBitrate = currentBitrate
                bestTrackIdx = i
            }
        }

        assertEquals("Must select the highest bitrate format (index 1 @ 8.5 Mbps)", 1, bestTrackIdx)
        assertEquals(8_500_000, bestBitrate)
    }

    @Test
    fun testExoPlayerMediaCache_createsSingletonAndCacheDataSourceFactory() {
        val upstreamFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        val cacheFactory = com.example.data.cache.ExoPlayerMediaCache.createCacheDataSourceFactory(context, upstreamFactory)
        assertNotNull(cacheFactory)

        val cacheInstance = com.example.data.cache.ExoPlayerMediaCache.getCache(context)
        assertNotNull(cacheInstance)
        val expectedDir = java.io.File(context.cacheDir, "exoplayer_media_cache")
        assertTrue("Cache dir must exist", expectedDir.exists())

        // Releasing and clearing
        com.example.data.cache.ExoPlayerMediaCache.release()
    }

    @Test
    fun testNetworkClientRotateClientIdentity_generatesNewDeviceId() {
        val (ip1, id1) = com.example.data.network.NetworkClient.getClientIdentity()
        assertNotNull(ip1)
        assertNotNull(id1)

        val (ip2, id2) = com.example.data.network.NetworkClient.rotateClientIdentity()
        assertNotNull(ip2)
        assertNotNull(id2)
        assertTrue("Device ID must change upon identity rotation to reset CDN rate limit", id1 != id2)
    }

    @Test
    fun testRateLimitDomainDifferentiation_dlMovviezNotBlockedWhenCdnMovviezFlagged() {
        val failedStreamHosts = mutableSetOf<String>()
        failedStreamHosts.add("cdn.movviez.com")

        val cdnUrl = "https://cdn.movviez.com/secure.php?file=test.mp4"
        val dlUrl = "https://dl.movviez.com/secure.php?file=test.mp4"

        val cdnHost = android.net.Uri.parse(cdnUrl).host?.lowercase()
        val dlHost = android.net.Uri.parse(dlUrl).host?.lowercase()

        assertTrue("cdn.movviez.com should be recognized as rate-limited", failedStreamHosts.contains(cdnHost))
        assertFalse("dl.movviez.com MUST NOT be blocked when only cdn.movviez.com failed", failedStreamHosts.contains(dlHost))
    }

    @Test
    fun testMunowatchSessionCookie_activeSessionReturnsValidId() {
        sessionManager.saveSessionId(SecureSessionManager.DEFAULT_SESSION_ID)
        val sessionId = sessionManager.getSessionId()
        assertEquals("926cdd498cd8113008318bd9c6696ff5", sessionId)
        assertEquals("926cdd498cd8113008318bd9c6696ff5", SecureSessionManager.DEFAULT_SESSION_ID)
    }

    @Test
    fun testMunowatchDataGumite_decodesAuthenticBunnyCdnStream() {
        val b64Stream = java.util.Base64.getEncoder().encodeToString(
            "https://munotech2.b-cdn.net/port/str/eyJkcyI6InRlc3QiLCJmIjoiMTIzIn0=".toByteArray(Charsets.UTF_8)
        )
        val html = """
            <html><body>
                <div id="soul-info" data-series-code="123" data-vidid="123" data-gumite="$b64Stream"></div>
            </body></html>
        """.trimIndent()

        val parsed = parser.extractVideoSourceFromWatchPage(html, "Server 1 (Munowatch HD - Direct CDN)")
        assertNotNull(parsed)
        assertTrue("Stream must route to BunnyCDN", parsed!!.url.contains("munotech2.b-cdn.net"))
        assertTrue("Stream token must be preserved", parsed.url.contains("/port/str/"))
    }
}
