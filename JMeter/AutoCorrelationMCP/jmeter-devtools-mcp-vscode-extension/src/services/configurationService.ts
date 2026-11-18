import * as fs from 'fs';
import * as path from 'path';

export class ConfigurationService {
    private configFilePath: string;
    private config: any;

    constructor() {
        this.configFilePath = path.join(__dirname, 'config.json');
        this.loadConfig();
    }

    private loadConfig() {
        if (fs.existsSync(this.configFilePath)) {
            const rawData = fs.readFileSync(this.configFilePath, 'utf-8');
            this.config = JSON.parse(rawData);
        } else {
            this.config = {};
        }
    }

    public getConfig(key: string): any {
        return this.config[key];
    }

    public setConfig(key: string, value: any) {
        this.config[key] = value;
        this.saveConfig();
    }

    private saveConfig() {
        fs.writeFileSync(this.configFilePath, JSON.stringify(this.config, null, 2));
    }
}