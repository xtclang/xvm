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
