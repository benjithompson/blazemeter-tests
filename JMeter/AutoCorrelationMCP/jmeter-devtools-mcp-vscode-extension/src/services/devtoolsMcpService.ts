import { DevToolsContext } from '../types';
import { EventEmitter } from 'events';

export class DevToolsMcpService {
    private context: DevToolsContext | null = null;
    private eventEmitter: EventEmitter;

    constructor() {
        this.eventEmitter = new EventEmitter();
    }

    public setContext(context: DevToolsContext): void {
        this.context = context;
        this.eventEmitter.emit('contextUpdated', context);
    }

    public getContext(): DevToolsContext | null {
        return this.context;
    }

    public onContextUpdated(listener: (context: DevToolsContext) => void): void {
        this.eventEmitter.on('contextUpdated', listener);
    }

    public clearContext(): void {
        this.context = null;
        this.eventEmitter.emit('contextCleared');
    }
}