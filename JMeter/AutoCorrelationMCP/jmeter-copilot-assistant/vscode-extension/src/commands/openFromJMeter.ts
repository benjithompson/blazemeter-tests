import * as vscode from 'vscode';
import { TestPlanRepository } from '../../../plugin/src/main/java/com/example/jmeter/copilot/core/TestPlanRepository';
import { ResultsRepository } from '../../../plugin/src/main/java/com/example/jmeter/copilot/core/ResultsRepository';

export function openFromJMeter(context: vscode.ExtensionContext) {
    const testPlanRepo = new TestPlanRepository();
    const resultsRepo = new ResultsRepository();

    vscode.window.showOpenDialog({
        canSelectMany: false,
        openLabel: 'Open JMeter Test Plan',
        filters: {
            'JMeter Files': ['jmx'],
        },
    }).then(fileUri => {
        if (fileUri && fileUri[0]) {
            const testPlanPath = fileUri[0].fsPath;
            testPlanRepo.loadTestPlan(testPlanPath)
                .then(testPlan => {
                    vscode.window.showInformationMessage(`Loaded test plan: ${testPlan.name}`);
                    // Additional logic to display the test plan in the GUI can be added here
                })
                .catch(err => {
                    vscode.window.showErrorMessage(`Failed to load test plan: ${err.message}`);
                });
        }
    });

    // Logic to retrieve and display results can be added here
    resultsRepo.getResults()
        .then(results => {
            // Display results in the GUI
        })
        .catch(err => {
            vscode.window.showErrorMessage(`Failed to retrieve results: ${err.message}`);
        });
}