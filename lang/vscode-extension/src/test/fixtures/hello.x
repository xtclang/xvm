/**
 * Smallest possible Ecstasy module used as a fixture for the VS Code
 * extension's integration tests and for the `:lang:vscode-extension:runCode`
 * Gradle task (which launches VS Code in Extension Development Host mode
 * with this folder open so .x file association can be verified by eye).
 */
module HelloEcstasy {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("Hello from Ecstasy");
    }
}
