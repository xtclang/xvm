package org.xtclang.tooling.generators

import org.xtclang.tooling.model.*

/**
 * Generates Eclipse IDE plugin components:
 * - plugin.xml
 * - Editor configuration
 * - Syntax coloring
 * - Content assist
 */
class EclipseGenerator(private val model: LanguageModel) {
    
    private val pluginId = "org.xtclang.eclipse"
    private val languageName = model.name
    private val languageNameCap = model.name.replaceFirstChar { it.uppercase() }
    private val languageId = model.name.lowercase()
    
    /**
     * Generate plugin.xml
     */
    fun generatePluginXml(): String = """
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
    <!-- Editor -->
    <extension point="org.eclipse.ui.editors">
        <editor
            id="$pluginId.editor"
            name="$languageName Editor"
            extensions="${model.fileExtensions.joinToString(",")}"
            icon="icons/xtc_file.png"
            contributorClass="$pluginId.editor.${languageNameCap}EditorContributor"
            class="$pluginId.editor.${languageNameCap}Editor">
        </editor>
    </extension>
    
    <!-- Content Type -->
    <extension point="org.eclipse.core.contenttype.contentTypes">
        <content-type
            id="$pluginId.contenttype"
            name="$languageName Source File"
            base-type="org.eclipse.core.runtime.text"
            file-extensions="${model.fileExtensions.joinToString(",")}"
            priority="normal">
        </content-type>
    </extension>
    
    <!-- Syntax Coloring -->
    <extension point="org.eclipse.ui.editors.presentationReconcilers">
        <presentationReconciler
            id="$pluginId.presentationReconciler"
            class="$pluginId.editor.${languageNameCap}PresentationReconciler">
        </presentationReconciler>
    </extension>
    
    <!-- Document Setup Participant -->
    <extension point="org.eclipse.core.filebuffers.documentSetup">
        <participant
            contentTypeId="$pluginId.contenttype"
            class="$pluginId.editor.${languageNameCap}DocumentSetupParticipant">
        </participant>
    </extension>
    
    <!-- Preferences -->
    <extension point="org.eclipse.ui.preferencePages">
        <page
            id="$pluginId.preferences"
            name="$languageName"
            class="$pluginId.preferences.${languageNameCap}PreferencePage">
        </page>
        <page
            id="$pluginId.preferences.editor"
            name="Editor"
            category="$pluginId.preferences"
            class="$pluginId.preferences.${languageNameCap}EditorPreferencePage">
        </page>
        <page
            id="$pluginId.preferences.syntax"
            name="Syntax Coloring"
            category="$pluginId.preferences"
            class="$pluginId.preferences.${languageNameCap}SyntaxColoringPreferencePage">
        </page>
    </extension>
    
    <!-- Property Testers -->
    <extension point="org.eclipse.core.expressions.propertyTesters">
        <propertyTester
            id="$pluginId.propertyTester"
            type="org.eclipse.core.resources.IFile"
            namespace="$pluginId"
            properties="is${languageNameCap}File"
            class="$pluginId.${languageNameCap}PropertyTester">
        </propertyTester>
    </extension>
    
    <!-- Nature -->
    <extension point="org.eclipse.core.resources.natures">
        <runtime>
            <nature
                id="$pluginId.nature"
                name="$languageName Nature"
                class="$pluginId.${languageNameCap}Nature">
            </nature>
        </runtime>
    </extension>
    
    <!-- Builder -->
    <extension point="org.eclipse.core.resources.builders">
        <builder
            id="$pluginId.builder"
            name="$languageName Builder">
            <run class="$pluginId.builder.${languageNameCap}Builder"/>
        </builder>
    </extension>
    
    <!-- Templates -->
    <extension point="org.eclipse.ui.editors.templates">
        <contextType
            id="$pluginId.templates.context"
            name="$languageName"
            class="$pluginId.templates.${languageNameCap}ContextType">
        </contextType>
        <include file="templates/templates.xml"/>
    </extension>
    
    <!-- Content Assist -->
    <extension point="org.eclipse.jface.text.contentAssistProcessor">
        <contentAssistProcessor
            class="$pluginId.editor.${languageNameCap}ContentAssistProcessor"
            contentType="$pluginId.contenttype">
        </contentAssistProcessor>
    </extension>
    
    <!-- Outline -->
    <extension point="org.eclipse.ui.views.contentOutlines">
        <contentOutline
            id="$pluginId.outline"
            class="$pluginId.outline.${languageNameCap}ContentOutlinePage">
        </contentOutline>
    </extension>
    
    <!-- Markers -->
    <extension point="org.eclipse.core.resources.markers">
        <marker
            id="$pluginId.problem"
            name="$languageName Problem"
            super type="org.eclipse.core.resources.problemmarker">
            <persistent value="true"/>
        </marker>
    </extension>
    
    <!-- New Wizard -->
    <extension point="org.eclipse.ui.newWizards">
        <wizard
            id="$pluginId.wizard.newModule"
            name="$languageName Module"
            class="$pluginId.wizards.New${languageNameCap}ModuleWizard"
            category="$pluginId.category"
            icon="icons/xtc_file.png">
            <description>Create a new $languageName module</description>
        </wizard>
        <wizard
            id="$pluginId.wizard.newClass"
            name="$languageName Class"
            class="$pluginId.wizards.New${languageNameCap}ClassWizard"
            category="$pluginId.category"
            icon="icons/xtc_class.png">
            <description>Create a new $languageName class</description>
        </wizard>
        <category
            id="$pluginId.category"
            name="$languageName">
        </category>
    </extension>
</plugin>
""".trimIndent()

