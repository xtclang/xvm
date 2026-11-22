# Ecstasy language adapters

| Adapter | Knows |
|---------|-------|
| **Ecstasy compiler**, the default | The code's types, as the compiler does. Embedded builds and runs need it. |
| **Tree-sitter** | The code's structure only, with an index of the workspace. The older, syntax-only adapter, and a fallback when the compiler adapter has trouble with a project. |

Switching restarts the language server. The setting is `xtc.languageService.adapter`.
