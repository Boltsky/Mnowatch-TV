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

  public static getHome(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const rails = CatalogService.getHomeRails(baseUrl);
    res.json({
      rails: rails,
      featured: rails[0]?.items[0] || null
    });
  }

  public static getVjs(req: Request, res: Response): void {
    res.json({ vjs: CatalogService.getVjs() });
  }

  public static getGenres(req: Request, res: Response): void {
    res.json({ genres: CatalogService.getGenres() });
  }

  public static getByVj(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const { name } = req.params;
    const items = CatalogService.getByVj(name, baseUrl);
    res.json({ vj: name, count: items.length, items });
  }

  public static getByGenre(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const { name } = req.params;
    const items = CatalogService.getByGenre(name, baseUrl);
    res.json({ genre: name, count: items.length, items });
  }

  public static getMovies(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const items = CatalogService.getAllMovies(baseUrl);
    res.json({ count: items.length, items });
  }

  public static getSeries(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const items = CatalogService.getAllSeries(baseUrl);
    res.json({ count: items.length, items });
  }

  public static getItem(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const { id } = req.params;
    const item = CatalogService.getItemById(id, baseUrl);
    if (!item) {
      res.status(404).json({ error: "Title not found" });
      return;
    }
    res.json(item);
  }

  public static search(req: Request, res: Response): void {
    const baseUrl = CatalogController.getBaseUrl(req);
    const query = req.query.q as string || '';
    const results = CatalogService.search(query, baseUrl);
    res.json({ query, count: results.length, items: results });
  }
}
