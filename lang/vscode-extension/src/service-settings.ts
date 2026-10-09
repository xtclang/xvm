/** Connection preferences are window scoped; formatting and presentation use their native scope. */
export interface ServiceSettings {
    readonly adapter: 'default' | 'compiler' | 'treesitter';
    readonly textSynchronization: 'full' | 'incremental';
    readonly saveFormatting: 'editor' | 'server';
    readonly inlayHints: boolean;
}

export function parseServiceSettings(raw: Record<string, unknown>): ServiceSettings {
    const adapter = raw.adapter === undefined ? 'default' : raw.adapter;
    const textSynchronization = raw.textSynchronization === undefined ? 'full' : raw.textSynchronization;
    const saveFormatting = raw.saveFormatting === undefined ? 'editor' : raw.saveFormatting;
    const inlayHints = raw.inlayHints === undefined ? true : raw.inlayHints;
    if (adapter !== 'default' && adapter !== 'compiler' && adapter !== 'treesitter') {
        throw new Error('Language adapter must be default, compiler or treesitter');
    }
    if (textSynchronization !== 'full' && textSynchronization !== 'incremental') {
        throw new Error('Text synchronization must be full or incremental');
    }
    if (saveFormatting !== 'editor' && saveFormatting !== 'server') {
        throw new Error('Save formatting must be owned by the editor or server');
    }
    if (typeof inlayHints !== 'boolean') {
        throw new Error('Inlay hints must be a boolean');
    }
    return Object.freeze({ adapter, textSynchronization, saveFormatting, inlayHints });
}

/** Native format-on-save wins: never run two formatters for a save. */
export function synchronizationOptions(settings: ServiceSettings, nativeFormatOnSave: boolean): object {
    return {
        incremental: settings.textSynchronization === 'incremental',
        formatOnSave: settings.saveFormatting === 'server' && !nativeFormatOnSave,
    };
}
