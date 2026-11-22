async function searchTwolekede(q) {
  const url = `https://twolekede.com/search.php?q=${encodeURIComponent(q)}`;
  console.log('\n--- Searching:', q, '-->', url);
  try {
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
      }
    });
    const html = await res.text();
    console.log('HTML length:', html.length);
    const movieMatches = (html.match(/moviedetails\.php/gi) || []).length;
    const seriesMatches = (html.match(/seriesdetails\.php/gi) || []).length;
    console.log(`  Movies found: ${movieMatches}, Series found: ${seriesMatches}`);
    // Extract a few titles
    const cardRegex = /<a\s+[^>]*href=["'](?:moviedetails|seriesdetails|preview)\.php\?id=(\d+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let m, count = 0;
    while ((m = cardRegex.exec(html)) !== null && count < 5) {
      count++;
      console.log(`  Link: href match with id=${m[1]}`);
    }
  } catch (e) {
    console.error('Error:', e);
  }
}

async function run() {
  await searchTwolekede('Light');
  await searchTwolekede('VJ Light');
  await searchTwolekede('Pauleta');
  await searchTwolekede('VJ Pauleta');
  await searchTwolekede('Kiwuule');
  await searchTwolekede('Desperado');
}

run();
