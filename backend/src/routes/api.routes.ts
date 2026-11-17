import { Router } from 'express';
import { CatalogController } from '../controllers/catalog.controller';
import { StreamController } from '../controllers/stream.controller';

const router = Router();

// Health check
router.get('/health', CatalogController.getHealth);

// Dynamic multi-thousand catalog endpoints
router.get('/catalog/home', CatalogController.getHome);
router.get('/catalog/vjs', CatalogController.getVjs);
router.get('/catalog/genres', CatalogController.getGenres);
router.get('/catalog/movies', CatalogController.getMovies);
router.get('/catalog/series', CatalogController.getSeries);
router.get('/catalog/details/:id', CatalogController.getMovieDetails);
router.get('/catalog/title/:id', CatalogController.getMovieDetails);
router.get('/catalog/search', CatalogController.search);

// Media Streaming Proxy with HTTP 206 Range support
router.get('/stream/proxy', StreamController.proxy);

// Real-time on-demand stream resolver
router.get('/stream/resolve', StreamController.resolve);

export default router;
