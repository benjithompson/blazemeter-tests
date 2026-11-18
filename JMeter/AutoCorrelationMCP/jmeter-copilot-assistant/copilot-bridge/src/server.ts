import express from 'express';
import bodyParser from 'body-parser';
import copilotRoutes from './routes/copilot';
import healthRoutes from './routes/health';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(bodyParser.json());
app.use('/api/copilot', copilotRoutes);
app.use('/api/health', healthRoutes);

app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});