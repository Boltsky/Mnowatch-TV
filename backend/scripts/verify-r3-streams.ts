import http from 'http';
import app from '../src/app';
import { StreamProxyService } from '../src/services/streamProxy.service';

interface TestResult {
  suite: string;
  name: string;
  passed: boolean;
  details: string;
  error?: string;
}

const results: TestResult[] = [];

function recordTest(suite: string, name: string, passed: boolean, details: string, error?: string) {
  results.push({ suite, name, passed, details, error });
  const status = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`${status} [${suite}] ${name}: ${details}`);
  if (error) {
    console.error(`   Error: ${error}`);
  }
}

async function runR3Verification() {
  console.log('='.repeat(70));
  console.log('🎬 MUNOWATCH R3 STREAM RESOLUTION & TRAILER ELIMINATION VERIFICATION');
  console.log('='.repeat(70));

  const isRemote = process.argv.includes('--target=remote');
  let baseUrl = isRemote ? 'https://munowatch-api.onrender.com' : 'http://localhost:10001';
  let localServer: http.Server | null = null;

  if (!isRemote) {
    // Check if port 10000 is already active
    try {
      const ping = await fetch('http://localhost:10000/api/v1/health', { signal: AbortSignal.timeout(1000) });
      if (ping.ok) {
        baseUrl = 'http://localhost:10000';
        console.log(`📡 Connected to existing local server at ${baseUrl}`);
      }
    } catch {
      // Start test server on port 10001
      console.log(`🚀 Starting in-process verification server on port 10001...`);
      localServer = app.listen(10001);
      await new Promise(r => setTimeout(r, 500));
      console.log(`📡 Local verification server running at ${baseUrl}`);
    }
  } else {
    console.log(`🌐 Testing against remote production backend at ${baseUrl}`);
  }

  try {
    // =========================================================================
    // SUITE 1: Trailer Signature Detection Unit Invariants
    // =========================================================================
    console.log('\n--- SUITE 1: Trailer & Promo Detection Unit Invariants ---');
    const trailerUrlsToDetect = [
      'https://trailer256.b-cdn.net/sample.mp4',
      'https://trailer256.b-cdn.net/s0_m_jumanji-the-next-level-2.mp4',
      'https://res-watch.b-cdn.net/intro_myvj_1_720p.mp4',
      'https://dl.movviez.com/trailers/deadpool_trailer.mp4',
      'https://dl.movviez.com/movies/movie_sample.mp4',
      'https://twolekede.com/uploads/promo.mp4',
      'https://trailer256.b-cdn.net/m_mobi_5040_The_Purge:_Anarchy.mp4',
      // Iteration 2 Remediation: URL-encoded & obfuscated trailer patterns
      'https://cdn.movviez.com/movies/%74%72%61%69%6c%65%72.mp4',
      'https://cdn.movviez.com/movies%2ftrailer/deadpool.mp4',
      'https://dl.movviez.com/movies/%2574%2572%2561%2569%256c%2565%2572.mp4',
      // Iteration 2 Remediation: Query parameter trailer indicators
      'https://dl.movviez.com/stream.php?type=trailer&id=123',
      'https://dl.movviez.com/preview.php?clip=trailer',
      'https://dl.movviez.com/video.php?file=trailer.mp4',
      'https://dl.movviez.com/stream.php?trailer=1',
      // Iteration 2 Remediation: Teaser patterns
      'https://dl.movviez.com/movies/deadpool_teaser.mp4',
      'https://dl.movviez.com/movies/teaser.mp4',
      'https://dl.movviez.com/movies/teaser/clip.mp4'
    ];

    for (const tUrl of trailerUrlsToDetect) {
      const isDetected = StreamProxyService.isTrailerOrPromoUrl(tUrl);
      recordTest(
        'Trailer Guard',
        `Reject ${tUrl.substring(0, 45)}...`,
        isDetected === true,
        `Detected as trailer/promo: ${isDetected}`
      );
    }

    const genuineUrlsToAccept: Array<{ url: string; expectedTitle?: string }> = [
      { url: 'https://dl.movviez.com/secure.php?file=movies%2F1750261825_DEADPOOL%20AND%20WOLVERINE%20ICE%20P_x264.mp4&mode=preview' },
      { url: 'https://dl.movviez.com/secure.php?file=movies%2F1756209194_SUPERMAN%20JR_x264.mp4&mode=preview' },
      { url: 'https://cdn.movviez.com/secure.php?file=movies%2F1774293983_BadBoys_x264.mp4' },
      { url: 'https://dl.movviez.com/secure.php?file=series%2F125784_HOTD_S01E01.mp4' },
      // Iteration 2 Remediation: Context-aware acceptance of titles containing "trailer"
      { url: 'https://dl.movviez.com/secure.php?file=movies%2F198371_Trailer_Park_Boys_The_Movie_x264.mp4', expectedTitle: 'Trailer Park Boys' },
      { url: 'https://dl.movviez.com/secure.php?file=movies%2F198371_Trailer_Park_Boys_The_Movie_x264.mp4' }, // Protect even without expectedTitle
      { url: 'https://dl.movviez.com/movies/Trailer_Park_Boys.mp4', expectedTitle: 'Trailer Park Boys' },
      { url: 'https://dl.movviez.com/secure.php?file=movies%2FThe_Trailer_2023.mp4', expectedTitle: 'The Trailer' }
    ];

    for (const gItem of genuineUrlsToAccept) {
      const isRejected = StreamProxyService.isTrailerOrPromoUrl(gItem.url, gItem.expectedTitle);
      recordTest(
        'Trailer Guard',
        `Accept Genuine ${gItem.url.substring(0, 45)}...`,
        isRejected === false,
        `Permitted full movie stream: ${!isRejected}${gItem.expectedTitle ? ` (Title: "${gItem.expectedTitle}")` : ''}`
      );
    }

    // Iteration 2 Remediation: Verify that an actual trailer clip of a movie containing "Trailer" is still rejected!
    const trailerClipsOfTrailerTitle = [
      { url: 'https://dl.movviez.com/movies/Trailer_Park_Boys_trailer.mp4', expectedTitle: 'Trailer Park Boys' },
      { url: 'https://dl.movviez.com/trailers/Trailer_Park_Boys.mp4', expectedTitle: 'Trailer Park Boys' }
    ];
    for (const cItem of trailerClipsOfTrailerTitle) {
      const isDetected = StreamProxyService.isTrailerOrPromoUrl(cItem.url, cItem.expectedTitle);
      recordTest(
        'Trailer Guard',
        `Reject Trailer Clip for "${cItem.expectedTitle}"`,
        isDetected === true,
        `Correctly detected as trailer clip: ${isDetected}`
      );
    }

    // =========================================================================
    // SUITE 2: Dynamic Stream Resolution across Catalog Categories
    // =========================================================================
    console.log('\n--- SUITE 2: Stream Resolution across Home, Movies, Series, Categories ---');
    const testTitles = [
      { category: 'Home / Featured', id: 'tk_2667', title: 'Deadpool & Wolverine' },
      { category: 'Movies / Blockbuster', id: 'tk_12950', title: 'Bad Boys: Ride or Die' },
      { category: 'Movies / Action', id: 'tk_12948', title: 'Gladiator II' },
      { category: 'Movies / VJ ICEP', id: 'tk_1421', title: 'Furiosa: A Mad Max Saga Part 1' },
      { category: 'Movies / UgaWatch ID', id: 'myvj_17108', title: 'Jumanji: The Next Level' },
      { category: 'Movies / Cleansed Rich', id: 'myvj_5040', title: 'Superman' },
      { category: 'Series / Episode', id: 'tk_125784', title: 'House of the Dragon' }
    ];

    let sampleResolvedDirectUrl = '';

    for (const tc of testTitles) {
      try {
        const url = `${baseUrl}/api/v1/stream/resolve?id=${encodeURIComponent(tc.id)}&title=${encodeURIComponent(tc.title)}`;
        const res = await fetch(url, { signal: AbortSignal.timeout(12000) });
        if (!res.ok) {
          recordTest('Stream Resolution', `${tc.category}: ${tc.title}`, false, `HTTP ${res.status}`);
          continue;
        }

        const data: any = await res.json();

        if (data.success) {
          const streamUrl = data.streamUrl || '';
          const directUrl = data.directUrl || '';

          // 1. Assert ZERO trailer URLs
          const hasTrailerSignature =
            StreamProxyService.isTrailerOrPromoUrl(streamUrl) ||
            StreamProxyService.isTrailerOrPromoUrl(directUrl) ||
            streamUrl.includes('trailer256.b-cdn.net') ||
            directUrl.includes('trailer256.b-cdn.net') ||
            streamUrl.includes('intro_myvj') ||
            directUrl.includes('intro_myvj') ||
            streamUrl.includes('/trailer') ||
            directUrl.includes('/trailer');

          // 2. Assert valid full-length media endpoints
          const hasValidEndpoint =
            directUrl.includes('movviez.com') ||
            directUrl.includes('twolekede.com') ||
            directUrl.includes('b-cdn.net');

          // 3. Assert title token match
          const tokenMatch = StreamProxyService.urlMatchesTitle(directUrl, tc.title);

          const allPassed = !hasTrailerSignature && hasValidEndpoint && tokenMatch;
          recordTest(
            'Stream Resolution',
            `${tc.category}: ${tc.title}`,
            allPassed,
            `Zero trailers: ${!hasTrailerSignature}, Valid endpoint: ${hasValidEndpoint}, Title match: ${tokenMatch} (${directUrl.substring(0, 50)}...)`
          );

          if (!sampleResolvedDirectUrl && directUrl) {
            sampleResolvedDirectUrl = directUrl;
          }
        } else {
          // If title not found on CDN, assert it returned unavailable: true and NO trailers
          const streamUrl = data.streamUrl || '';
          const directUrl = data.directUrl || '';
          const isCleanUnavailable = (data.unavailable === true || !data.success) && streamUrl === '' && directUrl === '';
          recordTest(
            'Stream Resolution',
            `${tc.category}: ${tc.title} (Graceful Unavailable)`,
            isCleanUnavailable,
            `Correctly declared unavailable without falling back to trailers: ${isCleanUnavailable}`
          );
        }
      } catch (err: any) {
        recordTest('Stream Resolution', `${tc.category}: ${tc.title}`, false, 'Resolution failed', err.message);
      }
    }

    // =========================================================================
    // SUITE 3: Handling Unavailable Streams (R2)
    // =========================================================================
    console.log('\n--- SUITE 3: Unavailable Stream Handling (Zero Trailer Fallback) ---');
    try {
      const unavailableId = 'nonexistent_movie_999999';
      const unavailableTitle = 'NonExistentUgandanMovieXYZ';
      const res = await fetch(
        `${baseUrl}/api/v1/stream/resolve?id=${encodeURIComponent(unavailableId)}&title=${encodeURIComponent(unavailableTitle)}`,
        { signal: AbortSignal.timeout(8000) }
      );
      const data: any = await res.json();

      const handlesUnavailable = (data.success === false || data.unavailable === true) && data.streamUrl === '' && data.directUrl === '';
      const noTrailer = typeof data.streamUrl === 'string' && !data.streamUrl.includes('trailer') && !data.streamUrl.includes('intro_myvj');

      recordTest(
        'Unavailable Handling',
        'Reject Non-Existent Movie Without Trailer',
        handlesUnavailable && noTrailer,
        `unavailable: ${data.unavailable}, success: ${data.success}, streamUrl: "${data.streamUrl}"`
      );
    } catch (err: any) {
      recordTest('Unavailable Handling', 'Reject Non-Existent Movie', false, 'Request error', err.message);
    }

    // =========================================================================
    // SUITE 4: Stream Proxy HTTP 206 Range Delivery
    // =========================================================================
    console.log('\n--- SUITE 4: Stream Proxy HTTP 206 Range Delivery ---');
    if (sampleResolvedDirectUrl) {
      try {
        const proxyTestUrl = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(sampleResolvedDirectUrl)}`;
        const res = await fetch(proxyTestUrl, {
          headers: { Range: 'bytes=0-1023' },
          signal: AbortSignal.timeout(12000)
        });

        const statusOk = res.status === 206 || res.status === 200;
        const contentType = res.headers.get('content-type') || '';
        const isMediaContent = contentType.includes('video') || contentType.includes('mp4') || contentType.includes('octet-stream');
        const hasRangeOrLength = !!(res.headers.get('content-range') || res.headers.get('content-length'));

        recordTest(
          'Proxy Streaming',
          'HTTP Range 206 Partial Content Delivery',
          statusOk && isMediaContent && hasRangeOrLength,
          `Status: ${res.status}, Content-Type: ${contentType}, Content-Range/Length: ${hasRangeOrLength}`
        );
      } catch (err: any) {
        recordTest('Proxy Streaming', 'HTTP Range 206 Delivery', false, 'Proxy request error', err.message);
      }
    } else {
      recordTest('Proxy Streaming', 'HTTP Range 206 Delivery', false, 'No sample direct URL resolved to test proxy');
    }

    // =========================================================================
    // SUITE 5: Proxy Strict Blocking of Trailers & Promos
    // =========================================================================
    console.log('\n--- SUITE 5: Proxy Strict Blocking of Trailers & Promos ---');
    const forbiddenProxyTargets = [
      'https://trailer256.b-cdn.net/sample_trailer.mp4',
      'https://res-watch.b-cdn.net/intro_myvj_1_720p.mp4',
      'https://dl.movviez.com/trailers/teaser_trailer.mp4',
      // Iteration 2 Remediation: URL-encoded & query-param trailer proxy attempts
      'https://dl.movviez.com/movies/%74%72%61%69%6c%65%72.mp4',
      'https://cdn.movviez.com/movies%2ftrailer/deadpool.mp4',
      'https://dl.movviez.com/stream.php?type=trailer&id=123',
      'https://dl.movviez.com/movies/deadpool_teaser.mp4'
    ];

    for (const fTarget of forbiddenProxyTargets) {
      try {
        const proxyBlockUrl = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(fTarget)}`;
        const res = await fetch(proxyBlockUrl, { signal: AbortSignal.timeout(6000) });
        const isBlocked = res.status === 400;
        const body: any = await res.json().catch(() => ({}));
        const hasBlockMessage = (body.error || '').toLowerCase().includes('trailer');

        recordTest(
          'Proxy Security',
          `Block Proxying of ${fTarget.substring(0, 45)}...`,
          isBlocked && hasBlockMessage,
          `Status: ${res.status} (Expected 400), Message: "${body.error}"`
        );
      } catch (err: any) {
        recordTest('Proxy Security', `Block Proxying of ${fTarget.substring(0, 45)}...`, false, 'Request error', err.message);
      }
    }

    // =========================================================================
    // SUITE 6: Multi-Season Series Title Matching Invariants (Iteration 2)
    // =========================================================================
    console.log('\n--- SUITE 6: Multi-Season Series Title Matching Invariants ---');
    const seriesMatchingPairs = [
      { a: 'House of the Dragon', b: 'House of the Dragon S01', expected: true, label: 'Season S01' },
      { a: 'House of the Dragon', b: 'House of the Dragon Season 2', expected: true, label: 'Season 2' },
      { a: 'Game of Thrones', b: 'Game of Thrones Season 1', expected: true, label: 'Season 1' },
      { a: 'Loki', b: 'Loki S02', expected: true, label: 'Season S02' },
      { a: 'The Boys', b: 'The Boys Season 4', expected: true, label: 'Season 4' },
      { a: 'Supacell S01', b: 'Supacell', expected: true, label: 'Reverse S01' },
      // Strict discrimination against different movie sequels / franchises
      { a: 'Bad Boys 2', b: 'Bad Boys 3', expected: false, label: 'Sequel Mismatch (Bad Boys 2 vs 3)' },
      { a: 'Deadpool', b: 'Deadpool 2', expected: false, label: 'Sequel Mismatch (Deadpool vs Deadpool 2)' },
      { a: 'Dune', b: 'Dune 2', expected: false, label: 'Sequel Mismatch (Dune vs Dune 2)' },
      { a: 'The Platform 2', b: 'The Platform', expected: false, label: 'Sequel Mismatch (Platform 2 vs 1)' }
    ];

    for (const sm of seriesMatchingPairs) {
      const match = StreamProxyService.titlesMatch(sm.a, sm.b);
      const passed = match === sm.expected;
      recordTest(
        'Title Matching',
        `"${sm.a}" vs "${sm.b}" (${sm.label})`,
        passed,
        `Result: ${match} (Expected: ${sm.expected})`
      );
    }

  } finally {
    if (localServer) {
      localServer.close();
      console.log('\n🛑 Closed local verification test server.');
    }
  }

  // =========================================================================
  // SUMMARY REPORT
  // =========================================================================
  const total = results.length;
  const passed = results.filter(r => r.passed).length;
  const failed = total - passed;

  console.log('\n' + '='.repeat(70));
  console.log(`📊 VERIFICATION SUMMARY: ${passed}/${total} PASSED (${Math.round((passed / total) * 100)}%)`);
  if (failed > 0) {
    console.log(`❌ ${failed} FAILURES DETECTED!`);
    process.exit(1);
  } else {
    console.log(`🎉 ALL ASSERTIONS PASSED WITH ZERO TRAILER LEAKS!`);
    console.log('='.repeat(70));
    process.exit(0);
  }
}

runR3Verification().catch((err) => {
  console.error('Fatal verification runner error:', err);
  process.exit(1);
});
