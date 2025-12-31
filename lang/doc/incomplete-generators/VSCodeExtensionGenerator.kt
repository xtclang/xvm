package org.xtclang.tooling.generators

import kotlinx.serialization.json.*
import org.xtclang.tooling.model.*

/**
 * Generates VS Code extension files:
 * - package.json
 * - language-configuration.json
 * - LSP client implementation
 */
class VSCodeExtensionGenerator(private val model: LanguageModel) {
    
    private val extensionId = model.name.lowercase()
    private val displayName = "${model.name} Language"
    
    /**
     * Generate package.json for VS Code extension
     */
    fun generatePackageJson(): String {
        val json = buildJsonObject {
            put("name", extensionId)
            put("displayName", displayName)
            put("description", "Language support for ${model.name}")
            put("version", "1.0.0")
            put("publisher", "xtclang")
            put("license", "MIT")
            put("repository", buildJsonObject {
                put("type", "git")
                put("url", "https://github.com/xtclang/vscode-$extensionId")
            })
            
            putJsonArray("categories") {
                add("Programming Languages")
            }
            putJsonArray("keywords") {
                add(model.name.lowercase())
                add("language")
                add("syntax")
            }
            
            put("engines", buildJsonObject {
                put("vscode", "^1.75.0")
            })
            
            put("activationEvents", buildJsonArray {
                model.fileExtensions.forEach { ext ->
                    add("onLanguage:$extensionId")
                }
            })
            
            put("main", "./out/extension.js")
            
            put("contributes", buildJsonObject {
                // Languages
                putJsonArray("languages") {
                    addJsonObject {
                        put("id", extensionId)
                        put("aliases", buildJsonArray {
                            add(model.name)
                            add(extensionId)
                        })
                        put("extensions", buildJsonArray {
                            model.fileExtensions.forEach { add(".$it") }
                        })
                        put("configuration", "./language-configuration.json")
                    }
                }
                
                // Grammars
                putJsonArray("grammars") {
                    addJsonObject {
                        put("language", extensionId)
                        put("scopeName", model.scopeName)
                        put("path", "./syntaxes/$extensionId.tmLanguage.json")
                    }
                }
                
                // Snippets
                putJsonArray("snippets") {
                    addJsonObject {
                        put("language", extensionId)
                        put("path", "./snippets/$extensionId.json")
                    }
                }
                
                // Configuration
                putJsonObject("configuration") {
                    put("title", model.name)
                    putJsonObject("properties") {
                        putJsonObject("$extensionId.trace.server") {
                            put("type", "string")
                            put("scope", "window")
                            put("enum", buildJsonArray { add("off"); add("messages"); add("verbose") })
                            put("default", "off")
                            put("description", "Traces the communication between VS Code and the language server.")
                        }
                        putJsonObject("$extensionId.maxNumberOfProblems") {
                            put("type", "number")
                            put("scope", "resource")
                            put("default", 100)
                            put("description", "Maximum number of problems to report per file.")
                        }
                    }
                }
            })
            
            put("scripts", buildJsonObject {
                put("vscode:prepublish", "npm run compile")
                put("compile", "tsc -p ./")
                put("watch", "tsc -watch -p ./")
                put("lint", "eslint src --ext ts")
            })
            
            put("devDependencies", buildJsonObject {
                put("@types/node", "^18.x")
                put("@types/vscode", "^1.75.0")
                put("@typescript-eslint/eslint-plugin", "^6.x")
                put("@typescript-eslint/parser", "^6.x")
                put("eslint", "^8.x")
                put("typescript", "^5.x")
            })
            
            put("dependencies", buildJsonObject {
                put("vscode-languageclient", "^9.0.1")
            })
        }
        
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json)
    }
    
    /**
     * Generate language-configuration.json
     */
    fun generateLanguageConfiguration(): String {
        val json = buildJsonObject {
            // Comments
            putJsonObject("comments") {
                put("lineComment", model.comments.lineComment)
                putJsonArray("blockComment") {
                    add(model.comments.blockCommentStart)
                    add(model.comments.blockCommentEnd)
                }
            }
            
            // Brackets
            putJsonArray("brackets") {
                addJsonArray { add("{"); add("}") }
                addJsonArray { add("["); add("]") }
                addJsonArray { add("("); add(")") }
                addJsonArray { add("<"); add(">") }
            }
            
            // Auto-closing pairs
            putJsonArray("autoClosingPairs") {
                addJsonObject { put("open", "{"); put("close", "}") }
                addJsonObject { put("open", "["); put("close", "]") }
                addJsonObject { put("open", "("); put("close", ")") }
                addJsonObject { put("open", "<"); put("close", ">") }
                addJsonObject { put("open", "\""); put("close", "\""); put("notIn", buildJsonArray { add("string") }) }
                addJsonObject { put("open", "'"); put("close", "'"); put("notIn", buildJsonArray { add("string") }) }
                addJsonObject { put("open", "/*"); put("close", " */") }
            }
            
            // Surrounding pairs
            putJsonArray("surroundingPairs") {
                addJsonArray { add("{"); add("}") }
                addJsonArray { add("["); add("]") }
                addJsonArray { add("("); add(")") }
                addJsonArray { add("<"); add(">") }
                addJsonArray { add("\""); add("\"") }
                addJsonArray { add("'"); add("'") }
            }
            
            // Folding
            putJsonObject("folding") {
                putJsonObject("markers") {
                    put("start", "^\\s*//\\s*#?region\\b")
                    put("end", "^\\s*//\\s*#?endregion\\b")
                }
            }
            
            // Indentation rules
            putJsonObject("indentationRules") {
                put("increaseIndentPattern", "^.*\\{[^}\"']*\$|^.*\\([^)\"']*\$|^\\s*(public|private|protected).*\$|^\\s*(if|else|for|while|do|switch|try|catch|finally)\\s*\\(.*\\)\\s*\$")
                put("decreaseIndentPattern", "^\\s*(\\}|\\)|\\])\\s*\$")
            }
            
            // Word pattern (for double-click selection)
            put("wordPattern", "[a-zA-Z_][a-zA-Z0-9_]*")
            
            // On enter rules
            putJsonArray("onEnterRules") {
                // Continue doc comments
                addJsonObject {
                    put("beforeText", "^\\s*/\\*\\*(?!/)([^\\*]|\\*(?!/))*\$")
                    put("afterText", "^\\s*\\*/$")
                    putJsonObject("action") {
                        put("indent", "indentAndOutdent")
                        put("appendText", " * ")
                    }
                }
                addJsonObject {
                    put("beforeText", "^\\s*/\\*\\*(?!/)([^\\*]|\\*(?!/))*\$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("appendText", " * ")
                    }
                }
                addJsonObject {
                    put("beforeText", "^(\\t|[ ])*[ ]\\*([ ]([^\\*]|\\*(?!/))*)?$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("appendText", "* ")
                    }
                }
                addJsonObject {
                    put("beforeText", "^(\\t|[ ])*[ ]\\*/\\s*\$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("removeText", 1)
                    }
                }
            }
        }
        
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json)
    }
    
    /**
     * Generate extension.ts (TypeScript entry point)
     */
    fun generateExtensionTs(): String = """
import * as path from 'path';
import { workspace, ExtensionContext } from 'vscode';

import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    // The server is implemented in Java/Kotlin and packaged as a JAR
    const serverJar = context.asAbsolutePath(
        path.join('server', '${extensionId}-lsp.jar')
    );
    
    // Java executable - use java from PATH or JAVA_HOME
    const javaExecutable = process.env.JAVA_HOME 
        ? path.join(process.env.JAVA_HOME, 'bin', 'java')
        : 'java';

    const serverOptions: ServerOptions = {
        run: {
            command: javaExecutable,
            args: ['-jar', serverJar],
            transport: TransportKind.stdio
        },
        debug: {
            command: javaExecutable,
            args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005', '-jar', serverJar],
            transport: TransportKind.stdio
        }
    };

    // Options to control the language client
    const clientOptions: LanguageClientOptions = {
        // Register the server for ${model.name} documents
        documentSelector: [{ scheme: 'file', language: '${extensionId}' }],
        synchronize: {
            // Notify the server about file changes to '.${model.fileExtensions.first()}' files in the workspace
            fileEvents: workspace.createFileSystemWatcher('**/*.{${model.fileExtensions.joinToString(",")}}')
        }
    };

    // Create the language client and start it
    client = new LanguageClient(
        '${extensionId}LanguageServer',
        '${model.name} Language Server',
        serverOptions,
        clientOptions
    );

    // Start the client (which also launches the server)
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
""".trimIndent()
    
    /**
     * Generate basic snippets
     */
    fun generateSnippets(): String {
        val json = buildJsonObject {
            putJsonObject("Module") {
                put("prefix", "module")
                putJsonArray("body") {
                    add("module \${1:ModuleName} {")
                    add("\tvoid run() {")
                    add("\t\t\$0")
                    add("\t}")
                    add("}")
                }
                put("description", "Create a new module")
            }
            
            putJsonObject("Class") {
                put("prefix", "class")
                putJsonArray("body") {
                    add("class \${1:ClassName} {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Create a new class")
            }
            
            putJsonObject("Interface") {
                put("prefix", "interface")
                putJsonArray("body") {
                    add("interface \${1:InterfaceName} {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Create a new interface")
            }
            
            putJsonObject("Service") {
                put("prefix", "service")
                putJsonArray("body") {
                    add("service \${1:ServiceName} {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Create a new service")
            }
            
            putJsonObject("Mixin") {
                put("prefix", "mixin")
                putJsonArray("body") {
                    add("mixin \${1:MixinName} into \${2:TargetType} {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Create a new mixin")
            }
            
            putJsonObject("If Statement") {
                put("prefix", "if")
                putJsonArray("body") {
                    add("if (\${1:condition}) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "If statement")
            }
            
            putJsonObject("If-Else") {
                put("prefix", "ife")
                putJsonArray("body") {
                    add("if (\${1:condition}) {")
                    add("\t\$2")
                    add("} else {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "If-else statement")
            }
            
            putJsonObject("For Loop") {
                put("prefix", "for")
                putJsonArray("body") {
                    add("for (Int \${1:i} = 0; \$1 < \${2:count}; \$1++) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "For loop")
            }
            
            putJsonObject("For-Each Loop") {
                put("prefix", "foreach")
                putJsonArray("body") {
                    add("for (\${1:Type} \${2:item} : \${3:collection}) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "For-each loop")
            }
            
            putJsonObject("While Loop") {
                put("prefix", "while")
                putJsonArray("body") {
                    add("while (\${1:condition}) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "While loop")
            }
            
            putJsonObject("Try-Catch") {
                put("prefix", "try")
                putJsonArray("body") {
                    add("try {")
                    add("\t\$1")
                    add("} catch (\${2:Exception} \${3:e}) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Try-catch block")
            }
            
            putJsonObject("Switch") {
                put("prefix", "switch")
                putJsonArray("body") {
                    add("switch (\${1:expression}) {")
                    add("\tcase \${2:value}:")
                    add("\t\t\$0")
                    add("\t\tbreak;")
                    add("\tdefault:")
                    add("\t\tbreak;")
                    add("}")
                }
                put("description", "Switch statement")
            }
            
            putJsonObject("Method") {
                put("prefix", "method")
                putJsonArray("body") {
                    add("\${1:void} \${2:methodName}(\${3:parameters}) {")
                    add("\t\$0")
                    add("}")
                }
                put("description", "Method declaration")
            }
            
            putJsonObject("Property") {
                put("prefix", "prop")
                putJsonArray("body") {
                    add("\${1:Type} \${2:propertyName};")
                }
                put("description", "Property declaration")
            }
            
            putJsonObject("Inject") {
                put("prefix", "inject")
                putJsonArray("body") {
                    add("@Inject \${1:Type} \${2:name};")
                }
                put("description", "Inject dependency")
            }
            
            putJsonObject("Console Print") {
                put("prefix", "print")
                putJsonArray("body") {
                    add("@Inject Console console;")
                    add("console.print(\$\"\${1:message}\");")
                }
                put("description", "Print to console")
            }
            
            putJsonObject("Assert") {
                put("prefix", "assert")
                putJsonArray("body") {
                    add("assert \${1:condition};")
                }
                put("description", "Assert statement")
            }
            
            putJsonObject("Lambda") {
                put("prefix", "lambda")
                putJsonArray("body") {
                    add("(\${1:params}) -> \${2:expression}")
                }
                put("description", "Lambda expression")
            }
        }
        
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json)
    }
}

// Extension functions
private fun JsonObjectBuilder.putJsonArray(key: String, block: JsonArrayBuilder.() -> Unit) {
    put(key, buildJsonArray(block))
}

private fun JsonObjectBuilder.putJsonObject(key: String, block: JsonObjectBuilder.() -> Unit) {
    put(key, buildJsonObject(block))
}

private fun JsonArrayBuilder.addJsonObject(block: JsonObjectBuilder.() -> Unit) {
    add(buildJsonObject(block))
}

private fun JsonArrayBuilder.addJsonArray(block: JsonArrayBuilder.() -> Unit) {
    add(buildJsonArray(block))
}