    /**
     * Generate syntax coloring configuration
     */
    fun generateSyntaxColoringConfig(): String = """
package $pluginId.editor;

import org.eclipse.jface.text.*;
import org.eclipse.jface.text.rules.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

/**
 * Scanner for $languageName syntax highlighting.
 */
public class ${languageNameCap}CodeScanner extends RuleBasedScanner {
    
    // Color constants
    public static final RGB COLOR_KEYWORD = new RGB(127, 0, 85);
    public static final RGB COLOR_STRING = new RGB(42, 0, 255);
    public static final RGB COLOR_COMMENT = new RGB(63, 127, 95);
    public static final RGB COLOR_DOC_COMMENT = new RGB(63, 95, 191);
    public static final RGB COLOR_NUMBER = new RGB(125, 125, 125);
    public static final RGB COLOR_ANNOTATION = new RGB(100, 100, 100);
    public static final RGB COLOR_TYPE = new RGB(0, 0, 192);
    public static final RGB COLOR_DEFAULT = new RGB(0, 0, 0);
    
    public ${languageNameCap}CodeScanner(${languageNameCap}ColorManager colorManager) {
        // Tokens
        IToken keywordToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_KEYWORD), null, SWT.BOLD));
        IToken stringToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_STRING)));
        IToken commentToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_COMMENT)));
        IToken docCommentToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_DOC_COMMENT)));
        IToken numberToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_NUMBER)));
        IToken annotationToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_ANNOTATION)));
        IToken typeToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_TYPE), null, SWT.BOLD));
        IToken defaultToken = new Token(
            new TextAttribute(colorManager.getColor(COLOR_DEFAULT)));
        
        setDefaultReturnToken(defaultToken);
        
        java.util.List<IRule> rules = new java.util.ArrayList<>();
        
        // Whitespace
        rules.add(new WhitespaceRule(new ${languageNameCap}WhitespaceDetector()));
        
        // Comments
        rules.add(new EndOfLineRule("//", commentToken));
        rules.add(new MultiLineRule("/**", "*/", docCommentToken, (char) 0, true));
        rules.add(new MultiLineRule("/*", "*/", commentToken, (char) 0, true));
        
        // Strings
        rules.add(new SingleLineRule("\"", "\"", stringToken, '\\'));
        rules.add(new SingleLineRule("'", "'", stringToken, '\\'));
        rules.add(new SingleLineRule("$\"", "\"", stringToken, '\\'));
        
        // Annotations
        rules.add(new ${languageNameCap}AnnotationRule(annotationToken));
        
        // Numbers
        rules.add(new ${languageNameCap}NumberRule(numberToken));
        
        // Keywords and types
        WordRule wordRule = new WordRule(new ${languageNameCap}WordDetector(), defaultToken);
        
        // Keywords
        ${model.keywords.sorted().joinToString("\n        ") { 
            "wordRule.addWord(\"$it\", keywordToken);" 
        }}
        
        // Built-in types
        for (String type : new String[] {
            "Bit", "Boolean", "Byte", "Char", "Dec", "Float",
            "Int", "Int8", "Int16", "Int32", "Int64", "Int128", "IntN",
            "UInt", "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",
            "String", "Object", "Enum", "Exception",
            "Array", "List", "Set", "Map", "Range", "Interval", "Tuple",
            "Function", "Method", "Property", "Type", "Class",
            "Const", "Service", "Module", "Package", "Void"
        }) {
            wordRule.addWord(type, typeToken);
        }
        
        // Boolean and null literals
        wordRule.addWord("True", keywordToken);
        wordRule.addWord("False", keywordToken);
        wordRule.addWord("Null", keywordToken);
        
        rules.add(wordRule);
        
        setRules(rules.toArray(new IRule[0]));
    }
}

class ${languageNameCap}WhitespaceDetector implements IWhitespaceDetector {
    @Override
    public boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }
}

class ${languageNameCap}WordDetector implements IWordDetector {
    @Override
    public boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_';
    }
    
    @Override
    public boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}

class ${languageNameCap}AnnotationRule implements IRule {
    private final IToken token;
    
    public ${languageNameCap}AnnotationRule(IToken token) {
        this.token = token;
    }
    
    @Override
    public IToken evaluate(ICharacterScanner scanner) {
        int c = scanner.read();
        if (c == '@') {
            int count = 1;
            c = scanner.read();
            while (Character.isLetterOrDigit(c) || c == '_') {
                count++;
                c = scanner.read();
            }
            scanner.unread();
            if (count > 1) {
                return token;
            }
            scanner.unread();
        } else {
            scanner.unread();
        }
        return Token.UNDEFINED;
    }
}

class ${languageNameCap}NumberRule implements IRule {
    private final IToken token;
    
    public ${languageNameCap}NumberRule(IToken token) {
        this.token = token;
    }
    
    @Override
    public IToken evaluate(ICharacterScanner scanner) {
        int c = scanner.read();
        if (Character.isDigit(c)) {
            // Check for hex or binary
            if (c == '0') {
                c = scanner.read();
                if (c == 'x' || c == 'X') {
                    // Hex number
                    c = scanner.read();
                    while (isHexDigit(c) || c == '_') {
                        c = scanner.read();
                    }
                    scanner.unread();
                    return token;
                } else if (c == 'b' || c == 'B') {
                    // Binary number
                    c = scanner.read();
                    while (c == '0' || c == '1' || c == '_') {
                        c = scanner.read();
                    }
                    scanner.unread();
                    return token;
                }
            }
            
            // Decimal number
            while (Character.isDigit(c) || c == '_') {
                c = scanner.read();
            }
            
            // Check for float
            if (c == '.') {
                c = scanner.read();
                if (Character.isDigit(c)) {
                    while (Character.isDigit(c) || c == '_') {
                        c = scanner.read();
                    }
                    // Check for exponent
                    if (c == 'e' || c == 'E') {
                        c = scanner.read();
                        if (c == '+' || c == '-') {
                            c = scanner.read();
                        }
                        while (Character.isDigit(c)) {
                            c = scanner.read();
                        }
                    }
                } else {
                    scanner.unread();
                }
            }
            
            scanner.unread();
            return token;
        }
        scanner.unread();
        return Token.UNDEFINED;
    }
    
    private boolean isHexDigit(int c) {
        return Character.isDigit(c) || 
               (c >= 'a' && c <= 'f') || 
               (c >= 'A' && c <= 'F');
    }
}
""".trimIndent()

