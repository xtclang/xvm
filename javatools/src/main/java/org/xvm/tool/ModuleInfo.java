package org.xvm.tool;

import org.jetbrains.annotations.Nullable;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Version;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;
import static org.xvm.tool.Launcher.DUP_NAME;
import static org.xvm.tool.Launcher.MISSING_PKG_NODE;
import static org.xvm.tool.Launcher.READ_FAILURE;
import static org.xvm.tool.ResourceDir.NO_RESOURCES;
import static org.xvm.util.Handy.getExtension;
import static org.xvm.util.Handy.listFiles;
import static org.xvm.util.Handy.parentOf;
import static org.xvm.util.Handy.readFileChars;
import static org.xvm.util.Handy.removeExtension;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;


/**
 * Immutable snapshot of module information gleaned from a file specification.
 * All metadata is computed eagerly at construction time. Only expensive operations
 * like source tree parsing are deferred.
 */
public class ModuleInfo {
    private final File         fileSpec;
    private final String       fileName;
    private final File         projectDir;
    private final SourceInfo   source;
    private final BinaryInfo   binary;
    private final ResourceInfo resources;
    private final String       moduleName;

    // The only mutable state: lazily computed source tree
    private final AtomicReference<Node> sourceTreeRef = new AtomicReference<>();

    public ModuleInfo(final File fileSpec, final boolean deduce) {
        this(fileSpec, deduce, List.of(), null);
    }

    public ModuleInfo(final File fileSpec, final boolean deduce, final List<File> resourceSpecs, final File binarySpec) {
        Objects.requireNonNull(fileSpec, "A file specification is required for the module");
        // Resolve everything eagerly at construction to enable final state and debuggability from the API usages.
        // The only mutable state is the source tree Node, which of course is the
        final Resolution r = resolve(fileSpec, deduce, resourceSpecs, binarySpec);
        this.fileSpec   = fileSpec;
        this.fileName   = r.fileName;
        this.projectDir = r.projectDir;
        this.source     = r.source;
        this.binary     = r.binary;
        this.resources  = r.resources;
        this.moduleName = r.moduleName;
    }


    // ----- immutable state records ---------------------------------------------------------------

    /**
     * Source location information - all computed at construction.
     */
    private record SourceInfo(
            File    dir,
            File    file,
            boolean isTree,
            boolean exists,
            long    timestamp
    ) {}

    /**
     * Binary file information - all computed at construction.
     */
    private record BinaryInfo(
            File    dir,
            File    file,
            boolean exists,
            Version version,
            long    timestamp
    ) {}

    /**
     * Resource directory information - all computed at construction.
     */
    private record ResourceInfo(
            ResourceDir dir,
            long        timestamp
    ) {}

    /**
     * Resolution result - bundles all computed state.
     */
    private record Resolution(
            String       fileName,
            File         projectDir,
            SourceInfo   source,
            BinaryInfo   binary,
            ResourceInfo resources,
            String       moduleName
    ) {}

    private static Resolution resolve(final File fileSpec, final boolean deduce, final List<File> resourceSpecs, final File binarySpec) {
        // Parse file specification into immutable result
        final ParsedFileSpec parsed = parseFileSpec(fileSpec);
        // Find project structure
        final StructureResult structure = findProjectStructure(parsed.dirSpec, parsed.fileName, extractShorterName(parsed.fileName), deduce);
        // Derive source info
        final SourceInfo source = deriveSourceInfo(structure, parsed.fileName);
        // Resolve binary
        final BinaryInfo binary = resolveBinary(binarySpec, structure.projectDir, parsed.fileName, deduce);
        // Resolve resources (pass source.dir and projectDir to avoid creating another ModuleInfo)
        final ResourceInfo resources = resolveResources(resourceSpecs, source.file, source.dir, structure.projectDir, deduce);
        // Determine module name
        final String moduleName = determineModuleName(structure.moduleName, source, binary, parsed.fileName, structure.projectDir);
        // Return resolved state.
        return new Resolution(parsed.fileName, structure.projectDir, source, binary, resources, moduleName);
    }

    private record ParsedFileSpec(File dirSpec, String fileName) {}

