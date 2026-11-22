async function checkVj(name) {
  const url = `https://twolekede.com/categories.php?name=${encodeURIComponent(name)}`;
  console.log('\n--- Checking:', name, '-->', url);
  try {
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
      }
    });
    const html = await res.text();
    console.log('HTML length:', html.length);
    // Find movie links
    const cardRegex = /<a\s+[^>]*href=["'](?:moviedetails|preview)\.php\?id=(\d+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let match;
    let count = 0;
    while ((match = cardRegex.exec(html)) !== null && count < 5) {
      count++;
      const id = match[1];
      const body = match[2];
      const titleMatch = body.match(/class=["']title["'][^>]*>([^<]+)<\//i) || body.match(/<h6>([^<]+)<\/h6>/i) || body.match(/<h5[^>]*>([^<]+)<\/h5>/i);
      console.log(`  Found movie id=${id}, title=${titleMatch ? titleMatch[1].trim() : 'unknown'}`);
    }
    console.log(`  Total movies on page 1 for "${name}":`, (html.match(/moviedetails\.php/gi) || []).length);
    
    // Also check series.php or categories.php for series
    const seriesUrl = `https://twolekede.com/categories.php?name=${encodeURIComponent(name)}&type=series`;
    // Or check if there are series links in html
    const seriesMatch = (html.match(/seriesdetails\.php/gi) || []).length;
    console.log(`  Series links in html:`, seriesMatch);
  } catch (e) {
    console.error('Error:', e);
  }
}

async function run() {
  await checkVj('VJ Light');
  await checkVj('Light');
  await checkVj('VJ Pauleta');
  await checkVj('Pauleta');
  await checkVj('MK Kisuule');
  await checkVj('VJ MK Kiwuule');
  await checkVj('VJ Kisuule');
}

run();
