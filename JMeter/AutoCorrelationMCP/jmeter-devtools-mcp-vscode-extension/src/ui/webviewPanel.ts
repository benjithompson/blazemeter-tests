import * as vscode from 'vscode';

export class WebviewPanel {
    public static currentPanel: WebviewPanel | undefined;

    private readonly panel: vscode.WebviewPanel;
    private readonly extensionUri: vscode.Uri;

    private constructor(panel: vscode.WebviewPanel, extensionUri: vscode.Uri) {
        this.panel = panel;
        this.extensionUri = extensionUri;

        this.panel.onDidDispose(() => this.dispose(), null, context.subscriptions);
        this.panel.webview.onDidReceiveMessage(
            message => {
                switch (message.command) {
                    case 'someCommand':
                        this.handleSomeCommand(message);
                        return;
                }
            },
            null,
            context.subscriptions
        );

        this.update();
    }

    public static createOrShow(extensionUri: vscode.Uri) {
        const panel = vscode.window.createWebviewPanel(
            'jmeterDevtoolsMcp',
            'JMeter DevTools MCP',
            vscode.ViewColumn.One,
            {
                enableScripts: true,
                localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'media')]
            }
        );

        WebviewPanel.currentPanel = new WebviewPanel(panel, extensionUri);
    }

    private dispose() {
        WebviewPanel.currentPanel = undefined;
        this.panel.dispose();
    }

    private update() {
        this.panel.title = 'JMeter DevTools MCP';
        this.panel.webview.html = this.getHtmlForWebview(this.panel.webview);
    }

    private getHtmlForWebview(webview: vscode.Webview) {
        const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'media', 'script.js'));
        const styleUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'media', 'style.css'));

        return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link href="${styleUri}" rel="stylesheet">
            <title>JMeter DevTools MCP</title>
        </head>
        <body>
            <h1>Hello from JMeter DevTools MCP!</h1>
            <script src="${scriptUri}"></script>
        </body>
        </html>`;
    }

    private handleSomeCommand(message: any) {
        // Handle the command received from the webview
    }
}