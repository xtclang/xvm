import * as path from 'path';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
    console.log('XTC Language Support is now active');

    // Find the LSP server JAR
    const serverJar = context.asAbsolutePath(path.join('server', 'lsp-server.jar'));

    // Java executable - use JAVA_HOME if set, otherwise assume 'java' is in PATH
    const javaHome = process.env.JAVA_HOME;
    const javaExecutable = javaHome ? path.join(javaHome, 'bin', 'java') : 'java';

    // Server options - start the LSP server as a Java process
    const serverOptions: ServerOptions = {
        run: {
            command: javaExecutable,
            args: ['-jar', serverJar],
            transport: TransportKind.stdio
        },
        debug: {
            command: javaExecutable,
            args: ['-jar', serverJar, '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'],
            transport: TransportKind.stdio
        }
    };

    // Client options
    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xtc' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.x')
        }
    };

    // Create and start the language client
    client = new LanguageClient(
        'xtcLanguageServer',
        'XTC Language Server',
        serverOptions,
        clientOptions
    );

    // Start the client (also starts the server)
    client.start();

    // Register create project command
    const createProjectCommand = vscode.commands.registerCommand('xtc.createProject', async () => {
        const projectName = await vscode.window.showInputBox({
            prompt: 'Enter project name',
            placeHolder: 'myproject',
            validateInput: (value) => {
                if (!value || value.trim().length === 0) {
                    return 'Project name cannot be empty';
                }
                if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(value)) {
                    return 'Project name must start with a letter or underscore and contain only letters, digits, and underscores';
                }
                return null;
            }
        });

        if (!projectName) {
            return;
        }

        const projectType = await vscode.window.showQuickPick(
            ['Application', 'Library', 'Service'],
            { placeHolder: 'Select project type' }
        );

        if (!projectType) {
            return;
        }

        const folderUri = await vscode.window.showOpenDialog({
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            openLabel: 'Select Parent Folder'
        });

        if (!folderUri || folderUri.length === 0) {
            return;
        }

        const parentPath = folderUri[0].fsPath;

        // Use xtc init command if available, otherwise show instructions
        const terminal = vscode.window.createTerminal('XTC');
        terminal.show();
        terminal.sendText(`xtc init "${projectName}" --type ${projectType.toLowerCase()} --dir "${parentPath}"`);

        vscode.window.showInformationMessage(`Creating XTC ${projectType} project: ${projectName}`);
    });

    context.subscriptions.push(createProjectCommand);
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
