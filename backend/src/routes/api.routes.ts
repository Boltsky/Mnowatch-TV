import { Router } from 'express';
import { CatalogController } from '../controllers/catalog.controller';
import { StreamController } from '../controllers/stream.controller';

const router = Router();

// Health check
router.get('/health', CatalogController.getHealth);

// Catalog endpoints
router.get('/catalog/home', CatalogController.getHome);
router.get('/catalog/vjs', CatalogController.getVjs);
router.get('/catalog/genres', CatalogController.getGenres);
router.get('/catalog/vj/:name', CatalogController.getByVj);
router.get('/catalog/genre/:name', CatalogController.getByGenre);
router.get('/catalog/movies', CatalogController.getMovies);
router.get('/catalog/series', CatalogController.getSeries);
router.get('/catalog/title/:id', CatalogController.getItem);
router.get('/catalog/search', CatalogController.search);

// Media Streaming Proxy with HTTP 206 Range support
router.get('/stream/proxy', StreamController.proxy);

export default router;
