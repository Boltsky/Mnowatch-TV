async function inspectVjLight() {
  const url = 'https://twolekede.com/categories.php?name=VJ%20Light';
  const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const html = await res.text();
  console.log('HTML length:', html.length);
  const links = [...html.matchAll(/<a\s+[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)];
  console.log('Total <a> tags:', links.length);
  const seriesLinks = links.filter(l => l[1].includes('series'));
  console.log('Series links count:', seriesLinks.length);
  for (const sl of seriesLinks.slice(0, 5)) {
    console.log('href:', sl[1]);
    console.log('snippet:', sl[2].replace(/\s+/g, ' ').slice(0, 200));
  }
}
inspectVjLight();
