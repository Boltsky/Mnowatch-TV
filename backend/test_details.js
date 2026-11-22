async function checkDetails() {
  for (const id of ['117980', '6947']) {
    const res = await fetch(`https://twolekede.com/moviedetails.php?id=${id}`, { headers: { 'User-Agent': 'Mozilla/5.0' } });
    const html = await res.text();
    console.log(`\n=== ID ${id} Details ===`);
    const title = html.match(/<h[1-6][^>]*>([^<]+)<\/h[1-6]>/gi);
    console.log('Headings:', title);
    const p = [...html.matchAll(/<p[^>]*>([\s\S]*?)<\/p>/gi)].map(m => m[1].replace(/<[^>]+>/g, '').trim());
    console.log('Paragraphs:', p.slice(0, 10));
    const spans = [...html.matchAll(/<span[^>]*>([\s\S]*?)<\/span>/gi)].map(m => m[1].replace(/<[^>]+>/g, '').trim());
    console.log('Spans:', spans.slice(0, 10));
  }
}
checkDetails();
