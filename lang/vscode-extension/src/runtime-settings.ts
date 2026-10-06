import * as vscode from 'vscode';

/** One argument per element; a bounded tuning surface cannot replace the launcher or LSP transport. */
export function validateJvmOptions(raw: unknown): string[] {
    if (!Array.isArray(raw) || raw.some(value => typeof value !== 'string')) throw new Error('JVM options must be an array of strings.');
    const memory = /^(-Xms|-Xmx|-Xss|-XX:MaxMetaspaceSize=|-XX:ReservedCodeCacheSize=)([1-9][0-9]*)([kKmMgG])$/;
    const sizes = new Map<string, bigint>();
    const keys = raw.map((option: string) => {
        const match = memory.exec(option);
        if (match) {
            const bytes = BigInt(match[2]) * ({ k: 1024n, m: 1048576n, g: 1073741824n }[match[3].toLowerCase()]!);
            if (bytes > 9223372036854775807n) throw new Error('JVM memory size is too large.');
            sizes.set(match[1], bytes);
            return match[1];
        }
        if (/^-XX:\+Use(G1|Serial|Parallel|Z)GC$/.test(option)) return 'collector';
        if (/^-XX:ActiveProcessorCount=[1-9][0-9]*$/.test(option) && Number(option.split('=')[1]) <= 1024) return 'processors';
        throw new Error(`Unsupported JVM option: ${option}. Use heap/stack sizes, MaxMetaspaceSize, ReservedCodeCacheSize, ActiveProcessorCount or a supported collector.`);
    });
    if (new Set(keys).size !== keys.length) throw new Error('Duplicate or conflicting JVM options.');
    if (sizes.has('-Xms') && sizes.has('-Xmx') && sizes.get('-Xms')! > sizes.get('-Xmx')!) throw new Error('Initial heap must not exceed maximum heap.');
    return [...raw];
}

export function runtimeJvmOptions(): string[] {
    // Ignore workspace JSON even if written manually: runtime tuning is machine-local.
    return validateJvmOptions(vscode.workspace.getConfiguration('xtc').inspect('java.vmOptions')?.globalValue ?? []);
}


export interface LogRetention { historyDays: number; maxFileMb: number; totalSizeMb: number; retainedSessions: number }
export const defaultRetention: Readonly<LogRetention> = Object.freeze({ historyDays: 7, maxFileMb: 10, totalSizeMb: 50, retainedSessions: 5 });
export function validateLogRetention(raw: unknown): LogRetention {
    if (raw === undefined) return { ...defaultRetention };
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Log retention must be an object.');
    if (Object.keys(raw).some(key => !(key in defaultRetention))) throw new Error('Unknown log retention setting.');
    const value = { ...defaultRetention, ...raw };
    if (!Object.values(value).every(Number.isInteger) || value.historyDays < 1 || value.historyDays > 90 || value.maxFileMb < 1 || value.maxFileMb > 100 || value.totalSizeMb < value.maxFileMb || value.totalSizeMb > 1000 || value.retainedSessions < 1 || value.retainedSessions > 20) throw new Error('Log retention: days 1–90, file MB 1–100, archive MB at least file MB and at most 1000, retired sessions 1–20.');
    return value;
}
export function runtimeLogOptions(): LogRetention {
    return validateLogRetention(vscode.workspace.getConfiguration('xtc').inspect('server.logs')?.globalValue);
}
export function runtimeLogArguments(): string[] {
    return Object.entries(runtimeLogOptions()).map(([key, value]) => `-Dxtc.logs.${key}=${value}`);
}
