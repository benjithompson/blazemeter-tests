import * as vscode from 'vscode';
import { CopilotBridgeClient } from '../../copilot-bridge/src/bridge/CopilotBridgeClient';

export function provideAssistance(context: vscode.ExtensionContext) {
    const client = new CopilotBridgeClient();

    const disposable = vscode.commands.registerCommand('jmeter-copilot.assist', async () => {
        const testPlan = await vscode.window.showInputBox({ prompt: 'Enter the JMeter test plan name' });
        if (!testPlan) {
            vscode.window.showErrorMessage('Test plan name is required.');
            return;
        }

        const assistance = await client.requestAssistance(testPlan);
        if (assistance) {
            vscode.window.showInformationMessage(`Assistance provided: ${assistance}`);
        } else {
            vscode.window.showErrorMessage('Failed to retrieve assistance from GitHub Copilot.');
        }
    });

    context.subscriptions.push(disposable);
}