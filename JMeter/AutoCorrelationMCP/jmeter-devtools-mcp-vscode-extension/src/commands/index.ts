import { commands, window } from 'vscode';
import { JMeterService } from '../services/jmeterService';
import { DevToolsMcpService } from '../services/devtoolsMcpService';
import { ConfigurationService } from '../services/configurationService';

const jmeterService = new JMeterService();
const devToolsMcpService = new DevToolsMcpService();
const configurationService = new ConfigurationService();

export function registerCommands() {
    commands.registerCommand('jmeter.startTest', async () => {
        const result = await jmeterService.startTest();
        window.showInformationMessage(result);
    });

    commands.registerCommand('jmeter.stopTest', async () => {
        const result = await jmeterService.stopTest();
        window.showInformationMessage(result);
    });

    commands.registerCommand('jmeter.getResults', async () => {
        const results = await jmeterService.getResults();
        window.showInformationMessage(`Test Results: ${results}`);
    });

    commands.registerCommand('devtoolsMcp.ensureContext', async () => {
        const context = await devToolsMcpService.ensureContext();
        window.showInformationMessage(`DevTools Context: ${context}`);
    });

    commands.registerCommand('configuration.load', async () => {
        const config = await configurationService.load();
        window.showInformationMessage(`Configuration Loaded: ${JSON.stringify(config)}`);
    });

    commands.registerCommand('configuration.save', async () => {
        await configurationService.save();
        window.showInformationMessage('Configuration Saved');
    });
}