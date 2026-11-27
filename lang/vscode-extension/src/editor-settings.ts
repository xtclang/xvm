import * as vscode from 'vscode';
import { parseServiceSettings, ServiceSettings } from './service-settings';

export function readServiceSettings(resource?: vscode.Uri): ServiceSettings {
    const config = vscode.workspace.getConfiguration('xtc', resource);
    return parseServiceSettings({
        adapter: config.get('languageService.adapter'),
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
    const integer = (key: string, fallback: number, maximum = 32) => {
        const value = config.get<unknown>(key, fallback);
        if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > maximum) {
            throw new Error(`Ecstasy formatting.${key} must be an integer from 1 to ${maximum}`);
        }
        return value;
    };
    const boolean = (key: string, fallback: boolean) => {
        const value = config.get<unknown>(key, fallback);
        if (typeof value !== 'boolean') throw new Error(`Ecstasy formatting.${key} must be a boolean`);
        return value;
    };
    const string = (key: string, fallback: string) => {
        const value = config.get<unknown>(key, fallback);
        if (typeof value !== 'string') throw new Error(`Ecstasy formatting.${key} must be a string`);
        return value;
    };
    return {
        indentSize: integer('indentSize', 4),
        continuationIndentSize: integer('continuationIndentSize', 8),
        maxLineWidth: integer('maxLineWidth', 120, 1000),
        insertSpaces: boolean('insertSpaces', true),
        wrapComments: boolean('wrapComments', false),
        formatterTagsEnabled: boolean('formatterTagsEnabled', false),
        formatterOffTag: string('formatterOffTag', '@formatter:off'),
        formatterOnTag: string('formatterOnTag', '@formatter:on'),
        formatterTagsAcceptRegexp: boolean('formatterTagsAcceptRegexp', false),
    };
}
