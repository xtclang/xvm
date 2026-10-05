/** Import ownership and accepted report contents; no mutable parsed model escapes this owner. */
export class CompilerImport {
    private state: Readonly<{
        accepted?: string;
        observed?: Readonly<{ text?: string }>;
        operation?: Readonly<{ prepare: boolean }>;
        result?: ImportResult;
        retired?: boolean;
    }> = {};

    constructor(private readonly read: () => string | undefined, private readonly validate: (text: string) => unknown) { }

    current(): string | undefined {
        if (this.state.retired) return undefined;
        if (this.state.operation) return this.state.accepted;
        const text = this.read();
        if (this.state.observed && this.state.observed.text === text) return this.state.accepted;
        if (text !== undefined) this.validate(text);
        this.state = { ...this.state, accepted: text, observed: { text } };
        return text;
    }

    retained(): string | undefined { return this.state.accepted; }

    retire(): void { this.state = { retired: true }; }

    isRunning(operation: Readonly<{ prepare: boolean }>): boolean { return this.state.operation === operation; }

    begin(prepare: boolean): Readonly<{ prepare: boolean }> {
        if (this.state.retired) throw new Error('Compiler import owner has been retired.');
        if (this.state.operation) throw new Error('An Ecstasy compiler import is already running for this folder.');
        const operation = Object.freeze({ prepare });
        this.state = { ...this.state, operation };
        return operation;
    }

    finish(operation: Readonly<{ prepare: boolean }>, outcome: ImportResult['outcome'], detail?: string): ImportResult {
        if (this.state.retired) return { outcome: 'cancelled', message: 'Compiler import owner has been retired.', finished: new Date().toISOString() };
        if (this.state.operation !== operation) throw new Error('Compiler import no longer owns this result.');
        let observed = this.state.observed;
        let accepted = this.state.accepted;
        let result: ImportResult;
        try {
            const text = this.read();
            observed = { text };
            if (outcome === 'succeeded') {
                if (text === undefined) throw new Error('Gradle did not export an Ecstasy compiler model.');
                this.validate(text);
                accepted = text;
            }
            result = { outcome, message: outcome === 'succeeded' ? 'Ecstasy compiler inputs imported.' :
                outcome === 'cancelled' ? 'Import cancelled; previous compiler configuration retained.' :
                    detail ?? 'Gradle import failed; previous compiler configuration retained.', finished: new Date().toISOString() };
        } catch (error) {
            result = { outcome: outcome === 'cancelled' ? 'cancelled' : 'failed',
                message: outcome === 'cancelled' ? 'Import cancelled; previous compiler configuration retained.' :
                    `${error}; previous compiler configuration retained.`, finished: new Date().toISOString() };
        }
        this.state = { accepted, observed, result };
        return result;
    }

    description(): string {
        const { operation, result } = this.state;
        return operation ? (operation.prepare ? 'Preparing generated Ecstasy inputs…' : 'Refreshing evaluated Ecstasy compiler paths…') :
            result ? `${result.message}\nLast import: ${result.finished}` : 'No compiler import run in this editor session.';
    }
}

export interface ImportResult {
    readonly outcome: 'succeeded' | 'cancelled' | 'failed';
    readonly message: string;
    readonly finished: string;
}
