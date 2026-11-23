import app from './app';
import dotenv from 'dotenv';

dotenv.config();

process.on('uncaughtException', (err) => {
  console.error('Uncaught Exception caught safely:', err?.message || err);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

const PORT = process.env.PORT || 10000;

app.listen(PORT, () => {
  console.log(`🎬 Munowatch Web API & Stream Proxy listening on port ${PORT}`);
});
