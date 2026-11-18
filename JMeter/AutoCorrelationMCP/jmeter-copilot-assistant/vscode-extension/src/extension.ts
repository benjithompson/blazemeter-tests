import * as vscode from 'vscode';
import { provideAssistance } from './commands/provideAssistance';
import { openFromJMeter } from './commands/openFromJMeter';

export function activate(context: vscode.ExtensionContext) {
    const provideAssistanceCommand = vscode.commands.registerCommand('jmeterCopilot.provideAssistance', provideAssistance);
    const openFromJMeterCommand = vscode.commands.registerCommand('jmeterCopilot.openFromJMeter', openFromJMeter);

    context.subscriptions.push(provideAssistanceCommand);
    context.subscriptions.push(openFromJMeterCommand);
}

export function deactivate() {}