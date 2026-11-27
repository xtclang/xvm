import { PlaybookFinal } from '../wait';
import { catalog } from './shared';

/**
 * A host bug that doc/errs/errs-upstream-issues.md registers as [up], with which a case fails while the host has it:
 * [symptom] says whether a case's failure is exactly the bug's, and [always] whether the case meets the bug in every
 * run, so that a pass says the host may be fixed.
 */
interface KnownHostFailure {
    readonly up: string;
    readonly bug: string;
    readonly always: boolean;
    readonly symptom: (failure: unknown) => boolean;
}

/** The message of [failure], or what it is. */
function message(failure: unknown): string {
    return failure instanceof Error ? failure.message : String(failure);
}

/**
 * Whether [failure] is the files of X218's Undo as UP23 leaves them, final: VS Code moved the child, Box.x, back first,
 * and then failed to move its parent folder back, as the folder it would move it to exists, so the parent's other
 * files, Helper.x and Box's companions, are in neither place it reads. An Undo that did not take at all, or one that
 * restored the folders but not an edit, fails as itself.
 */
function up23(failure: unknown): boolean {
    if (!(failure instanceof PlaybookFinal) || !message(failure).startsWith('Move undone: all sources and resources agree')) return false;
    const texts = failure.value;
    const files = catalog.cases.X218.values.files;
    if (!Array.isArray(texts) || texts.length !== files.length) return false;
    const at = (file: string) => texts[files.findIndex(each => each.file === file)];
    const box = files.find(each => each.file === 'App/tools/Box.x')!;
    return at(box.file) === box.source && ['App/tools/Helper.x', 'App/tools/Box/Part.x', 'App/tools/Box/data.txt'].every(file => at(file) === '');
}

/**
 * The known host failures of VS Code's playbook, by their cases. A case that fails with its symptom reports
 * "known host failure", apart from both the passed and the failed cases, and leaves the run green; any other failure
 * of the case fails it; and a case that always meets the bug, and passes, fails as the bug's being fixed. None is a pass.
 */
const KNOWN_HOST_FAILURES: Readonly<Record<string, KnownHostFailure>> = {
    // TODO VSCODE: UP23 — VS Code undoes overlapping parent and child moves in forward order, which fails with "target
    // already exists": the child is back, the parent is not, at the check of X218's Undo
    X218: {
        up: 'UP23', always: true,
        bug: 'VS Code undoes overlapping parent and child moves in their forward order, which fails with "target already exists"',
        symptom: up23,
    },
    // TODO VSCODE: UP16 — Paste repaints the Cut tree nodes that an Explorer refresh retired and throws "Data tree node
    // not found" from itemsCopied, after Move, Undo, Redo and the resources passed; it needs a refresh during the Paste
    X130: {
        up: 'UP16', always: false,
        bug: "VS Code's Paste repaints Cut tree nodes that an Explorer refresh retired, and throws from itemsCopied",
        symptom: failure => message(failure).startsWith('X130 FAILED: VS Code threw while clearing Cut highlighting')
            && message(failure).includes('Data tree node not found'),
    },
};

/** The known host failure that [failure], the failure of the case [id], is, or undefined when it is any other. */
export function knownHostFailure(id: string, failure: unknown): { up: string; bug: string } | undefined {
    const known = KNOWN_HOST_FAILURES[id];
    return known?.symptom(failure) ? { up: known.up, bug: known.bug } : undefined;
}

/**
 * The failure of the case [id], which passed, when it always meets a known host bug: the host seems fixed, and the
 * exception and the register's row are to go; undefined for any other case.
 */
export function seemsFixed(id: string): Error | undefined {
    const known = KNOWN_HOST_FAILURES[id];
    return known?.always
        ? new Error(`${known.up} seems fixed: ${id} passed, where ${known.bug}; remove its known host failure and close the register's ${known.up} row`)
        : undefined;
}
