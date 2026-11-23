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
      let { fhdStream, saverStream } = await StreamProxyService.resolveAllQualityStreams(id, title, vj);

      if (!fhdStream && !saverStream) {
        const legacy = await StreamProxyService.resolveMediaStream(id, title, vj);
        if (legacy && !StreamProxyService.isTrailerOrPromoUrl(legacy, title)) {
          if (legacy.includes('b-cdn.net')) {
            fhdStream = legacy;
          } else {
            saverStream = legacy;
          }
        }
      }

      if (fhdStream || saverStream) {
        const protocol = req.headers['x-forwarded-proto'] || req.protocol;
        const host = req.headers['x-forwarded-host'] || req.get('host');
        const baseUrl = `${protocol}://${host}`;

        // Concurrently probe file sizes for both quality streams
        const [fhdProbe, saverProbe] = await Promise.all([
          fhdStream ? StreamProxyService.probeFileSize(fhdStream) : null,
          saverStream ? StreamProxyService.probeFileSize(saverStream) : null
        ]);

        const qualities: Array<{
          id: 'fhd' | 'saver';
          label: string;
          name: string;
          quality: string;
          fileSize?: number;
          fileSizeFormatted: string;
          directUrl: string;
          streamUrl: string;
        }> = [];

        const servers: any[] = [];

        if (fhdStream) {
          const fhdProxy = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(fhdStream)}${title ? `&title=${encodeURIComponent(title)}` : ''}`;
          const fhdSize = fhdProbe?.formatted || '1.6 GB';
          qualities.push({
            id: 'fhd',
            label: '1080p Full HD',
            name: '1080p Full HD (Master)',
            quality: '1080p Full HD',
            fileSize: fhdProbe?.sizeBytes,
            fileSizeFormatted: fhdSize,
            directUrl: fhdStream,
            streamUrl: fhdProxy
          });
          servers.push({
            id: 'server-fhd-direct',
            name: '1080p Full HD (Direct CDN)',
            quality: `1080p Full HD (${fhdSize})`,
            tier: 'fhd',
            fileSize: fhdProbe?.sizeBytes,
            fileSizeFormatted: fhdSize,
            streamUrl: fhdStream,
            isProxy: false
          });
          servers.push({
            id: 'server-fhd-proxy',
            name: '1080p Full HD (CORS Safe)',
            quality: `1080p Full HD (${fhdSize})`,
            tier: 'fhd',
            fileSize: fhdProbe?.sizeBytes,
            fileSizeFormatted: fhdSize,
            streamUrl: fhdProxy,
            isProxy: true
          });
        }

        if (saverStream) {
          const saverProxy = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(saverStream)}${title ? `&title=${encodeURIComponent(title)}` : ''}`;
          const saverSize = saverProbe?.formatted || (id.includes('_s') || id.includes('series') ? '350 MB' : '286 MB');
          qualities.push({
            id: 'saver',
            label: 'Data Saver',
            name: 'Data Saver (Mobile Compressed)',
            quality: '720p / Data Saver',
            fileSize: saverProbe?.sizeBytes,
            fileSizeFormatted: saverSize,
            directUrl: saverStream,
            streamUrl: saverProxy
          });
          servers.push({
            id: 'server-saver-direct',
            name: 'Data Saver (Direct CDN)',
            quality: `Data Saver (${saverSize})`,
            tier: 'saver',
            fileSize: saverProbe?.sizeBytes,
            fileSizeFormatted: saverSize,
            streamUrl: saverStream,
            isProxy: false
          });
          servers.push({
            id: 'server-saver-proxy',
            name: 'Data Saver (CORS Safe)',
            quality: `Data Saver (${saverSize})`,
            tier: 'saver',
            fileSize: saverProbe?.sizeBytes,
            fileSizeFormatted: saverSize,
            streamUrl: saverProxy,
            isProxy: true
          });
        }

        // Default: prefer 1080p Full HD if available, else Data Saver
        const primaryQuality = qualities.find(q => q.id === 'fhd') || qualities[0];

        res.json({
          success: true,
          directUrl: primaryQuality.directUrl,
          streamUrl: primaryQuality.streamUrl,
          fileSize: primaryQuality.fileSize,
          fileSizeFormatted: primaryQuality.fileSizeFormatted,
          qualities,
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
    const quality = (req.query.quality as string) || '';

    try {
      // 1. Recursively unwrap and decode proxy URLs to extract the raw underlying target
      let prevUrl = '';
      while (targetUrl !== prevUrl) {
        prevUrl = targetUrl;
        if (targetUrl.includes('/stream/proxy?url=')) {
          const idx = targetUrl.indexOf('/stream/proxy?url=');
          targetUrl = targetUrl.substring(idx + 18);
        }
        try {
          const decoded = decodeURIComponent(targetUrl);
          if (decoded !== targetUrl) {
            targetUrl = decoded;
          }
        } catch {}
      }

      let resolvedId = id;
      // 2. Auto-resolve Twolekede preview HTML pages to genuine media stream URLs
      if (targetUrl.includes('preview.php') || targetUrl.includes('twolekede.com/preview')) {
        const match = targetUrl.match(/(?:preview\.php\?id=|[?&]id=)(\d+)/);
        if (match) {
          if (!resolvedId) resolvedId = `tk_${match[1]}`;
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

      // 3. If stream is not direct or empty, resolve fresh genuine direct CDN stream with quality preference
      if (!targetUrl || (!targetUrl.includes('.mp4') && !targetUrl.includes('.mkv') && !targetUrl.includes('secure.php') && !targetUrl.includes('b-cdn.net'))) {
        if (resolvedId || title) {
          const { fhdStream, saverStream } = await StreamProxyService.resolveAllQualityStreams(resolvedId, title, vj);
          if (quality === 'saver') {
            targetUrl = saverStream || fhdStream || '';
          } else if (quality === 'fhd') {
            targetUrl = fhdStream || saverStream || '';
          } else {
            targetUrl = fhdStream || saverStream || '';
          }

          if (!targetUrl) {
            let resolved = await StreamProxyService.resolveMediaStream(resolvedId, title, vj);
            if (!resolved && title) {
              resolved = await StreamProxyService.resolveBunnyCdnStream(title, vj);
            }
            if (resolved && !StreamProxyService.isTrailerOrPromoUrl(resolved, title)) {
              targetUrl = resolved;
            }
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
          downloadFilename = `${cleanTitle} - S${sNum}E${eNum}${vjPart} - Bolt.mp4`;
        } else {
          downloadFilename = `${cleanTitle}${vjPart} - Bolt.mp4`;
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
