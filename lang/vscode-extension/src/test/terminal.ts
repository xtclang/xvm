/**
 * [text], a terminal's, without the clock of Gradle's progress bar, as `<===--> 75% EXECUTING [1m 3s]`, which its rich
 * console counts every second while a task runs, also one that waits for input or hangs: what a task prints, which the
 * harness's waits count as under way while it changes.
 */
export function withoutClock(text: string): string {
    return text.replace(/(<[=-]*> *\d+% [A-Z]+) \[[^\]\n]*\]/g, '$1');
}
