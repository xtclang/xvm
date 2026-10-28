import * as vscode from 'vscode';
import { runtimeJvmOptions, runtimeLogOptions } from './runtime-settings';

interface Setting { scope?: string; default?: unknown; deprecationMessage?: string }
interface Group { properties: Record<string, Setting> }

/** Also accepts the original single settings group for older packaged manifests. */
export function configurationProperties(groups: Group | Group[]): Record<string, Setting> {
    return Object.assign({}, ...[groups].flat().map(group => group.properties));
}

/** Report the scope actually read by the launcher, not an ignored folder or machine override. */
export function configuredSettings(properties: Record<string, Setting>, resource?: vscode.Uri): object {
    const window = vscode.workspace.getConfiguration('xtc');
    const document = vscode.workspace.getConfiguration('xtc', resource);
    return Object.fromEntries(Object.entries(properties).map(([name, definition]) => {
        const key = name.slice('xtc.'.length);
        const config = definition.scope === 'resource' ? document : window;
        const inspected = config.inspect(key);
        const folder = document.inspect(key)?.workspaceFolderValue;
        const machine = definition.scope === 'machine';
        const origin = machine ? (inspected?.globalValue !== undefined ? 'user' : 'default')
            : definition.scope === 'resource' && folder !== undefined ? 'folder'
            : inspected?.workspaceValue !== undefined ? 'workspace'
            : inspected?.globalValue !== undefined ? 'user' : 'default';
        const read = () => key === 'java.vmOptions' ? runtimeJvmOptions()
            : key === 'server.logs' ? runtimeLogOptions() : config.get(key, definition.default);
        const value = (() => { try { return { value: read() }; } catch (error) { return { invalid: String(error) }; } })();
        return [key, { ...value, origin, scope: definition.scope,
            ...(machine && inspected?.workspaceValue !== undefined ? { ignoredWorkspaceOverride: true } : {}),
            ...(definition.scope !== 'resource' && folder !== undefined ? { ignoredFolderOverride: true } : {}) }];
    }));
}
