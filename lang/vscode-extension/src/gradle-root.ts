import * as fs from 'node:fs';
import * as path from 'node:path';
import type * as vscode from 'vscode';

/** What marks the directory where a Gradle task of a folder runs: the wrapper that the task runs, or a build's settings. */
const ROOT_FILES = ['gradlew', 'gradlew.bat', 'settings.gradle.kts', 'settings.gradle'];

/**
 * Where the Gradle tasks of [folder] run, `./gradlew` among them: the nearest directory at or above the folder that has
 * the Gradle wrapper or a build's settings, as for a folder of the window that is a project inside another folder's
 * Gradle build; the folder itself when none has.
 */
export function gradleRoot(folder: vscode.WorkspaceFolder): string {
    const start = folder.uri.fsPath;
    for (let directory = start; ; directory = path.dirname(directory)) {
        if (ROOT_FILES.some(file => fs.existsSync(path.join(directory, file)))) return directory;
        if (path.dirname(directory) === directory) return start;
    }
}
