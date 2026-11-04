/**
 * The events of a test run, which xunit's `json` report (`xvm.xunit.report=json`) prints in the test process's output,
 * a line each, the marker and then the event as a JSON object, and which the language server reads from a run's output
 * and sends apart from it: `xtc/testEvent` for a run, the DAP event `xtcTest` for a debug session. xunit's
 * `JsonExecutionListener` defines them; a reader passes over the events and fields it does not know.
 */
export interface TestEvent {
    /** `run`, first, with the format's version; `started`; `finished`; or `skipped`, a test or container that did not run. */
    readonly event: string;
    readonly version?: number;
    /** The test's or container's ID in the run; `parent` is its container's, absent for the root. */
    readonly id?: string;
    readonly parent?: string;
    readonly name?: string;
    /** The xunit selector that runs it alone, as `xtc test`'s `-p`, `-c` and `-t` take it; empty for the module. */
    readonly selector?: string;
    readonly container?: boolean;
    /** A finished one's `passed`, `failed`, `aborted` (an exception other than a failed assertion) or `skipped`. */
    readonly status?: string;
    /** A failure's message, or why one was skipped. */
    readonly message?: string;
    /** A failure's exception, with its stack. */
    readonly details?: string;
    readonly durationMillis?: number;
}

/** How the line of each event starts, ahead of its JSON object. */
export const MARKER = '##xunit ';

/** The report that prints the events: `testXtc`'s `--test-report`, the injection `xvm.xunit.report`. */
export const REPORT = 'json';

/** The event that [line], a line of a test process's output, is, or undefined for any other output. */
export function parseTestEvent(line: string): TestEvent | undefined {
    const text = line.replace(/\r?\n$/, '');
    if (!text.startsWith(MARKER)) {
        return undefined;
    }
    try {
        const value: unknown = JSON.parse(text.slice(MARKER.length));
        return isTestEvent(value) ? value : undefined;
    } catch {
        return undefined;
    }
}

/** Whether [value] is an event's object: an object with the event's name. */
export function isTestEvent(value: unknown): value is TestEvent {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
        && typeof (value as { event?: unknown }).event === 'string';
}

/**
 * The line a failure's details give for the source file [file], from a stack frame such as
 * `at fails() (Mathy.x:10)`, one-based; undefined when no frame is in that file.
 */
export function failureLine(details: string, file: string): number | undefined {
    for (const [, name, line] of details.matchAll(/\(([^()\s:]+\.x):(\d+)\)/g)) {
        if (name === file) {
            return Number(line);
        }
    }
    return undefined;
}

/** Splits text that arrives in pieces into whole lines; what is left at the end is the last line. */
export class LineSplitter {
    private rest = '';

    feed(text: string): string[] {
        const lines = (this.rest + text).split(/\r?\n/);
        this.rest = lines.pop() ?? '';
        return lines;
    }

    flush(): string[] {
        const last = this.rest;
        this.rest = '';
        return last ? [last] : [];
    }
}
