const { CatalogService } = require('./dist/services/catalog.service');

async function testParse() {
  const url = 'https://twolekede.com/categories.php?name=VJ%20Light';
  const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const html = await res.text();
  const items = CatalogService.parseTwolekedeSeriesCards(html, 'VJ Light', 'VJ Light');
  console.log('Parsed items count for VJ Light:', items.length);
  for (const it of items.slice(0, 5)) {
    console.log(it.title, it.vj, it.isSeries, it.id);
  }
}
testParse().catch(console.error);
