import { Request, Response } from 'express';
import { StreamProxyService } from '../services/streamProxy.service';

export class StreamController {
  public static proxy(req: Request, res: Response): void {
    StreamProxyService.handleProxyStream(req, res);
  }

  public static async resolve(req: Request, res: Response): Promise<void> {
    const id = (req.query.id as string) || '';
    const title = (req.query.title as string) || '';
    const vj = (req.query.vj as string) || '';

    if (!id && !title) {
      res.status(400).json({ error: 'Missing id or title parameter' });
      return;
    }

    try {
      const resolvedDirect = await StreamProxyService.resolveMediaStream(id, title, vj);
      if (resolvedDirect && !StreamProxyService.isTrailerOrPromoUrl(resolvedDirect, title)) {
        const protocol = req.headers['x-forwarded-proto'] || req.protocol;
        const host = req.headers['x-forwarded-host'] || req.get('host');
        const baseUrl = `${protocol}://${host}`;
        const proxyUrl = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(resolvedDirect)}${title ? `&title=${encodeURIComponent(title)}` : ''}`;

        const servers = [
          {
            id: 'server-direct',
            name: resolvedDirect.includes('b-cdn.net') ? 'Server 1 (BunnyCDN High-Speed - Direct)' : 'Server 1 (Direct CDN Stream)',
            quality: '1080p Full HD',
            streamUrl: resolvedDirect,
            isProxy: false
          },
          {
            id: 'server-proxy',
            name: 'Server 2 (Munowatch High-Speed Proxy - CORS Safe)',
            quality: '1080p FHD',
            streamUrl: proxyUrl,
            isProxy: true
          }
        ];

        // Heterogeneous CDN failover: provide independent BunnyCDN edge stream when available
        if (title && !resolvedDirect.includes('b-cdn.net')) {
          try {
            const bcdnDirect = await StreamProxyService.resolveBunnyCdnStream(title, vj);
            if (bcdnDirect && bcdnDirect !== resolvedDirect && !StreamProxyService.isTrailerOrPromoUrl(bcdnDirect, title)) {
              const bcdnProxy = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(bcdnDirect)}${title ? `&title=${encodeURIComponent(title)}` : ''}`;
              servers.push(
                {
                  id: 'server-bunny-direct',
                  name: 'Server 3 (BunnyCDN Edge - Direct)',
                  quality: '1080p FHD',
                  streamUrl: bcdnDirect,
                  isProxy: false
                },
                {
                  id: 'server-bunny-proxy',
                  name: 'Server 4 (BunnyCDN Edge - CORS Safe)',
                  quality: '1080p FHD',
                  streamUrl: bcdnProxy,
                  isProxy: true
                }
              );
            }
          } catch {}
        }

        res.json({
          success: true,
          directUrl: resolvedDirect,
          streamUrl: proxyUrl,
          servers
        });
      } else {
        const alternative = title ? await StreamProxyService.findAlternativeMovie(title) : null;
        res.json({
          success: false,
          unavailable: true,
          streamUrl: '',
          directUrl: '',
          error: title
            ? `A full Ugandan translated release for "${title}" is not currently available. We strictly stream genuine full movies, not promotional trailers.`
            : 'Full Ugandan translated movie is currently unavailable. No trailer playback permitted.',
          alternative: alternative || undefined
        });
      }
    } catch (e: any) {
      res.status(500).json({ error: 'Stream resolution failed', message: e.message });
    }
  }

  public static async download(req: Request, res: Response): Promise<void> {
    let targetUrl = (req.query.url as string) || '';
    const id = (req.query.id as string) || '';
    const title = (req.query.title as string) || '';
    const vj = (req.query.vj as string) || '';
    const filename = (req.query.filename as string) || '';
    const season = req.query.season as string;
    const episode = req.query.episode as string;

    try {
      // 1. Recursively unwrap nested proxy URLs to prevent self-calling loops & 502 Bad Gateway
      while (targetUrl.includes('/stream/proxy?url=')) {
        const idx = targetUrl.indexOf('/stream/proxy?url=');
        const raw = targetUrl.substring(idx + 18);
        try {
          targetUrl = decodeURIComponent(raw);
        } catch {
          targetUrl = raw;
        }
      }

      // 2. Auto-resolve Twolekede preview HTML pages to genuine media stream URLs
      if (targetUrl.includes('preview.php') || targetUrl.includes('twolekede.com/preview')) {
        const match = targetUrl.match(/id=(\d+)/);
        if (match) {
          const direct = await StreamProxyService.resolveTwolekedeStreamById(match[1], title);
          if (direct) {
            targetUrl = direct;
          } else {
            targetUrl = ''; // Clear so resolveMediaStream handles it below
          }
        } else {
          targetUrl = '';
        }
      }

      // 3. If stream is not direct or empty, resolve fresh genuine direct CDN stream
      if (!targetUrl || (!targetUrl.includes('.mp4') && !targetUrl.includes('secure.php') && !targetUrl.includes('b-cdn.net'))) {
        if (id || title) {
          let resolved = await StreamProxyService.resolveMediaStream(id, title, vj);
          if (!resolved && title) {
            resolved = await StreamProxyService.resolveBunnyCdnStream(title, vj);
          }
          if (resolved && !StreamProxyService.isTrailerOrPromoUrl(resolved, title)) {
            targetUrl = resolved;
          }
        }
      }

      if (!targetUrl || targetUrl.includes('preview.php')) {
        res.status(404).send('Movie download stream could not be resolved or is currently unavailable.');
        return;
      }

      // 4. Reject promotional trailers / clips
      if (StreamProxyService.isTrailerOrPromoUrl(targetUrl, title)) {
        res.status(400).send('Trailer and promo downloads are strictly blocked.');
        return;
      }

      targetUrl = targetUrl.trim();
      if (!targetUrl.startsWith('http://') && !targetUrl.startsWith('https://')) {
        targetUrl = 'https://' + targetUrl.replace(/^\/+/, '');
      }

      // 5. Format clean, sanitized attachment filename
      let downloadFilename = filename ? filename.replace(/[<>:"/\\|?*]+/g, '').trim() : '';
      if (!downloadFilename) {
        const cleanTitle = (title || 'Munowatch_Movie').replace(/[<>:"/\\|?*]+/g, '').trim();
        const cleanVj = (vj || '').replace(/[<>:"/\\|?*]+/g, '').trim();
        const vjPart = cleanVj ? ` (${cleanVj})` : '';

        if (season && episode) {
          const sNum = String(season).padStart(2, '0');
          const eNum = String(episode).padStart(2, '0');
          downloadFilename = `${cleanTitle} - S${sNum}E${eNum}${vjPart} - Munowatch.mp4`;
        } else {
          downloadFilename = `${cleanTitle}${vjPart} - Munowatch.mp4`;
        }
      }

      if (!downloadFilename.toLowerCase().endsWith('.mp4') && !downloadFilename.toLowerCase().endsWith('.mkv')) {
        downloadFilename += '.mp4';
      }

      StreamProxyService.pipeStream(targetUrl, req, res, 0, downloadFilename);
    } catch (err: any) {
      if (!res.headersSent) {
        res.status(500).send(`Download failed: ${err.message}`);
      }
    }
  }
}