    @SuppressWarnings("BooleanVariableAlwaysNegated")
    private static ParsedFileSpec parseFileSpec(final File fileSpec) {
        final File resolved = resolveFile(fileSpec);
        final String rawName = resolved.getName();

        if (rawName.isEmpty()) {
            return new ParsedFileSpec(resolved.getParentFile(), null);
        }

        if (resolved.isDirectory()) {
            final boolean hasMatchingSource = parentOf(resolved)
                    .filter(p -> new File(p, rawName + ".x").exists())
                    .isPresent();
            if (!hasMatchingSource) {
                return new ParsedFileSpec(resolved, null);
            }
        }

        final File dirSpec = parentOf(resolved)
                .filter(File::isDirectory)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to identify a module directory for " + fileSpec));

        // Strip extension if it's an ecstasy file (source .x or binary .xtc)
        final String fileName = isExplicitEcstasyFile(rawName) ? removeExtension(rawName) : rawName;
        return new ParsedFileSpec(dirSpec, fileName);
    }

    private static SourceInfo deriveSourceInfo(final StructureResult structure, final String fileName) {
        final File sourceFile = Optional.ofNullable(structure.sourceFile)
                .or(() -> Optional.ofNullable(fileName)
                        .flatMap(name -> Optional.ofNullable(structure.sourceDir)
                                .map(dir -> new File(dir, name + ".x"))))
                .orElse(null);

        if (sourceFile == null || !sourceFile.exists()) {
            return new SourceInfo(structure.sourceDir, null, false, false, 0L);
        }

        final File sourceDir = Optional.ofNullable(structure.sourceDir).orElseGet(sourceFile::getParentFile);
        final boolean isTree = fileName != null && new File(sourceDir, fileName).isDirectory();
        final long timestamp = computeSourceTimestamp(sourceFile, isTree);

        return new SourceInfo(sourceDir, sourceFile, isTree, true, timestamp);
    }

    private record StructureResult(File projectDir, File sourceDir, File sourceFile, String moduleName) {}

    private static StructureResult findProjectStructure(final File dirSpec, final String fileName, final String shorterName, final boolean deduce) {
        final List<File> srcFiles = sourceFiles(dirSpec);
        final List<File> binFiles = compiledFiles(dirSpec);

        // Directory with both source and compiled files
        if (!srcFiles.isEmpty() && !binFiles.isEmpty()) {
            return new StructureResult(dirSpec, dirSpec, null, null);
        }

        // Directory with only compiled files
        if (srcFiles.isEmpty() && !binFiles.isEmpty()) {
            final File projectDir = deduce ? projectDirFromSubDir(dirSpec) : dirSpec;
            final File sourceDir = deduce ? sourceDirFromPrjDir(projectDir) : null;
            return new StructureResult(projectDir, sourceDir, null, null);
        }

        // No source files - derive from project structure
        if (srcFiles.isEmpty()) {
            final File projectDir = deduce ? projectDirFromSubDir(dirSpec) : dirSpec;
            final File sourceDir = deduce ? sourceDirFromPrjDir(projectDir) : null;
            final String resolvedName = resolveFileName(fileName, sourceDir);
            final File sourceFile = Optional.ofNullable(resolvedName)
                    .flatMap(name -> Optional.ofNullable(sourceDir)
                            .map(dir -> new File(dir, name + ".x")))
                    .orElse(null);
            return new StructureResult(projectDir, sourceDir, sourceFile, null);
        }

        // Has source files - search for module
        return searchForModule(dirSpec, srcFiles, fileName, shorterName, deduce);
    }

    private static String resolveFileName(final String fileName, final File sourceDir) {
        if (fileName != null) {
            return fileName;
        }
        if (sourceDir == null) {
            return null;
        }
        final List<File> files = sourceFiles(sourceDir);
        return files.size() == 1 ? removeExtension(files.getFirst().getName()) : null;
    }

    private static StructureResult searchForModule(final File dir, final List<File> files, final String fileName, final String shorterName, final boolean deduce) {
        // Early exit if no files to search
        if (files.isEmpty()) {
            return null;
        }

        final String srcName  = fileName != null ? fileName + ".x" : null;
        final String srcName2 = shorterName != null ? shorterName + ".x" : null;

        // Try to find module in current directory
        final StructureResult found = tryFindModuleInDir(dir, files, srcName, srcName2, deduce);
        if (found != null) {
            return found;
        }

        // If not deducing, return defaults
        if (!deduce) {
            return defaultStructure(dir, false);
        }

        // Try to recurse to parent if it has source files
        return parentOf(dir)
                .map(parent -> searchForModule(parent, sourceFiles(parent), fileName, shorterName, true))
                .orElseGet(() -> defaultStructure(dir, true));
    }

