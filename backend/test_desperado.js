async function inspectDesperado() {
  for (const id of ['117980', '6947']) {
    const url = `https://twolekede.com/preview.php?id=${id}`;
    const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
    const html = await res.text();
    console.log(`\n--- ID ${id} (${url}):`);
    const title = html.match(/<h[1-6][^>]*>([^<]+)<\/h[1-6]>/gi);
    console.log('Headings:', title);
    const video = html.match(/<source[^>]+src=["']([^"']+)["']/i) || html.match(/src:\s*["']([^"']+)["']/i);
    console.log('Video src:', video ? video[1] : 'none');
    const iframe = html.match(/<iframe[^>]+src=["']([^"']+)["']/i);
    console.log('Iframe src:', iframe ? iframe[1] : 'none');
  }
}
inspectDesperado();
