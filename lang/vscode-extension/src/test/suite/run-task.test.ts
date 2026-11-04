import * as assert from 'node:assert';
import * as vscode from 'vscode';
import { createXtcRunTask, type XtcTaskDefinition } from '../../task-provider';

/** The command line a shell task runs, its command and its arguments, as the terminal shows it. */
function commandLine(definition: XtcTaskDefinition): string {
    const execution = createXtcRunTask(definition, () => undefined).execution;
    assert.ok(execution instanceof vscode.ShellExecution, 'A Gradle or command-line run is a shell task');
    const value = (part: string | vscode.ShellQuotedString) => typeof part === 'string' ? part : part.value;
    return execution.commandLine ?? [execution.command!, ...(execution.args ?? [])].map(value).join(' ');
}

suite('Run tasks', () => {
    test("a Gradle run task passes its args to runXtc, comma-separated, as it does its moduleArguments", () => {
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', args: ['Erin', 'Frank'], mode: 'gradle' }),
            `${process.platform === 'win32' ? 'gradlew.bat' : './gradlew'} -q runXtc --module=App --args=Erin,Frank`);
        assert.ok(commandLine({ type: 'xtc', moduleName: 'App', moduleArguments: 'one,two', mode: 'gradle' }).endsWith(' --args=one,two'));
        assert.ok(commandLine({ type: 'xtc', moduleName: 'App', mode: 'gradle' }).endsWith(' runXtc --module=App'));
    });

    test("a command-line run task passes its args to xtc run as they are", () => {
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', args: ['Erin', 'Frank'], useGradle: false }), 'xtc run App Erin Frank');
        assert.strictEqual(commandLine({ type: 'xtc', moduleName: 'App', moduleArguments: 'one, two', useGradle: false }), 'xtc run App one two');
    });

    test('an argument with a space stays one argument', () => {
        const execution = createXtcRunTask({ type: 'xtc', moduleName: 'App', args: ['Erin Smith', 'Frank'], mode: 'gradle' }, () => undefined).execution;
        assert.ok(execution instanceof vscode.ShellExecution);
        // the shell quotes an argument of a command with arguments; a command line is the shell's to split
        assert.strictEqual(execution.commandLine, undefined);
        assert.ok(execution.args?.includes('--args=Erin Smith,Frank'), JSON.stringify(execution.args));
    });
});