    private static StructureResult tryFindModuleInDir(final File dir, final List<File> files,
                                                      final String srcName, final String srcName2,
                                                      final boolean deduce) {
        // No file name specified - look for single module file
        if (srcName == null && files.size() == 1) {
            final File file = files.getFirst();
            final String moduleName = extractModuleName(file);
            if (moduleName != null) {
                final File projectDir = deduce ? projectDirFromSubDir(dir) : dir;
                return new StructureResult(projectDir, dir, file, moduleName);
            }
        }

        // File name specified - look for matching source
        if (srcName != null) {
            final File match = files.stream()
                    .filter(f -> !f.isDirectory())
                    .filter(f -> f.getName().equals(srcName) || f.getName().equals(srcName2))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                final File projectDir = deduce ? projectDirFromSubDir(dir) : dir;
                return new StructureResult(projectDir, dir, match, null);
            }
        }

        return null;
    }

    private static StructureResult defaultStructure(final File dir, final boolean deduce) {
        final File projectDir = deduce ? projectDirFromSubDir(dir) : dir;
        final File sourceDir = deduce ? sourceDirFromPrjDir(projectDir) : dir;
        return new StructureResult(projectDir, sourceDir, null, null);
    }

    private static long computeSourceTimestamp(final File sourceFile, final boolean isTree) {
        if (sourceFile == null || !sourceFile.exists()) {
            return 0L;
        }

        final long fileTime = sourceFile.lastModified();
        if (!isTree) {
            return fileTime;
        }

        final File subDir = new File(sourceFile.getParentFile(), removeExtension(sourceFile.getName()));
        return subDir.isDirectory()
                ? Math.max(fileTime, computeMaxTimestamp(subDir))
                : fileTime;
    }

    private static long computeMaxTimestamp(final File dir) {
        final List<File> files = new ArrayList<>();
        visitTree(dir, "x", files::add);
        return files.stream().mapToLong(File::lastModified).max().orElse(0L);
    }

    private static BinaryInfo resolveBinary(final File binarySpec, final File projectDir,
                                            final String fileName, final boolean deduce) {
        if (binarySpec == null) {
            return resolveBinaryDefault(projectDir, fileName, deduce);
        }

        final File resolved = resolveFile(binarySpec);
        if (resolved.exists()) {
            return resolveBinaryExisting(resolved, fileName, binarySpec);
        }
        return resolveBinaryNonExisting(resolved, fileName, binarySpec);
    }

    private static BinaryInfo resolveBinaryDefault(final File projectDir, final String fileName,
                                                   final boolean deduce) {
        final File binDir = deduce ? binaryDirFromPrjDir(projectDir) : projectDir;
        final File binFile = fileName != null ? new File(binDir, fileName + ".xtc") : null;
        final boolean exists = binFile != null && binFile.exists();
        return makeBinaryInfo(binDir, binFile, exists);
    }

    private static BinaryInfo resolveBinaryExisting(final File resolved, final String fileName,
                                                    final File originalSpec) {
        if (resolved.isDirectory()) {
            final File binFile = fileName != null ? new File(resolved, fileName + ".xtc") : null;
            final boolean exists = binFile != null && binFile.exists();
            return makeBinaryInfo(resolved, binFile, exists);
        }

        final String ext = getExtension(resolved.getName());
        if (!"xtc".equals(ext)) {
            throw new IllegalArgumentException(
                    "Target destination " + originalSpec + " must use an .xtc extension");
        }
        return makeBinaryInfo(resolved.getParentFile(), resolved, true);
    }

    private static BinaryInfo resolveBinaryNonExisting(final File resolved, final String fileName, final File originalSpec) {
        validateParentExists(resolved, originalSpec);
        final String ext = getExtension(resolved.getName());
        if ("xtc".equals(ext)) {
            return new BinaryInfo(resolved.getParentFile(), resolved, false, null, 0L);
        }
        final File binFile = fileName != null ? new File(resolved, fileName + ".xtc") : null;
        return new BinaryInfo(resolved, binFile, false, null, 0L);
    }

    private static void validateParentExists(final File resolved, final File originalSpec) {
        File parent = resolved.getParentFile();
        while (parent != null && !parent.exists()) {
            parent = parent.getParentFile();
        }
        assert parent == null || parent.isDirectory();
        Objects.requireNonNull(parent, "Target destination " + originalSpec + " cannot be created");
    }

    private static BinaryInfo makeBinaryInfo(final File dir, final File file, final boolean exists) {
        final Version version = exists && file != null ? loadBinaryVersion(file).orElse(null) : null;
        final long timestamp = exists && file != null ? file.lastModified() : 0L;
        return new BinaryInfo(dir, file, exists, version, timestamp);
    }

    private static Optional<Version> loadBinaryVersion(final File file) {
        try {
            return Optional.ofNullable(new FileStructure(file).getModule().getVersion());
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private static ResourceInfo resolveResources(final List<File> resourceSpecs, final File sourceFile,
                                                 final File sourceDir, final File projectDir,
                                                 final boolean deduce) {
        if (resourceSpecs == null || resourceSpecs.isEmpty()) {
            return resolveResourcesDefault(sourceDir, projectDir, deduce);
        }

        validateResourceSpecs(resourceSpecs);

        // Use forSourceDir to avoid ModuleInfo recursion
        final List<File> defaultDirs = (sourceFile != null && sourceFile.exists())
                ? ResourceDir.forSourceDir(sourceDir, projectDir, deduce).getLocations()
                : List.of();

        final ResourceDir resDir = defaultDirs.isEmpty()
                ? new ResourceDir(resourceSpecs)
                : new ResourceDir(Stream.concat(resourceSpecs.stream(), defaultDirs.stream()).toList());

        return new ResourceInfo(resDir, resDir.getTimestamp());
    }

    private static void validateResourceSpecs(final List<File> specs) {
        for (final File file : specs) {
            if (file == null) {
                throw new IllegalArgumentException("A resource location is specified as null");
            }
            if (!file.exists()) {
                throw new IllegalArgumentException("The resource location " + file + " does not exist");
            }
        }
    }

    private static ResourceInfo resolveResourcesDefault(final File sourceDir, final File projectDir,
                                                        final boolean deduce) {
        // Use forSourceDir directly to avoid ModuleInfo recursion
        final ResourceDir resDir = Optional.ofNullable(sourceDir)
                .map(dir -> ResourceDir.forSourceDir(dir, projectDir, deduce))
                .orElse(NO_RESOURCES);
        return new ResourceInfo(resDir, resDir.getTimestamp());
    }

    private static String determineModuleName(final String foundName, final SourceInfo source,
                                              final BinaryInfo binary, final String fileName,
                                              final File projectDir) {
        if (foundName != null) {
            return foundName;
        }

        // Try source
        if (source.exists && source.file != null) {
            final String name = extractModuleName(source.file);
            if (name != null) {
                return name;
            }
        }

        // Try binary
        if (binary.exists && binary.file != null) {
            final String name = extractModuleNameFromBinary(binary.file);
            if (name != null) {
                return name;
            }
        }

        // Guess from file names
        if (source.file != null) {
            return removeExtension(source.file.getName());
        }
        if (binary.file != null) {
            return removeExtension(binary.file.getName());
        }
        if (fileName != null) {
            return fileName;
        }
        return projectDir.getName();
    }

    private static String extractModuleNameFromBinary(final File file) {
        try {
            return new FileStructure(file).getModuleId().getName();
        } catch (final IOException e) {
            return null;
        }
    }

    private static String extractShorterName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        final String name = isExplicitEcstasyFile(fileName) ? removeExtension(fileName) : fileName;
        final int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : null;
    }

    public File getFileSpec() {
        return fileSpec;
    }

    @SuppressWarnings("unused")
    public String getFileName() {
        return fileName;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public String getQualifiedModuleName() {
        return moduleName;
    }

    @SuppressWarnings("unused")
    public String getSimpleModuleName() {
        final int dot = moduleName.indexOf('.');
        return dot >= 0 ? moduleName.substring(0, dot) : moduleName;
    }

    @SuppressWarnings("unused")
    public boolean isModuleNameQualified() {
        return moduleName.indexOf('.') >= 0;
    }

    public boolean isSystemModule() {
        return moduleName.equals(ECSTASY_MODULE) || moduleName.equals(TURTLE_MODULE);
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isUpToDate() {
        return binary.exists
            && binary.timestamp > 0L
            && binary.timestamp >= source.timestamp
            && binary.timestamp >= resources.timestamp;
    }

    // ----- source accessors ----------------------------------------------------------------------

    public File getSourceFile() {
        return source.file;
    }

    public File getSourceDir() {
        return source.dir;
    }

    @SuppressWarnings("unused")
    public boolean isSourceTree() {
        return source.isTree;
    }

    @SuppressWarnings("unused")
    public long getSourceTimestamp() {
        return source.timestamp;
    }

    // ----- binary accessors ----------------------------------------------------------------------

    public File getBinaryFile() {
        return binary.file;
    }

    @SuppressWarnings("unused")
    public File getBinaryDir() {
        return binary.dir;
    }

    public Version getModuleVersion() {
        return binary.version;
    }

    @SuppressWarnings("unused")
    public long getBinaryTimestamp() {
        return binary.timestamp;
    }

    public ResourceDir getResourceDir() {
        return resources.dir;
    }

    @SuppressWarnings("unused")
    public long getResourceTimestamp() {
        return resources.timestamp;
    }

    /**
     * Build and return the parsed source tree. This is the only lazy operation
     * as parsing is expensive. Thread-safe via AtomicReference.
     */
    public Node getSourceTree(final ErrorListener errs) {
        final Node cached = sourceTreeRef.get();
        if (cached != null) {
            return cached;
        }
        final Node node = buildSourceTree(errs);
        if (node != null) {
            sourceTreeRef.compareAndSet(null, node);
        }
        return sourceTreeRef.get();
    }

    private Node buildSourceTree(final ErrorListener errs) {
        if (source.dir == null || source.file == null) {
            return null;
        }

        final var errors = errs != null ? errs : new ErrorList();
        final Node node;
        if (source.isTree) {
            final DirNode dirNode = new DirNode(null, new File(source.dir, fileName), source.file);
            dirNode.buildSourceTree();
            node = dirNode;
        } else {
            node = new FileNode(null, source.file);
        }

        // Process phases
        if (!runPhases(node, errors, Node::parse, Node::registerNames, Node::linkParseTrees)) {
            return null;
        }

        return node;
    }

    @SafeVarargs
    private static boolean runPhases(final Node node, final ErrorListener errs, final Consumer<Node>... phases) {
        node.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return false;
        }

        for (final Consumer<Node> phase : phases) {
            phase.accept(node);
            node.logErrors(errs);
            if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
                return false;
            }
        }
        return true;
    }

    // ----- Node classes --------------------------------------------------------------------------
    // TODO: This is in the middle of Module info and suddenly public?
    public abstract class Node implements ErrorListener {
        protected final DirNode parent;
        protected final File file;
        protected final ErrorList errors;
        protected ResourceDir resDir;

        protected Node(final DirNode parent, final File file) {
            this.parent = parent;
            this.file   = file;
            this.errors =  new ErrorList();
        }

        public DirNode parent() {
            return parent;
        }

        public File file() {
            return file;
        }

        public Node root() {
            return parent == null ? this : parent.root();
        }

        public FileNode module() {
            Node r = root();
            return r instanceof final DirNode d ? d.sourceNode() : (FileNode) r;
        }

        public ModuleInfo moduleInfo() {
            return ModuleInfo.this;
        }

        public int depth() {
            return parent == null ? 0 : 1 + parent.depth();
        }

        @SuppressWarnings("unused")
        public abstract ResourceDir resourceDir();

        public abstract void parse();

        public abstract void registerNames();

        abstract void linkParseTrees();

        public abstract String name();

        @SuppressWarnings("unused")
        public abstract String descriptiveName();

        public abstract Statement ast();

        public abstract TypeCompositionStatement type();

        @Override
        public boolean log(final ErrorInfo err) {
            return errs().log(err);
        }

        @Override
        public boolean isAbortDesired() {
            return errors.isAbortDesired();
        }

        @Override
        public boolean hasSeriousErrors() {
            return errors.hasSeriousErrors();
        }

        @Override
        public boolean hasError(final String code) {
            return errors.hasError(code);
        }

        public ErrorList errs() {
            return errors;
        }

        public void logErrors(final ErrorListener errs) {
            errors.getErrors().forEach(errs::log);
            errors.clear();
        }

        @SuppressWarnings("unused")
        protected String resourcePathPart() {
            return null;
        }
    }

    public class DirNode extends Node {
        private final File srcFile;
        private final Map<File, FileNode> clsNodes = new LinkedHashMap<>();
        private final List<DirNode> pkgNodes = new ArrayList<>();
        private final Map<String, Node> children = new HashMap<>();

        // TODO Lazy parsing.
        private @Nullable FileNode srcNode;

        DirNode(final DirNode parent, final File dir, final File srcFile) {
            super(parent, dir);
            this.srcFile = srcFile;
            // Note: srcNode is created lazily in sourceNode() to avoid 'this' escape
        }

        @SuppressWarnings("UnusedReturnValue")
        DirNode buildSourceTree() {
            final File thisDir = file();
            for (final File f : listFiles(thisDir)) {
                final String name = f.getName();
                if (f.isDirectory()) {
                    if (!new File(thisDir, name + ".x").exists() && name.indexOf('.') < 0) {
                        final DirNode child = new DirNode(this, f, null);
                        pkgNodes.add(child);
                        child.buildSourceTree();
                    }
                } else if (name.endsWith(".x")) {
                    final File subDir = new File(thisDir, removeExtension(name));
                    if (subDir.isDirectory()) {
                        final DirNode child = new DirNode(this, subDir, f);
                        pkgNodes.add(child);
                        child.buildSourceTree();
                    } else {
                        clsNodes.put(f, new FileNode(this, f));
                    }
                }
            }
            return this;
        }

        @Override
        public String name() {
            return file().getName();
        }

        @Override
        public String descriptiveName() {
            if (parent == null) {
                return "module " + name();
            }
            final var sb = new StringBuilder("package ").append(name());
            for (DirNode n = parent; n.parent != null; n = n.parent) {
                sb.insert(8, n.name() + '.');
            }
            return sb.toString();
        }

        @Override
        public ResourceDir resourceDir() {
            if (resDir == null) {
                final ResourceDir parentDir = parent == null ? ModuleInfo.this.getResourceDir() : parent.resourceDir();
                resDir = parentDir.getDirectory(resourcePathPart());
                if (resDir == null) {
                    resDir = NO_RESOURCES;
                }
            }
            return resDir;
        }

        @Override
        protected String resourcePathPart() {
            final String name = file().getName();
            final int dot = name.indexOf('.');
            return dot >= 0 ? name.substring(0, dot) : name;
        }

        @Override
        public void parse() {
            // Get the source node (creates it lazily from srcFile if available)
            var node = sourceNode();
            if (node == null) {
                // No source file - create a synthetic package node
                srcNode = new FileNode(this, "package " + file().getName() + "{}");
                node = srcNode;
            }
            node.parse();
            clsNodes.values().forEach(FileNode::parse);
            pkgNodes.forEach(DirNode::parse);
        }

        @Override
        public void registerNames() {
            sourceNode().registerNames();
            clsNodes.values().forEach(c -> {
                c.registerNames();
                registerName(c.name(), c);
            });
            pkgNodes.forEach(p -> {
                p.registerNames();
                registerName(p.name(), p);
            });
        }

        @SuppressWarnings("UnusedReturnValue")
        private boolean registerName(final String name, final Node node) {
            if (name == null) {
                return false;
            }
            if (children.containsKey(name)) {
                log(ERROR, DUP_NAME, new Object[]{name, descriptiveName()}, null);
                return false;
            }
            return children.put(name, node) == null;
        }

        @Override
        void linkParseTrees() {
            Node pkg = sourceNode();
            if (pkg == null) {
                log(ERROR, MISSING_PKG_NODE, new Object[]{descriptiveName()}, null);
                return;
            }
            final TypeCompositionStatement type = pkg.type();
            clsNodes.values().forEach(c -> type.addEnclosed(c.ast()));
            pkgNodes.forEach(p -> { type.addEnclosed(p.sourceNode().ast()); p.linkParseTrees(); });
        }

        @Override
        public Statement ast() { return srcNode != null ? srcNode.ast() : null; }

        @Override
        public TypeCompositionStatement type() { return srcNode != null ? srcNode.type() : null; }

        @Override
        public ErrorList errs() { return srcNode != null ? srcNode.errs() : null; }

        @Override
        public void logErrors(final ErrorListener errs) {
            super.logErrors(errs);
            if (srcNode != null) {
                srcNode.logErrors(errs);
            }
            clsNodes.values().forEach(n -> n.logErrors(errs));
            pkgNodes.forEach(n -> n.logErrors(errs));
        }

        @SuppressWarnings("unused")
        public File sourceFile() {
            return srcFile;
        }

        public FileNode sourceNode() {
            if (srcNode == null && srcFile != null) {
                srcNode = new FileNode(this, srcFile);
            }
            return srcNode;
        }

        @SuppressWarnings("unused")
        public List<DirNode> packageNodes() {
            return pkgNodes;
        }

        @SuppressWarnings("unused")
        public Map<File, FileNode> classNodes() {
            return clsNodes;
        }

        public Map<String, Node> children() {
            return children;
        }

        @Override
        public String toString() {
            return parent == null ? '/' + name() + '/' : parent + name() + '/';
        }
    }

    public class FileNode extends Node {
        private String text;
        private org.xvm.compiler.Source src;
        private Statement stmtAST;
        private TypeCompositionStatement stmtType;

        FileNode(final DirNode parent, final File file) {
            super(parent, file);
        }

        FileNode(final DirNode parent, final String code) {
            super(parent, null);
            this.text = code;
        }

        @Override
        public int depth() {
            int d = super.depth();
            return (parent != null && parent.parent == null) ? d - 1 : d;
        }

        @Override
        public String name() {
            if (stmtType != null) {
                return stmtType.getName();
            }
            if (file != null) {
                String n = file.getName();
                return n.endsWith(".x") ? n.substring(0, n.length() - 2) : n;
            }
            return parent != null ? parent.file().getParent() : "<unknown>";
        }

        @Override
        public String descriptiveName() {
            return stmtType == null
                    ? file.getAbsolutePath()
                    : type().getCategory().getId().TEXT + ' ' + name();
        }

        @Override
        public ResourceDir resourceDir() {
            if (resDir != null) {
                return resDir;
            }
            // Non-package source files use their parent's resource dir
            if (!isPackageSource()) {
                resDir = parent.resourceDir();
            // Package source at root or immediate child of root uses module's resource dir
            } else if (parent == null || parent.parent == null) {
                resDir = ModuleInfo.this.getResourceDir();
            // Package source deeper in tree skips to grandparent (the containing package)
            } else {
                resDir = parent.parent.resourceDir();
            }
            return resDir;
        }

        public boolean isPackageSource() {
            return parent == null || this == parent.sourceNode();
        }

        public char[] content() {
            if (text != null) return text.toCharArray();
            try {
                return readFileChars(file);
            } catch (final IOException e) {
                log(ERROR, READ_FAILURE, new Object[]{file}, null);
                return new char[0];
            }
        }

        public org.xvm.compiler.Source source() {
            if (src == null) {
                src = new org.xvm.compiler.Source(this);
            }
            return src;
        }

        /**
         * Resolves a resource path to either a file or directory.
         *
         * @param path absolute (starts with "/") or relative path; trailing "/" requires directory result
         * @return the resolved resource entry, or empty if not found or invalid path
         */
        public Optional<ResourceDir.ResourceEntry> resolveResource(final String path) {
            final boolean isAbsolute = path.startsWith("/");
            final String stripped = isAbsolute ? path.substring(1) : path;
            if (stripped.startsWith("/")) {
                return Optional.empty();
            }

            final boolean requireDir = stripped.endsWith("/");
            final String clean = requireDir ? stripped.substring(0, stripped.length() - 1) : stripped;
            if (clean.endsWith("/")) {
                return Optional.empty();
            }

            final ResourceDir start = isAbsolute ? ModuleInfo.this.getResourceDir() : resourceDir();
            if (start == null) {
                return Optional.empty();
            }

            final List<String> segments = clean.isEmpty() ? List.of() : List.of(clean.split("/"));
            return Optional.ofNullable(navigateSegments(start, segments, requireDir));
        }

        private ResourceDir.ResourceEntry navigateSegments(
                final ResourceDir dir, final List<String> segments, final boolean requireDir) {
            if (segments.isEmpty()) {
                return new ResourceDir.ResourceEntry.DirEntry(dir);
            }

            final String segment = segments.getFirst();
            final List<String> rest = segments.subList(1, segments.size());

            if (segment.isEmpty() || ".".equals(segment)) {
                return navigateSegments(dir, rest, requireDir);
            }
            if ("..".equals(segment)) {
                final ResourceDir parent = dir.getParent().orElse(null);
                return parent != null ? navigateSegments(parent, rest, requireDir) : null;
            }
            return dir.find(segment).map(entry -> switch (entry) {
                case ResourceDir.ResourceEntry.DirEntry(final var sub) -> navigateSegments(sub, rest, requireDir);
                case ResourceDir.ResourceEntry.FileEntry _ -> rest.isEmpty() && !requireDir ? entry : null;
            }).orElse(null);
        }

        @Override
        void linkParseTrees() {
            // empty
        }

        @Override
        public void parse() {
            final var s = source();
            try {
                stmtAST = new Parser(s, this).parseSource();
            } catch (final CompilerException e) {
                if (!hasSeriousErrors()) {
                    log(FATAL, Parser.FATAL_ERROR, null, s, s.getPosition(), s.getPosition());
                }
            }
        }

        @Override
        public void registerNames() {
            if (stmtAST == null) {
                return;
            }
            stmtType = stmtAST instanceof final TypeCompositionStatement t
                    ? t
                    : (TypeCompositionStatement) ((StatementBlock) stmtAST).getStatements().getLast();
        }

        @Override
        public Statement ast() {
            return stmtAST;
        }

        @Override
        public TypeCompositionStatement type() {
            return stmtType;
        }

        @Override
        public String toString() {
            String base = parent != null ? parent.toString() : "";
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + name() + ".x";
        }
    }

    // ----- static helpers ------------------------------------------------------------------------
    private static String extractModuleName(final File file) {
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        String name = file.getName();
        if (isExplicitSourceFile(name)) {
            try {
                return new Parser(new org.xvm.compiler.Source(file), ErrorListener.BLACKHOLE).parseModuleNameIgnoreEverythingElse();
            } catch (final CompilerException | IOException e) {
                return null;
            }
        }
        if (isExplicitCompiledFile(name)) {
            try {
                return new FileStructure(file).getModuleId().getName();
            } catch (final IOException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean isExplicitEcstasyFile(final String name) {
        String ext = getExtension(name);
        return "x".equalsIgnoreCase(ext) || "xtc".equalsIgnoreCase(ext);
    }

    public static boolean isExplicitSourceFile(final String name) {
        return "x".equalsIgnoreCase(getExtension(name));
    }

    public static boolean isExplicitCompiledFile(final String name) {
        return "xtc".equalsIgnoreCase(getExtension(name));
    }

    public static List<File> sourceFiles(final File dir) {
        return listFiles(dir, "x");
    }

    public static List<File> compiledFiles(final File dir) {
        return listFiles(dir, "xtc");
    }

    public static File projectDirFromSubDir(final File dir) {
        final String name = dir.getName();
        if ("build".equalsIgnoreCase(name) || "target".equalsIgnoreCase(name)) {
            return parentOf(dir).orElse(dir);
        }
        File cur = dir;
        cur = stepUpIf(cur, "x", "xtc", "ecstasy");
        cur = stepUpIf(cur, "main", "test");
        cur = stepUpIf(cur, "src", "source");
        return cur;
    }

    private static File stepUpIf(final File dir, final String... names) {
        return Stream.of(names).anyMatch(n -> n.equalsIgnoreCase(dir.getName()))
                ? parentOf(dir).orElse(dir)
                : dir;
    }

    public static File sourceDirFromPrjDir(final File prjDir) {
        if (!sourceFiles(prjDir).isEmpty()) {
            return prjDir;
        }
        File cur = descendInto(prjDir, "src", "source");
        if (!sourceFiles(cur).isEmpty()) return cur;
        cur = descendInto(cur, "main");
        if (!sourceFiles(cur).isEmpty()) return cur;
        cur = descendInto(cur, "x", "xtc", "ecstasy");
        if (!sourceFiles(cur).isEmpty()) return cur;
        return prjDir;
    }

    private static File descendInto(final File dir, final String... names) {
        return Stream.of(names)
                .map(n -> new File(dir, n))
                .filter(File::isDirectory)
                .findFirst()
                .orElse(dir);
    }

    public static File binaryDirFromPrjDir(final File prjDir) {
        return Stream.of("build", "target")
                .map(n -> new File(prjDir, n))
                .filter(File::isDirectory)
                .findFirst()
                .orElseGet(() -> !sourceFiles(prjDir).isEmpty() || !compiledFiles(prjDir).isEmpty()
                        ? prjDir
                        : new File(prjDir, "build"));
    }

    private static void visitTree(final File dir, final String ext, final Consumer<File> visitor) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Stream.of(files)
                .filter(f -> ext == null || ext.equalsIgnoreCase(getExtension(f.getName())))
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(f -> {
                    if (f.isDirectory()) visitTree(f, ext, visitor);
                    else visitor.accept(f);
                });
    }

    @Override
    public String toString() {
        return "ModuleInfo[module=" + moduleName + ", project=" + projectDir + "]";
    }
}
