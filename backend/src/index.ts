import app from './app';
import dotenv from 'dotenv';

dotenv.config();

const PORT = process.env.PORT || 10000;

app.listen(PORT, () => {
  console.log(`🎬 Munowatch Web API & Stream Proxy listening on port ${PORT}`);
});
