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

        res.json({
          success: true,
          directUrl: resolvedDirect,
          streamUrl: proxyUrl,
          servers: [
            {
              id: 'server-direct',
              name: 'Server 1 (Direct CDN Stream)',
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
          ]
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
}
