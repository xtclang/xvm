package org.xvm.lsp.adapter;

import org.jspecify.annotations.NonNull;
import org.xvm.lsp.model.CompilationResult;
import org.xvm.lsp.model.Diagnostic;
import org.xvm.lsp.model.Location;
import org.xvm.lsp.model.SymbolInfo;
import org.xvm.lsp.model.SymbolInfo.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock implementation of the XTC compiler adapter for testing.
 *
 * <p>This simulates what a real adapter would do by parsing XTC-like syntax
 * and producing reasonable results without actually invoking the compiler.
 */
public class MockXtcCompilerAdapter implements XtcCompilerAdapter {

    private final Map<String, CompilationResult> compiledDocuments = new ConcurrentHashMap<>();

    // Simple patterns to recognize XTC constructs
    private static final Pattern MODULE_PATTERN = Pattern.compile(
            "^\\s*module\\s+(\\w+(?:\\.\\w+)*)\\s*\\{?", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern INTERFACE_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?interface\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?service\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(\\w+(?:<[^>]+>)?(?:\\s*\\|\\s*\\w+)?)\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*[;=]", Pattern.MULTILINE);
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "ERROR:\\s*(.+)", Pattern.MULTILINE);

    @Override
    public @NonNull CompilationResult compile(
            final @NonNull String uri,
            final @NonNull String content) {

        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<SymbolInfo> symbols = new ArrayList<>();

        final String[] lines = content.split("\n", -1);

        // Check for deliberate ERROR markers (for testing)
        final Matcher errorMatcher = ERROR_PATTERN.matcher(content);
        while (errorMatcher.find()) {
            final int line = countLines(content, errorMatcher.start());
            diagnostics.add(Diagnostic.error(
                    Location.ofLine(uri, line),
                    errorMatcher.group(1)));
        }

        // Parse module
        final Matcher moduleMatcher = MODULE_PATTERN.matcher(content);
        if (moduleMatcher.find()) {
            final int line = countLines(content, moduleMatcher.start());
            final String moduleName = moduleMatcher.group(1);
            symbols.add(new SymbolInfo(
                    moduleName,
                    moduleName,
                    SymbolKind.MODULE,
                    new Location(uri, line, 0, line, moduleMatcher.end() - moduleMatcher.start()),
                    "Module " + moduleName,
                    null,
                    List.of()));
        }

        // Parse classes
        final Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            final int line = countLines(content, classMatcher.start());
            final String className = classMatcher.group(1);
            symbols.add(new SymbolInfo(
                    className,
                    className,
                    SymbolKind.CLASS,
                    new Location(uri, line, 0, line, classMatcher.end() - classMatcher.start()),
                    "Class " + className,
                    "class " + className,
                    List.of()));
        }

        // Parse interfaces
        final Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(content);
        while (interfaceMatcher.find()) {
            final int line = countLines(content, interfaceMatcher.start());
            final String ifaceName = interfaceMatcher.group(1);
            symbols.add(new SymbolInfo(
                    ifaceName,
                    ifaceName,
                    SymbolKind.INTERFACE,
                    new Location(uri, line, 0, line, interfaceMatcher.end() - interfaceMatcher.start()),
                    "Interface " + ifaceName,
                    "interface " + ifaceName,
                    List.of()));
        }

        // Parse services
        final Matcher serviceMatcher = SERVICE_PATTERN.matcher(content);
        while (serviceMatcher.find()) {
            final int line = countLines(content, serviceMatcher.start());
            final String serviceName = serviceMatcher.group(1);
            symbols.add(new SymbolInfo(
                    serviceName,
                    serviceName,
                    SymbolKind.SERVICE,
                    new Location(uri, line, 0, line, serviceMatcher.end() - serviceMatcher.start()),
                    "Service " + serviceName,
                    "service " + serviceName,
                    List.of()));
        }

