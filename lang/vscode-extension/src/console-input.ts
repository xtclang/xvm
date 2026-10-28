/** What one chunk of keystrokes typed into a run's terminal means for the program's console input. */
export interface ConsoleInputStep {
    /** Text to write back to the terminal, which does not echo by itself. */
    echo: string;
    /** Completed lines, each ending in a line feed, for the program. */
    lines: string[];
    /** Ctrl+D on an empty line: the end of input. */
    end: boolean;
    /** Ctrl+C: stop the program. */
    interrupt: boolean;
}

/**
 * Line editing for a run's pseudoterminal, which delivers raw keystrokes: printable text is echoed and
 * collected, Backspace removes the last character, Enter completes the line, and cursor keys and other
 * escape sequences are ignored. Pasted text is handled the same way, one character at a time.
 */
export class ConsoleLineEditor {
    private line: string[] = [];
    private escape: 'none' | 'start' | 'csi' | 'ss3' = 'none';
    private afterCarriageReturn = false;

    feed(data: string): ConsoleInputStep {
        const step: ConsoleInputStep = { echo: '', lines: [], end: false, interrupt: false };
        // iterating a string visits code points, so a surrogate pair is one character
        for (const ch of data) {
            if (this.skipEscape(ch)) {
                continue;
            }
            // a pasted CRLF is one line end
            const lineFeedAfterReturn = this.afterCarriageReturn && ch === '\n';
            this.afterCarriageReturn = ch === '\r';
            if (lineFeedAfterReturn) {
                continue;
            }
            switch (ch) {
                case '\r':
                case '\n':
                    step.lines.push(`${this.line.join('')}\n`);
                    this.line = [];
                    step.echo += '\r\n';
                    break;
                case '\x7f':
                case '\b':
                    if (this.line.pop() !== undefined) {
                        step.echo += '\b \b';
                    }
                    break;
                case '\x04':
                    if (this.line.length === 0) {
                        step.end = true;
                    }
                    break;
                case '\x03':
                    step.interrupt = true;
                    break;
                case '\x1b':
                    this.escape = 'start';
                    break;
                default:
                    if (ch >= ' ') {
                        this.line.push(ch);
                        step.echo += ch;
                    }
            }
        }
        return step;
    }

    /** Consumes an escape sequence: ESC [ parameters final-byte, or ESC O key. */
    private skipEscape(ch: string): boolean {
        switch (this.escape) {
            case 'start':
                this.escape = ch === '[' ? 'csi' : ch === 'O' ? 'ss3' : 'none';
                return true;
            case 'csi':
                if (ch >= '@' && ch <= '~') {
                    this.escape = 'none';
                }
                return true;
            case 'ss3':
                this.escape = 'none';
                return true;
            default:
                return false;
        }
    }
}