    /**
     * Generate templates.xml for code templates
     */
    fun generateTemplatesXml(): String = """
<?xml version="1.0" encoding="UTF-8"?>
<templates>
    <template 
        name="module" 
        description="Create a new module" 
        context="$pluginId.templates.context"
        enabled="true">
module ${'$'}{name} {
    void run() {
        ${'$'}{cursor}
    }
}
    </template>
    
    <template 
        name="class" 
        description="Create a new class" 
        context="$pluginId.templates.context"
        enabled="true">
class ${'$'}{name} {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="interface" 
        description="Create a new interface" 
        context="$pluginId.templates.context"
        enabled="true">
interface ${'$'}{name} {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="service" 
        description="Create a new service" 
        context="$pluginId.templates.context"
        enabled="true">
service ${'$'}{name} {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="mixin" 
        description="Create a new mixin" 
        context="$pluginId.templates.context"
        enabled="true">
mixin ${'$'}{name} into ${'$'}{target} {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="const" 
        description="Create a new const class" 
        context="$pluginId.templates.context"
        enabled="true">
const ${'$'}{name}(${'$'}{parameters}) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="enum" 
        description="Create a new enum" 
        context="$pluginId.templates.context"
        enabled="true">
enum ${'$'}{name} {
    ${'$'}{values}
}
    </template>
    
    <template 
        name="if" 
        description="If statement" 
        context="$pluginId.templates.context"
        enabled="true">
if (${'$'}{condition}) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="ife" 
        description="If-else statement" 
        context="$pluginId.templates.context"
        enabled="true">
if (${'$'}{condition}) {
    ${'$'}{then}
} else {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="for" 
        description="For loop" 
        context="$pluginId.templates.context"
        enabled="true">
for (Int ${'$'}{i} = 0; ${'$'}{i} &lt; ${'$'}{count}; ${'$'}{i}++) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="foreach" 
        description="For-each loop" 
        context="$pluginId.templates.context"
        enabled="true">
for (${'$'}{Type} ${'$'}{item} : ${'$'}{collection}) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="while" 
        description="While loop" 
        context="$pluginId.templates.context"
        enabled="true">
while (${'$'}{condition}) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="try" 
        description="Try-catch block" 
        context="$pluginId.templates.context"
        enabled="true">
try {
    ${'$'}{tryBlock}
} catch (${'$'}{Exception} ${'$'}{e}) {
    ${'$'}{cursor}
}
    </template>
    
    <template 
        name="switch" 
        description="Switch statement" 
        context="$pluginId.templates.context"
        enabled="true">
switch (${'$'}{expression}) {
    case ${'$'}{value}:
        ${'$'}{cursor}
        break;
    default:
        break;
}
    </template>
    
    <template 
        name="inject" 
        description="Inject dependency" 
        context="$pluginId.templates.context"
        enabled="true">
@Inject ${'$'}{Type} ${'$'}{name};
    </template>
    
    <template 
        name="print" 
        description="Print to console" 
        context="$pluginId.templates.context"
        enabled="true">
@Inject Console console;
console.print(${'$'}{message});
    </template>
</templates>
""".trimIndent()

    /**
     * Generate MANIFEST.MF
     */
    fun generateManifest(): String = """
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: $languageName IDE Support
Bundle-SymbolicName: $pluginId;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: $pluginId.Activator
Bundle-Vendor: xtclang.org
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.jface.text,
 org.eclipse.ui.editors,
 org.eclipse.ui.workbench.texteditor,
 org.eclipse.core.resources,
 org.eclipse.ui.ide,
 org.eclipse.core.filebuffers
Bundle-RequiredExecutionEnvironment: JavaSE-17
Automatic-Module-Name: $pluginId
Bundle-ActivationPolicy: lazy
Export-Package: $pluginId,
 $pluginId.editor,
 $pluginId.preferences
""".trimIndent()

    /**
     * Generate all Eclipse files as a map
     */
    fun generateAll(): Map<String, String> = mapOf(
        "plugin.xml" to generatePluginXml(),
        "src/${pluginId.replace(".", "/")}/editor/${languageNameCap}CodeScanner.java" to generateSyntaxColoringConfig(),
        "templates/templates.xml" to generateTemplatesXml(),
        "META-INF/MANIFEST.MF" to generateManifest()
    )
}
