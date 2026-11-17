import express from 'express';
import cors from 'cors';
import apiRoutes from './routes/api.routes';

const app = express();

app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'HEAD'],
  allowedHeaders: ['Origin', 'X-Requested-With', 'Content-Type', 'Accept', 'Authorization', 'Range']
}));

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Mount API routes (Both /api/v1 and root / for universal client compatibility)
app.use('/api/v1', apiRoutes);
app.use('/', apiRoutes);

// Root fallback
app.get('/', (req, res) => {
  res.json({
    name: "Munowatch Web API",
    description: "High-Performance Stream Proxy & Ugandan Translated Movies Catalog",
    endpoints: {
      health: "/api/v1/health",
      homeCatalog: "/api/v1/catalog/home",
      vjs: "/api/v1/catalog/vjs",
      search: "/api/v1/catalog/search?q=:query",
      streamProxy: "/api/v1/stream/proxy?url=:streamUrl"
    }
  });
});

export default app;
