import * as vscode from 'vscode';
import { registerCommands } from './commands/index';
import { JMeterService } from './services/jmeterService';
import { DevToolsMcpService } from './services/devtoolsMcpService';
import { ConfigurationService } from './services/configurationService';

export function activate(context: vscode.ExtensionContext) {
    const jmeterService = new JMeterService();
    const devToolsMcpService = new DevToolsMcpService();
    const configurationService = new ConfigurationService();

    registerCommands(context, jmeterService, devToolsMcpService, configurationService);

    // Additional setup can be done here, such as event listeners
}

export function deactivate() {
    // Clean up resources if necessary
}