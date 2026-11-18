import express from 'express';
import { CopilotBridgeClient } from '../bridge/CopilotBridgeClient';

const router = express.Router();
const copilotClient = new CopilotBridgeClient();

// Route to handle requests for AI assistance
router.post('/assist', async (req, res) => {
    try {
        const { prompt } = req.body;
        const response = await copilotClient.requestAssistance(prompt);
        res.json(response);
    } catch (error) {
        res.status(500).json({ error: 'Failed to get assistance from Copilot' });
    }
});

// Route to handle test plan retrieval
router.get('/test-plans', async (req, res) => {
    try {
        const testPlans = await copilotClient.getTestPlans();
        res.json(testPlans);
    } catch (error) {
        res.status(500).json({ error: 'Failed to retrieve test plans' });
    }
});

// Route to handle test results retrieval
router.get('/results/:testId', async (req, res) => {
    const { testId } = req.params;
    try {
        const results = await copilotClient.getTestResults(testId);
        res.json(results);
    } catch (error) {
        res.status(500).json({ error: 'Failed to retrieve test results' });
    }
});

// Route to handle parameterization and extraction of JSON headers
router.post('/extract-json', (req, res) => {
    const { jsonData } = req.body;
    try {
        const extractedHeaders = copilotClient.extractJsonHeaders(jsonData);
        res.json(extractedHeaders);
    } catch (error) {
        res.status(500).json({ error: 'Failed to extract JSON headers' });
    }
});

// Route to verify test outcomes
router.post('/verify-outcomes', (req, res) => {
    const { expected, actual } = req.body;
    try {
        const verificationResult = copilotClient.verifyOutcomes(expected, actual);
        res.json(verificationResult);
    } catch (error) {
        res.status(500).json({ error: 'Failed to verify test outcomes' });
    }
});

export default router;