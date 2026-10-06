import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export interface SourceAttachment { readonly module: string; readonly roots: readonly string[] }
export interface LibraryOptions { readonly modulePath: readonly string[] | null; readonly sourceAttachments: readonly SourceAttachment[] }
export const inheritedLibraries: LibraryOptions = Object.freeze({ modulePath: null, sourceAttachments: [] });

/** Window-owned library inputs. Relative paths need exactly one root; sources are navigation only. */
export function normalizeLibraries(raw: unknown, folders: readonly string[], checkFiles = false): LibraryOptions {
    if (raw === undefined || raw === null) return inheritedLibraries;
    if (typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Libraries must be an object.');
    const value = raw as Record<string, unknown>;
    if (Object.keys(value).some(key => !['modulePath', 'sourceAttachments'].includes(key))) throw new Error('Unknown library setting.');
    const paths = (input: unknown, sources: boolean): string[] => {
        if (!Array.isArray(input) || input.some(item => typeof item !== 'string' || !item.trim())) throw new Error('Library paths must be non-blank strings.');
        const result = input.map((text: string) => {
            const uri = /^[a-z][a-z\d+.-]*:/i.test(text) ? new URL(text) : (() => {
                if (folders.length !== 1) throw new Error('Relative library paths require exactly one workspace folder.');
                return new URL(text, folders[0].replace(/\/?$/, '/'));
            })();
            if (uri.protocol !== 'file:' || uri.search || uri.hash) throw new Error('Library paths must use local file URIs.');
            const file = path.resolve(fileURLToPath(uri));
            const canonical = fs.existsSync(file) ? fs.realpathSync(file) : file;
            if (checkFiles) {
                const stat = fs.statSync(canonical, { throwIfNoEntry: false });
                if (!stat || (sources ? !stat.isDirectory() : !stat.isDirectory() && !(stat.isFile() && canonical.endsWith('.xtc')))) {
                    throw new Error(`${sources ? 'Source directory' : 'Library file or directory'} does not exist: ${text}`);
                }
            }
            return pathToFileURL(canonical).toString();
        });
        if (new Set(result).size !== result.length) throw new Error('Duplicate library path.');
        return result;
    };
    if (value.sourceAttachments !== undefined && !Array.isArray(value.sourceAttachments)) throw new Error('sourceAttachments must be an array.');
    const attachments = (value.sourceAttachments as unknown[] | undefined ?? []).map(raw => {
        const attachment = raw as Partial<SourceAttachment> | null;
        if (!attachment || typeof attachment.module !== 'string' || !attachment.module.trim()) throw new Error('Source attachment needs a module name.');
        const roots = paths(attachment.roots, true);
        if (!roots.length) throw new Error('Source attachment needs ordered roots.');
        return { module: attachment.module, roots };
    });
    if (new Set(attachments.map(item => item.module)).size !== attachments.length) throw new Error('Duplicate source attachment module.');
    return { modulePath: value.modulePath == null ? null : paths(value.modulePath, false), sourceAttachments: attachments };
}
