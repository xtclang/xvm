/**
 * TeamCity service messages, as xunit prints a test run's progress for an editor (`xvm.xunit.report=teamcity`):
 * one per line, `##teamcity[testStarted name='adds()' nodeId='...' locationHint='xunit://adds']`, each attribute value
 * quoted with `'`, and `|` escaping the quote, itself, the line ends and the square brackets.
 */
export interface ServiceMessage {
    readonly name: string;
    readonly attributes: Readonly<Record<string, string>>;
}

const PREFIX = '##teamcity[';

/** What each `|` escape stands for. */
const ESCAPES: Readonly<Record<string, string>> = { '\'': '\'', '|': '|', n: '\n', r: '\r', '[': '[', ']': ']' };

/** The service message that [line] is, or undefined for any other output. */
export function parseServiceMessage(line: string): ServiceMessage | undefined {
    const text = line.trim();
    if (!text.startsWith(PREFIX) || !text.endsWith(']')) {
        return undefined;
    }
    const body = text.slice(PREFIX.length, -1);
    const name = /^[\w.-]+/.exec(body)?.[0];
    if (!name) {
        return undefined;
    }
    const attributes = Object.fromEntries(
        [...body.slice(name.length).matchAll(/\s*([\w.-]+)='((?:\|.|[^|'])*)'/g)]
            .map(([, key, value]) => [key, value.replace(/\|(.)/g, (_, escaped: string) => ESCAPES[escaped] ?? escaped)]));
    return { name, attributes };
}

/** The prefix of a `locationHint`, `protocol://path`, whose path is an xunit selector. */
const XUNIT = 'xunit://';

/** The xunit selector a message's `locationHint` names, such as `tests.MathTest.adds`, or undefined. */
export function selectorOf(message: ServiceMessage): string | undefined {
    const hint = message.attributes.locationHint;
    return hint?.startsWith(XUNIT) ? hint.slice(XUNIT.length) : undefined;
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
