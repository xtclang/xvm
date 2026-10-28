# Ecstasy Language Tooling Documentation

## Build, use and test the language tools

- [Language server](../lsp-server/README.md): compiler and Tree-sitter adapters, supported features and configuration.
- [IntelliJ plugin](../intellij-plugin/README.md) and [testing](../intellij-plugin/TESTING.md): native actions, settings and test harnesses.
- [VS Code extension](../vscode-extension/README.md): installation, commands and native tests.
- [Manual playbook](manual-test-plan.md): copyable examples, expected behavior, automated selections and client limitations.
- [Embedded build and run](plans/plan-embedded-execution.md#first-delivery-2026-10-15): building and running with the language server's compiler in both IDEs, and the remaining execution and debugging work.
- [Compiler architecture](../../javatools/README.md#compiler-architecture-for-language-tooling): why the embedding and source-analysis APIs are needed.

## Background design notes

The [retained design notes](plans/) describe earlier design work. Use the feature and testing guides
above for the behavior of the current implementation.
