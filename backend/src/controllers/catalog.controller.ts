import { Request, Response } from 'express';
import { CatalogService } from '../services/catalog.service';

export class CatalogController {
  private static getBaseUrl(req: Request): string {
    const protocol = req.headers['x-forwarded-proto'] || req.protocol;
    const host = req.headers['x-forwarded-host'] || req.get('host');
    return `${protocol}://${host}`;
  }

  public static getHealth(req: Request, res: Response): void {
    res.json({
      status: "ok",
      service: "Munowatch Web API & Stream Engine",
      version: "1.0.0",
      timestamp: new Date().toISOString(),
      platforms: ["web", "mobile", "smart-tv"]
    });
  }

  public static async getHome(req: Request, res: Response): Promise<void> {
    try {
      const baseUrl = CatalogController.getBaseUrl(req);
      const rails = await CatalogService.getHomeRails(baseUrl);
      res.json({
        rails: rails,
        featured: rails[0]?.items[0] || null
      });
    } catch (e: any) {
      res.status(500).json({ error: "Failed to load home rails", message: e.message });
    }
  }

  public static async getVjs(req: Request, res: Response): Promise<void> {
    try {
      const vjs = await CatalogService.getVjs();
      res.json({ vjs });
    } catch {
      res.json({ vjs: CatalogService.getDefaultVjs() });
    }
  }

  public static getGenres(req: Request, res: Response): void {
    res.json({ genres: CatalogService.getGenres() });
  }

  public static async getMovies(req: Request, res: Response): Promise<void> {
    try {
      const baseUrl = CatalogController.getBaseUrl(req);
      const page = parseInt(req.query.page as string) || 1;
      const perPage = parseInt(req.query.perPage as string) || 24;
      const year = req.query.year as string;
      const vj = req.query.vj as string;
      const genre = req.query.genre as string;
      const sort = req.query.sort as string;

      const result = await CatalogService.getMovies(
        { page, perPage, year, vj, genre, sort },
        baseUrl
      );
      res.json(result);
    } catch (e: any) {
      res.status(500).json({ error: "Failed to fetch movies", message: e.message });
    }
  }

  public static async getSeries(req: Request, res: Response): Promise<void> {
    try {
      const baseUrl = CatalogController.getBaseUrl(req);
      const page = parseInt(req.query.page as string) || 1;
      const perPage = parseInt(req.query.perPage as string) || 24;
      const vj = req.query.vj as string;
      const genre = req.query.genre as string;

      const result = await CatalogService.getSeries(
        { page, perPage, vj, genre },
        baseUrl
      );
      res.json(result);
    } catch (e: any) {
      res.status(500).json({ error: "Failed to fetch series", message: e.message });
    }
  }

  public static async search(req: Request, res: Response): Promise<void> {
    try {
      const baseUrl = CatalogController.getBaseUrl(req);
      const query = (req.query.q as string) || '';
      const results = await CatalogService.search(query, baseUrl);
      res.json({ query, count: results.length, items: results });
    } catch (e: any) {
      res.status(500).json({ error: "Search failed", message: e.message });
    }
  }

  public static async getMovieDetails(req: Request, res: Response): Promise<void> {
    try {
      const baseUrl = CatalogController.getBaseUrl(req);
      const id = req.params.id;
      const title = (req.query.title as string) || '';
      const item = await CatalogService.getMovieDetails(id, title, baseUrl);
      if (!item) {
        res.status(404).json({ error: "Movie details not found", id });
        return;
      }
      res.json(item);
    } catch (e: any) {
      res.status(500).json({ error: "Failed to fetch movie details", message: e.message });
    }
  }
}
