import * as vscode from 'vscode';
import { parseServiceSettings, ServiceSettings } from './service-settings';

export function readServiceSettings(resource?: vscode.Uri): ServiceSettings {
    const config = vscode.workspace.getConfiguration('xtc', resource);
    return parseServiceSettings({
        textSynchronization: config.get('languageService.textSynchronization'),
        saveFormatting: config.get('languageService.saveFormatting'),
        inlayHints: config.get('inlayHints.enabled'),
    });
}

export function nativeFormatOnSave(document: vscode.TextDocument): boolean {
    return vscode.workspace.getConfiguration('editor', { uri: document.uri, languageId: document.languageId }).get('formatOnSave', false);
}

export function formattingSettings(): object {
    const config = vscode.workspace.getConfiguration('xtc.formatting');
    const integer = (key: string, fallback: number) => {
        const value = config.get<unknown>(key, fallback);
        if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > 32) {
            throw new Error(`Ecstasy formatting.${key} must be an integer from 1 to 32`);
        }
        return value;
    };
    const insertSpaces = config.get<unknown>('insertSpaces', true);
    if (typeof insertSpaces !== 'boolean') throw new Error('Ecstasy formatting.insertSpaces must be a boolean');
    return { indentSize: integer('indentSize', 4), continuationIndentSize: integer('continuationIndentSize', 8), insertSpaces };
}
