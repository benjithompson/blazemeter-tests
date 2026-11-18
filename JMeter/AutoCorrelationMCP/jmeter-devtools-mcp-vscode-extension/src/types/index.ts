export interface JMeterTestConfig {
    testName: string;
    testScript: string;
    parameters: Record<string, any>;
    timeout: number;
}

export interface DevToolsContext {
    sessionId: string;
    targetId: string;
    isConnected: boolean;
}

export interface ExtensionConfig {
    jmeterPath: string;
    devToolsUrl: string;
    autoStartTests: boolean;
}