        // Parse methods
        final Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            final int line = countLines(content, methodMatcher.start());
            final String returnType = methodMatcher.group(1);
            final String methodName = methodMatcher.group(2);
            // Skip if this looks like a class/interface/service declaration
            if (!returnType.equals("class") && !returnType.equals("interface") && !returnType.equals("service")) {
                symbols.add(new SymbolInfo(
                        methodName,
                        methodName,
                        SymbolKind.METHOD,
                        new Location(uri, line, 0, line, methodMatcher.end() - methodMatcher.start()),
                        null,
                        returnType + " " + methodName + "(...)",
                        List.of()));
            }
        }

        // Parse properties
        final Matcher propertyMatcher = PROPERTY_PATTERN.matcher(content);
        while (propertyMatcher.find()) {
            final int line = countLines(content, propertyMatcher.start());
            final String propType = propertyMatcher.group(1);
            final String propName = propertyMatcher.group(2);
            // Skip if this looks like something else
            if (!propType.equals("class") && !propType.equals("interface") &&
                    !propType.equals("module") && !propType.equals("return")) {
                symbols.add(new SymbolInfo(
                        propName,
                        propName,
                        SymbolKind.PROPERTY,
                        new Location(uri, line, 0, line, propertyMatcher.end() - propertyMatcher.start()),
                        null,
                        propType + " " + propName,
                        List.of()));
            }
        }

        // Check for basic syntax errors
        if (content.contains("{") && !content.contains("}")) {
            diagnostics.add(Diagnostic.error(
                    Location.ofLine(uri, lines.length - 1),
                    "Unmatched opening brace"));
        }

        final CompilationResult result = CompilationResult.withDiagnostics(uri, diagnostics, symbols);
        compiledDocuments.put(uri, result);
        return result;
    }

    @Override
    public @NonNull Optional<SymbolInfo> findSymbolAt(
            final @NonNull String uri,
            final int line,
            final int column) {

        final CompilationResult result = compiledDocuments.get(uri);
        if (result == null) {
            return Optional.empty();
        }

        return result.symbols().stream()
                .filter(s -> containsPosition(s.location(), line, column))
                .findFirst();
    }

    @Override
    public @NonNull Optional<String> getHoverInfo(
            final @NonNull String uri,
            final int line,
            final int column) {

        return findSymbolAt(uri, line, column)
                .map(symbol -> {
                    final StringBuilder hover = new StringBuilder();
                    hover.append("```xtc\n");
                    if (symbol.typeSignature() != null) {
                        hover.append(symbol.typeSignature());
                    } else {
                        hover.append(symbol.kind().name().toLowerCase())
                                .append(" ")
                                .append(symbol.name());
                    }
                    hover.append("\n```");
                    if (symbol.documentation() != null) {
                        hover.append("\n\n").append(symbol.documentation());
                    }
                    return hover.toString();
                });
    }

    @Override
    public @NonNull List<CompletionItem> getCompletions(
            final @NonNull String uri,
            final int line,
            final int column) {

        final List<CompletionItem> completions = new ArrayList<>();

        // Add keywords
        for (final String keyword : List.of(
                "module", "class", "interface", "service", "mixin", "enum", "const",
                "public", "private", "protected", "static",
                "if", "else", "while", "for", "switch", "case", "return",
                "extends", "implements", "incorporates", "delegates", "into",
                "true", "false", "null", "this", "super")) {
            completions.add(new CompletionItem(
                    keyword,
                    CompletionItem.CompletionKind.KEYWORD,
                    "keyword",
                    keyword));
        }

        // Add built-in types
        for (final String type : List.of(
                "Int", "Int8", "Int16", "Int32", "Int64",
                "UInt", "UInt8", "UInt16", "UInt32", "UInt64",
                "String", "Boolean", "Char", "Bit", "Byte",
                "Float", "Double", "Dec", "Dec64", "Dec128",
                "Array", "List", "Map", "Set", "Iterator")) {
            completions.add(new CompletionItem(
                    type,
                    CompletionItem.CompletionKind.CLASS,
                    "built-in type",
                    type));
        }

        // Add symbols from current document
        final CompilationResult result = compiledDocuments.get(uri);
        if (result != null) {
            for (final SymbolInfo symbol : result.symbols()) {
                completions.add(new CompletionItem(
                        symbol.name(),
                        toCompletionKind(symbol.kind()),
                        symbol.typeSignature() != null ? symbol.typeSignature() : symbol.kind().name(),
                        symbol.name()));
            }
        }

        return completions;
    }

    @Override
    public @NonNull Optional<Location> findDefinition(
            final @NonNull String uri,
            final int line,
            final int column) {

        // In the mock, just return the symbol's own location
        return findSymbolAt(uri, line, column)
                .map(SymbolInfo::location);
    }

    @Override
    public @NonNull List<Location> findReferences(
            final @NonNull String uri,
            final int line,
            final int column,
            final boolean includeDeclaration) {

        // Mock implementation: just return the declaration
        final List<Location> refs = new ArrayList<>();
        findSymbolAt(uri, line, column).ifPresent(symbol -> {
            if (includeDeclaration) {
                refs.add(symbol.location());
            }
        });
        return refs;
    }

    private static int countLines(final String content, final int position) {
        int lines = 0;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static boolean containsPosition(final Location loc, final int line, final int column) {
        if (line < loc.startLine() || line > loc.endLine()) {
            return false;
        }
        if (line == loc.startLine() && column < loc.startColumn()) {
            return false;
        }
        if (line == loc.endLine() && column > loc.endColumn()) {
            return false;
        }
        return true;
    }

    private static CompletionItem.CompletionKind toCompletionKind(final SymbolKind kind) {
        return switch (kind) {
            case MODULE, PACKAGE -> CompletionItem.CompletionKind.MODULE;
            case CLASS, ENUM, CONST, MIXIN -> CompletionItem.CompletionKind.CLASS;
            case INTERFACE -> CompletionItem.CompletionKind.INTERFACE;
            case SERVICE -> CompletionItem.CompletionKind.CLASS;
            case METHOD, CONSTRUCTOR -> CompletionItem.CompletionKind.METHOD;
            case PROPERTY -> CompletionItem.CompletionKind.PROPERTY;
            case PARAMETER, TYPE_PARAMETER -> CompletionItem.CompletionKind.VARIABLE;
        };
    }
}
