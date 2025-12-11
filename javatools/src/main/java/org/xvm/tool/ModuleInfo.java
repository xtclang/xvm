package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import java.util.stream.Stream;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;


import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;

import static org.xvm.tool.Launcher.DUP_NAME;
import static org.xvm.tool.Launcher.MISSING_PKG_NODE;
import static org.xvm.tool.Launcher.READ_FAILURE;

import static org.xvm.tool.ResourceDir.NoResources;

import static org.xvm.util.Handy.dateString;
import static org.xvm.util.Handy.getExtension;
import static org.xvm.util.Handy.listFiles;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.readFileChars;
import static org.xvm.util.Handy.removeExtension;
import static org.xvm.util.Handy.resolveFile;


/**
 * Information gleaned about a module from a single specified file. This is a lazily populated
 * structure, not a point-in-time snapshot; as a result, in the presence of realtime changes
 * occurring to the module source/resource files or compiled files after this object is
 * instantiated, this can not be depended upon to reflect either the snapshot state of the
 * module as it was when the ModuleInfo was instantiated, nor the snapshot state of the module
 * as it is right now.
 */
public class ModuleInfo {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the module information from the specified file.
     *
     * @param fileSpec the file to analyze, which may or may not exist
     * @param deduce   pass true to enable the algorithm to deduce/search for likely locations
     */
    public ModuleInfo(final File fileSpec, final boolean deduce) {
        this(fileSpec, deduce, null, null);
    }

    /**
     * Construct the module information from the specified file, with no resource path.
     *
     * @param fileSpec   the file or directory to analyze, which may or may not exist
     * @param deduce     pass true to enable the algorithm to deduce/search for likely locations
     * @param binarySpec the file or directory which represents the target of the binary; as
     *                   provided to the compiler using the "-o" command line switch; may be null
     */
    public ModuleInfo(final File fileSpec, final boolean deduce, final File binarySpec) {
        this(fileSpec, deduce, List.of(), binarySpec);
    }

    /**
     * Construct the module information from the specified file.
     *
     * @param fileSpec      the file or directory to analyze, which may or may not exist
     * @param deduce        pass true to enable the algorithm to deduce/search for likely locations
     * @param resourceSpecs a list of files and/or directories which represent (in aggregate)
     *                      the resource path; null indicates that the default resources
     *                      location should be used, while an empty list explicitly indicates
     *                      that there is no resource path; as provided to the compiler using
     *                      the "-rp" command line switch
     * @param binarySpec    the file or directory which represents the target of the binary; as
     *                      provided to the compiler using the "-o" command line switch
     */
    public ModuleInfo(final File fileSpec, final boolean deduce, final List<File> resourceSpecs, final File binarySpec) {
        if (fileSpec == null) {
            throw new IllegalArgumentException("A file specification is required for the module");
        }

        this.fileSpec = fileSpec;
        this.deduce   = deduce;

        // start by figuring out the directory to work from and the file name to look for (which may
        // not be specified!), based on some combination of the current working directory and the
        // fileSpec; if the fileSpec refers to something that exists, it's either a directory or a
        // file, and the file may be a source or a compiled binary file; if the fileSpec refers to
        // a non-existent location, it might indicate either a directory or a file
        File resolved = resolveFile(fileSpec);
        File dirSpec  = resolved.getParentFile();
        fileName = resolved.getName();
        if (fileName.isEmpty()) {
            fileName = null;
        }
        if (resolved.isDirectory()) {
            // it's possible that the module name was specified without the ".x" extension, which
            // would match the name of the subdirectory containing the package and class files
            // within the module; this should be obvious because there will also be a source file
            if (dirSpec == null || fileName != null && !new File(dirSpec, fileName + ".x").exists()) {
                // we don't know the file name specified, because the name was of a directory
                dirSpec  = resolved;
                fileName = null;
            }
        }
        if (dirSpec == null || !dirSpec.isDirectory()) {
            throw new IllegalArgumentException("Unable to identify a module directory for " + fileSpec);
        }

        // the specified name may be a source file name, binary file name, qualified module name,
        // or simple module name
        String shorterName = null;
        if (fileName != null) {
            if (isExplicitEcstasyFile(fileName)) {
                fileName = removeExtension(fileName);
            }

            int dot = fileName.indexOf('.');
            if (dot > 0) {
                shorterName = fileName.substring(0, dot);
            }
        }

        // working from the dirSpec, attempt to identify the project directory, given that the
        // dirSpec could be any of:
        //   1) project dir
        //   2) any of ./src/main/x (etc.) dir
        //   3) any source dir nested somewhere under ./src/main/x (etc.) dir
        //   4) any of ./build or ./target dir
        // we may be able to identify the source and/or binary directories as a side effect of this
        // search, but the main thing we're looking for here is the project directory
        File curDir = null;     // no project dir identified, but begin looking from here
        do {
            var srcFiles = sourceFiles(dirSpec);
            var binFiles = compiledFiles(dirSpec);
            if (!srcFiles.isEmpty() && !binFiles.isEmpty()) {
                // we're in a directory with both source and compiled files; assume that this is
                // where we'll find everything that we need
                projectDir = binaryDir = sourceDir = dirSpec;
                break;
            }

            if (srcFiles.isEmpty() && !binFiles.isEmpty()) {
                // we're in a directory with compiled files; it's probably a "build" directory under
                // the project directory
                binaryDir = dirSpec;
                break;
            }

            if (srcFiles.isEmpty()) {
                // no source files, no binary files, so start looking for the project directory from this point
                curDir = dirSpec;
            } else {
                File   searchDir = dirSpec;
                String srcName   = fileName    == null ? null : fileName    + ".x";
                String srcName2  = shorterName == null ? null : shorterName + ".x";
                do {
                    sourceDir = searchDir;
                    if (srcName == null) {
                        if (srcFiles.size() == 1) {
                            File file = srcFiles.getFirst();
                            moduleName = extractModuleName(file);
                            if (moduleName != null) {
                                // we found "the" module
                                fileName      = removeExtension(file.getName());
                                sourceFile    = file;
                                sourceContent = Content.Module;
                                break;
                            }
                        }
                    } else {
                        if (srcFiles.stream().anyMatch(f -> !f.isDirectory()
                                && (f.getName().equals(srcName) || f.getName().equals(srcName2)))) {
                            break;
                        }
                    }

                    searchDir = searchDir.getParentFile();
                    if (searchDir == null) {
                        break;
                    }

                    srcFiles = sourceFiles(searchDir);
                } while (deduce && !srcFiles.isEmpty());
            }
        } while (false);

        if (projectDir == null) {
            if (sourceDir != null) {
                projectDir = deduce ? projectDirFromSubDir(sourceDir) : sourceDir;
            } else if (binaryDir != null) {
                projectDir = deduce ? projectDirFromSubDir(binaryDir) : binaryDir;
            } else {
                projectDir = deduce ? projectDirFromSubDir(curDir) : curDir;
            }
        }

        // at this point, there's a project directory; if we have not already done so, locate the
        // source file for the module from the project directory
        if (sourceFile == null) {
            if (deduce && sourceDir == null) {
                sourceDir = sourceDirFromPrjDir(projectDir);
            }

            if (sourceDir != null) {
                if (fileName == null) {
                    final var files = sourceFiles(sourceDir);
                    if (files.size() == 1) {
                        fileName = removeExtension(files.getFirst().getName());
                    }
                }

                if (fileName != null) {
                    sourceFile = new File(sourceDir, fileName + ".x");
                }
            }
        }

        if (sourceFile == null || !sourceFile.exists()) {
            sourceStatus = Status.NotExists;
        } else {
            sourceStatus = Status.Exists;

            if (sourceDir == null) {
                sourceDir = sourceFile.getParentFile();
            }

            sourceIsTree = new File(sourceDir, fileName).exists();
        }

        if (binarySpec != null) {
            // if it exists, it must either be an .xtc file or a directory
            final var resolvedBinary = resolveFile(binarySpec);
            if (resolvedBinary.exists()) {
                if (resolvedBinary.isDirectory()) {
                    binaryDir = resolvedBinary;
                } else {
                    String sExt = getExtension(resolvedBinary.getName());
                    if ("xtc".equals(sExt)) {
                        binaryFile   = resolvedBinary;
                        binaryDir    = resolvedBinary.getParentFile();
                        binaryStatus = Status.Exists;
                    } else {
                        throw new IllegalArgumentException("Target destination " + resolvedBinary
                                + " must use an .xtc extension");
                    }
                }
            } else {
                // if it doesn't exist, it needs to be located somewhere under some directory
                // that does exist
                File fileParent = resolvedBinary.getParentFile();
                while (true) {
                    if (fileParent == null) {
                        throw new IllegalArgumentException("Target destination " + resolvedBinary
                            + " is illegal because it does not exist and cannot be created");
                    }

                    if (fileParent.exists()) {
                        if (!fileParent.isDirectory()) {
                            throw new IllegalArgumentException("Target destination "
                                    + resolvedBinary + " is illegal because parent file "
                                    + fileParent + " is not a directory");
                        }

                        break;
                    }

                    fileParent = fileParent.getParentFile();
                }

                String sExt = getExtension(resolvedBinary.getName());
                if ("xtc".equals(sExt)) {
                    binaryFile   = resolvedBinary;
                    binaryDir    = resolvedBinary.getParentFile();
                    binaryStatus = Status.NotExists;
                } else {
                    binaryDir = resolvedBinary;
                }
            }
        }

        if (resourceSpecs != null && !resourceSpecs.isEmpty()) {
            for (File file : resourceSpecs) {
                if (file == null) {
                    throw new IllegalArgumentException("A resource location is specified as null");
                }

                if (!file.exists()) {
                    throw new IllegalArgumentException("The resource location " + file + " does not exist");
                }
            }

            // merge the resource directory lists, with the specified ones having priority
            final var dftResDirs = getResourceDir().getLocations();
            final var allResDirs = new ArrayList<>(resourceSpecs);
            if (!dftResDirs.isEmpty()) {
                allResDirs.addAll(dftResDirs);
            }

            resourceDir = new ResourceDir(allResDirs);
        }
    }


    // ----- general -------------------------------------------------------------------------------

    /**
     * @return the "file spec" that the ModuleInfo started from
     */
    public File getFileSpec() {
        return fileSpec;
    }

    /**
     * @return the file name (the short name without an extension) that the ModuleInfo is using
     */
    @SuppressWarnings("unused")
    public String getFileName() {
        return fileName;
    }

    /**
     * @return the inferred "project directory", which may be one or more steps above the
     *         directory containing the module source code
     */
    public File getProjectDir() {
        return projectDir;
    }

    /**
     * @return True if the module binary exists and is at least as up-to-date as the existent source
     *         and resource files and directories
     */
    public boolean isUpToDate() {
        long binTimestamp = getBinaryTimestamp();
        return binTimestamp >  0L
            && binTimestamp >= getSourceTimestamp()
            && binTimestamp >= getResourceTimestamp();
    }

    /**
     * @return True if the module name uses a qualified format
     */
    @SuppressWarnings("unused")
    public boolean isModuleNameQualified() {
        return getQualifiedModuleName().indexOf('.') >= 0;
    }

    /**
     * @return the full module name, which may or may not be qualified
     */
    public String getQualifiedModuleName() {
        if (moduleName != null) {
            return moduleName;
        }

        // Try to extract module name from source file
        if (sourceStatus == Status.Exists && sourceContent != Content.Invalid) {
            moduleName = extractModuleName(sourceFile);
            if (moduleName != null) {
                sourceContent = Content.Module;
                return moduleName;
            }
            sourceContent = Content.Invalid;
        }

        // Try to get the module name from the compiled module file
        if (getBinaryFile() != null && binaryContent != Content.Invalid && loadBinaryFile()) {
            return moduleName;
        }

        // Fall back to guessing from file/directory names
        if (sourceFile != null) {
            moduleName = removeExtension(sourceFile.getName());
        } else if (binaryFile != null) {
            moduleName = removeExtension(binaryFile.getName());
        } else {
            moduleName = Objects.requireNonNullElseGet(fileName, () -> projectDir.getName());
        }
        return moduleName;
    }

    /**
     * Determine if the specified module node represents a system module.
     *
     * @return true iff this module is for the Ecstasy or native prototype module
     */
    public boolean isSystemModule() {
        String sModule = getQualifiedModuleName();
        return sModule.equals(ECSTASY_MODULE)
            || sModule.equals(TURTLE_MODULE);
    }

    /**
     * @return the unqualified module name
     */
    @SuppressWarnings("unused")
    public String getSimpleModuleName() {
        String moduleName = getQualifiedModuleName();
        int firstDot = moduleName.indexOf('.');
        return firstDot >= 0 ? moduleName.substring(0, firstDot) : moduleName;
    }


    // ----- source --------------------------------------------------------------------------------

    /**
     * @return the module source file, or null if none can be resolved
     */
    public File getSourceFile() {
        return sourceFile;
    }

    /**
     * @return the module source file, or null if none can be resolved
     */
    public File getSourceDir() {
        return sourceDir;
    }

    /**
     * @return true iff the source code appears to be organized using the multiple-file,
     *         hierarchical, name-spaced tree
     */
    public boolean isSourceTree() {
        return sourceIsTree;
    }

    /**
     * @return lazily computed and cached latest-timestamp of the module source file or source
     *         directory contents, or 0 if none
     */
    public long getSourceTimestamp() {
        File fileSrc = getSourceFile();
        if (fileSrc != null && fileSrc.exists() && sourceTimestamp == 0L) {
            sourceTimestamp = sourceFile.lastModified();
            if (isSourceTree()) {
                File subDir = new File(sourceFile.getParentFile(), removeExtension(sourceFile.getName()));
                if (subDir.isDirectory()) {
                    sourceTimestamp = collectFiles(subDir, "x")
                            .mapToLong(File::lastModified)
                            .reduce(sourceTimestamp, Math::max);
                }
            }
        }

        return sourceTimestamp;
    }


    // ----- resources -----------------------------------------------------------------------------

    /**
     * @return the ResourceDir object representing the root resource directory
     */
    public ResourceDir getResourceDir() {
        if (resourceDir == null) {
            File sourceFile = getSourceFile();
            resourceDir = sourceFile.exists() ? ResourceDir.forSource(sourceFile, deduce) : NoResources;
        }

        return resourceDir;
    }

    /**
     * @return lazily computed and cached latest-timestamp of the resource directory contents,
     *         or 0 if none
     */
    public long getResourceTimestamp() {
        if (resourceTimestamp == 0L) {
            resourceTimestamp = getResourceDir().getTimestamp();
        }

        return resourceTimestamp;
    }


    // ----- binary --------------------------------------------------------------------------------

    /**
     * @return the file indicating the compiled form of the module, or null if the file cannot be
     *         determined, such as when the project "./build/" or "./target/" directory is missing
     */
    public File getBinaryFile() {
        if (binaryFile == null) {
            binaryFile   = new File(getBinaryDir(), fileName + ".xtc");
            binaryStatus = binaryFile.exists() ? Status.Exists : Status.NotExists;
        }

        return binaryFile;
    }

    /**
     * @return the directory that should contain the compiled form of the module, or null if there
     *         is no existent directory that should contain the binary, such as when the project
     *         "./build/" or "./target/" directory is missing
     */
    public File getBinaryDir() {
        if (binaryDir == null) {
            binaryDir = binaryFile == null
                    ? (deduce ? binaryDirFromPrjDir(projectDir) : projectDir)
                    : binaryFile.getParentFile();
        }

        return binaryDir;
    }

    /**
     * @return return the version of the compiled module, or null if either the compiled module
     *         does not exist or if it has no version
     */
    public Version getModuleVersion() {
        if (binaryVersion == null) {
            loadBinaryFile();
        }

        return binaryVersion;
    }

    /**
     * Attempt to read the compiled form of the module, extracting information from it including the
     * module name and version.
     *
     * @return true iff the file exists and was successfully loaded and parsed
     */
    private boolean loadBinaryFile() {
        if (binaryStatus != Status.NotExists && binaryContent == Content.Unknown) {
            File file = getBinaryFile();
            if (file != null && file.exists()) {
                binaryStatus  = Status.Exists;
                binaryContent = Content.Invalid;
                try {
                    FileStructure struct = new FileStructure(file);
                    moduleName    = struct.getModuleId().getName();
                    binaryVersion = struct.getModule().getVersion();
                    binaryContent = Content.Module;
                    return true;
                } catch (Exception ignore) {}
            } else {
                binaryStatus = Status.NotExists;
            }
        }

        return false;
    }

    /**
     * @return lazily computed and cached timestamp of the compiled module file, or 0 if none
     */
    public long getBinaryTimestamp() {
        if (binaryTimestamp == 0L) {
            File file = getBinaryFile();
            if (file != null && file.exists()) {
                binaryTimestamp = file.lastModified();
            }
        }

        return binaryTimestamp;
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Module(name="         + (moduleName == null ? "<unknown>" : moduleName)
             + ", fileSpec="          + fileSpec
             + ", fileName="          + fileName
             + ", moduleName="        + (moduleName == null ? "<unknown>" : moduleName)
             + ", projectDir="        + projectDir
             + ", sourceStatus="      + sourceStatus
             + ", sourceDir="         + sourceDir
             + ", sourceIsTree="      + sourceIsTree
             + ", sourceFile="        + sourceFile
             + ", sourceContent="     + sourceContent
             + ", sourceTimestamp="   + (sourceTimestamp == 0 ? "<unknown>" : dateString(sourceTimestamp))
             + ", resourceDir="       + resourceDir == null ? "<unknown>" : resourceDir
             + ", resourceTimestamp=" + (resourceTimestamp == 0 ? "<unknown>" : dateString(resourceTimestamp))
             + ", binaryStatus="      + binaryStatus
             + ", binaryDir="         + binaryDir == null ? "<unknown>" : binaryDir
             + ", binaryFile="        + binaryFile == null ? "<unknown>" : binaryFile
             + ", binaryVersion="     + binaryVersion == null ? "<unknown>" : binaryVersion
             + ", binaryContent="     + binaryContent
             + ", binaryTimestamp="   + (binaryTimestamp == 0 ? "<unknown>" : dateString(binaryTimestamp));
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Recursively collect all files matching the optional extension within the specified directory,
     * returning them as a stream in a stable, repeatable order.
     *
     * @param dir  a directory
     * @param ext  an optional extension to match; otherwise null
     *
     * @return a stream of matching non-directory files
     */
    private Stream<File> collectFiles(final File dir, final String ext) {
        final var children = new TreeMap<String, File>(String.CASE_INSENSITIVE_ORDER);
        for (File child : listFiles(dir)) {
            String name = child.getName();
            if (ext == null || ext.equalsIgnoreCase(getExtension(name))) {
                children.put(name, child);
            }
        }

        return children.values().stream()
                .flatMap(child -> child.isDirectory()
                        ? collectFiles(child, ext)
                        : Stream.of(child));
    }


    // ----- source tree ---------------------------------------------------------------------------

    /**
     * When working with a source code tree, and given a "module file", produce a source tree of the desired processing stage.
     *
     * @param errs  an optional error listener
     *
     * @return the root {@link Node} of the tree, or null if the ModuleInfo does not know the source
     *         location, or if serious errors occur loading the source tree
     */
    public Node getSourceTree(final ErrorListener errs) {
        if (sourceNode != null) {
            return sourceNode;
        }

        File srcDir  = getSourceDir();
        File srcFile = getSourceFile();
        if (srcDir == null || srcFile == null) {
            return null;
        }
        
        if (isSourceTree()) {
            assert fileName != null;
            File subDir = new File(srcDir, fileName);
            assert subDir.exists();
            DirNode dirNode = new DirNode(/*parent=*/null, subDir, srcFile);
            dirNode.buildSourceTree();
            sourceNode = dirNode;
        } else {
            sourceNode = new FileNode(null, srcFile);
        }
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return null;
        }

        sourceNode.parse();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return null;
        }

        sourceNode.registerNames();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return null;
        }

        sourceNode.linkParseTrees();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return null;
        }

        return sourceNode;
    }

    /**
     * Represents either a module/package or a class source node.
     */
    public abstract class Node
            implements ErrorListener {
        /**
         * Construct a Node.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        protected Node(final DirNode parent, final File file) {
            // at least one of the parameters is required
            assert parent != null || file != null;

            m_parent       = parent;
            m_file         = file;
        }

        /**
         * @return the parent of this node
         */
        public DirNode parent() {
            return m_parent;
        }

        /**
         * @return the root node
         */
        public Node root() {
            return m_parent == null ? this : m_parent.root();
        }

        /**
         * @return the node represent the module
         */
        public FileNode module() {
            Node rootNode = root();
            if (rootNode instanceof DirNode rootDir) {
                return rootDir.sourceNode();
            } else {
                return (FileNode) rootNode;
            }
        }

        /**
         * @return the ModuleInfo for the module
         */
        public ModuleInfo moduleInfo() {
            return ModuleInfo.this;
        }

        /**
         * @return the depth of this node
         */
        public int depth() {
            return parent() == null ? 0 : 1 + parent().depth();
        }

        /**
         * @return the file that this node represents
         */
        public File file() {
            return m_file;
        }

        /**
         * @return the ResourceDir for this node; never null
         */
        @SuppressWarnings("unused")
        public abstract ResourceDir resourceDir();

        /**
         * @return the child directory name for this node's ResourceDir compared to its parent's,
         *         or null to indicate that the parent's ResourceDir should be used
         */
        @SuppressWarnings("unused")
        protected String resourcePathPart() {
            return null;
        }

        /**
         * Load and parse the source code, as necessary.
         */
        public abstract void parse();

        /**
         * Collect the various top-level type names within the module.
         */
        public abstract void registerNames();

        /**
         * Link the various nodes of the module together
         */
        public void linkParseTrees() {
        }

        /**
         * @return the name of this node
         */
        public abstract String name();

        /**
         * @return a descriptive name for this node
         */
        @SuppressWarnings("unused")
        public abstract String descriptiveName();

        /**
         * @return the parsed AST from this node
         */
        public abstract Statement ast();

        /**
         * @return the type (from the parsed AST) of this node
         */
        public abstract TypeCompositionStatement type();

        @Override
        public boolean log(final ErrorInfo err) {
            return errs().log(err);
        }

        @Override
        public boolean isAbortDesired() {
            return m_errs != null && m_errs.isAbortDesired();
        }

        @Override
        public boolean hasSeriousErrors() {
            return m_errs != null && m_errs.hasSeriousErrors();
        }

        @Override
        public boolean hasError(final String sCode) {
            return m_errs != null && m_errs.hasError(sCode);
        }

        /**
         * @return the list containing any errors accumulated on (or under) this node
         */
        public ErrorList errs() {
            ErrorList errs = m_errs;
            if (errs == null) {
                m_errs = errs = new ErrorList(341);
            }
            return errs;
        }

        /**
         * Log any errors accumulated on (or under) this node
         */
        public void logErrors(final ErrorListener errs) {
            ErrorList deferred = m_errs;
            if (deferred != null) {
                for (ErrorInfo err : deferred.getErrors()) {
                    errs.log(err);
                }
                deferred.clear();
            }
        }


        // ----- fields ------------------------------------------------------------------------

        /**
         * The parent node, or null.
         */
        protected DirNode m_parent;

        /**
         * The file that this node is based on.
         */
        protected File m_file;

        /**
         * The cached ResourceDir for this Node.
         */
        protected ResourceDir m_resdir;

        /**
         * The error list that buffers errors for the file node, if any.
         */
        private ErrorList m_errs;
    }

    /**
     * A DirNode represents a source directory, which corresponds to a module or package.
     */
    public class DirNode
            extends Node {
        /**
         * Construct the root DirNode.
         *
         * @param parent   the parent node, which is null for the root node
         * @param dir      the directory that this node will represent
         * @param fileSrc  the file for the package.x file (or null if it does not exist)
         */
        DirNode(final DirNode parent, final File dir, final File fileSrc) {
            super(parent, dir);
            assert dir.isDirectory();

            if (fileSrc != null) {
                m_fileSrc = fileSrc;
                m_nodeSrc = new FileNode(this, fileSrc);
            }
        }

        /**
         * Build a subtree of nodes that are contained within this node.
         */
        void buildSourceTree() {
            File thisDir = file();
            for (File file : listFiles(thisDir)) {
                String name = file.getName();
                if (file.isDirectory()) {
                    // if the directory has no corresponding ".x" file, then it is an implied package
                    if (!(new File(thisDir, name + ".x")).exists() && name.indexOf('.') < 0) {
                        DirNode child = new DirNode(this, file, null);
                        packageNodes().add(child);
                        child.buildSourceTree();
                    }
                } else if (name.endsWith(".x")) {
                    // if there is a directory by the same name (minus the ".x"), then recurse to create
                    // a subtree
                    File subDir = new File(thisDir, removeExtension(name));
                    if (subDir.exists() && subDir.isDirectory()) {
                        // create a subtree
                        DirNode child = new DirNode(this, subDir, file);
                        packageNodes().add(child);
                        child.buildSourceTree();
                    } else {
                        // it's a source file
                        classNodes().put(file, new FileNode(this, file));
                    }
                }
            }
        }

        /**
         * @return the simple package name, or if this is a module, the fully qualified module name
         */
        @Override
        public String name() {
            return file().getName();
        }

        @Override
        public String descriptiveName() {
            if (parent() == null) {
                return "module " + name();
            }

            final var sb = new StringBuilder()
                .append("package ")
                .append(name());

            DirNode node = parent();
            while (node.parent() != null) {
                sb.insert(8, node.name() + '.');
                node = node.parent();
            }

            return sb.toString();
        }

        @Override
        public ResourceDir resourceDir() {
            if (m_resdir == null) {
                DirNode     parent    = parent();
                ResourceDir parentDir = parent == null
                        ? ModuleInfo.this.getResourceDir()
                        : parent.resourceDir();
                m_resdir = parentDir.getDirectory(resourcePathPart());

                // if no resources could be found, use an empty ResourceDir; this is helpful because
                // we can't "cache" the null result, and the caller doesn't have to check for null,
                // either
                if (m_resdir == null) {
                    m_resdir = NoResources;
                }
            }
            return m_resdir;
        }

        @Override
        protected String resourcePathPart() {
            String name = file().getName();
            int    dot  = name.indexOf('.');
            return dot >= 0 ? name.substring(0, dot) : name;
        }

        /**
         * Parse this node and all nodes it contains.
         */
        @Override
        public void parse() {
            if (m_nodeSrc == null) {
                // provide a default implementation
                assert m_parent != null;
                m_nodeSrc = new FileNode(this, "package " + file().getName() + "{}");
            }
            m_nodeSrc.parse();

            for (FileNode cmpFile : m_mapClzNodes.values()) {
                cmpFile.parse();
            }

            for (DirNode child : m_listPkgNodes) {
                child.parse();
            }
        }

        /**
         * Go through all the packages and types in this package and register their names.
         */
        public void registerNames() {
            // code was created by the parse phase if there was none
            assert sourceNode() != null;

            sourceNode().registerNames();

            for (FileNode clz : classNodes().values()) {
                clz.registerNames();
                registerName(clz.name(), clz);
            }

            for (DirNode pkg : packageNodes()) {
                pkg.registerNames();
                registerName(pkg.name(), pkg);
            }
        }

        /**
         * Register a node under a specified name.
         *
         * @param name  a name that must not conflict with any other child's name; if null, the
         *              request is ignored because it is assumed that an error has already been
         *              raised
         * @param node  the child node to register with the specified name
         */
        public void registerName(final String name, final Node node) {
            if (name != null) {
                if (children().containsKey(name)) {
                    log(Severity.ERROR, DUP_NAME, new Object[] {name, descriptiveName()}, null);
                } else {
                    children().put(name, node);
                }
            }
        }

        @Override
        public void linkParseTrees() {
            Node nodePkg = sourceNode();
            if (nodePkg == null) {
                log(Severity.ERROR, MISSING_PKG_NODE, new Object[]{descriptiveName()}, null);
            } else {
                TypeCompositionStatement typePkg = nodePkg.type();

                for (FileNode nodeClz : classNodes().values()) {
                    typePkg.addEnclosed(nodeClz.ast());
                }

                for (DirNode nodeNestedPkg : packageNodes()) {
                    // nest the package within this package
                    typePkg.addEnclosed(nodeNestedPkg.sourceNode().ast());

                    // recursively nest the classes and packages of the nested package within it
                    nodeNestedPkg.linkParseTrees();
                }
            }
        }

        @Override
        public Statement ast() {
            return sourceNode() == null ? null : sourceNode().ast();
        }

        @Override
        public TypeCompositionStatement type() {
            return sourceNode() == null ? null : sourceNode().type();
        }

        @Override
        public ErrorList errs() {
            if (sourceNode() != null) {
                return sourceNode().errs();
            }

            return null;
        }

        @Override
        public void logErrors(ErrorListener errs) {
            super.logErrors(errs);

            if (sourceNode() != null) {
                sourceNode().logErrors(errs);
            }

            for (FileNode clz : classNodes().values()) {
                clz.logErrors(errs);
            }

            for (DirNode pkg : packageNodes()) {
                pkg.logErrors(errs);
            }
        }

        /**
         * @return the module, package, or class source file, or null if none
         */
        @SuppressWarnings("unused")
        public File sourceFile() {
            return m_fileSrc;
        }

        /**
         * @return the corresponding node for the {@link #sourceFile()}
         */
        public FileNode sourceNode() {
            return m_nodeSrc;
        }

        /**
         * @return the list of child nodes that are packages
         */
        public List<DirNode> packageNodes() {
            return m_listPkgNodes;
        }

        /**
         * @return the map containing all class nodes by file
         */
        public ListMap<File, FileNode> classNodes() {
            return m_mapClzNodes;
        }

        /**
         * @return the map containing all children by name
         */
        public Map<String, Node> children() {
            return m_mapChildren;
        }

        @Override
        public String toString() {
            Node parent = parent();
            return parent == null
                    ? '/' + name() + '/'
                    : parent + name() + '/';
        }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The module.x or package.x file, or null.
         */
        private File m_fileSrc;

        /**
         * The node for the module, package, or class source.
         */
        private FileNode m_nodeSrc;

        /**
         * The classes nested directly in the module or package.
         */
        private final ListMap<File, FileNode> m_mapClzNodes = new ListMap<>();

        /**
         * The packages nested directly in the module or package.
         */
        private final List<DirNode> m_listPkgNodes = new ArrayList<>();

        /**
         * The child nodes (both packages and classes) nested directly in the module or package.
         */
        private final Map<String, Node> m_mapChildren = new HashMap<>();
    }

    /**
     * A FileNode represents an individual ".x" source file.
     */
    public class FileNode
            extends Node {
        /**
         * Construct a FileNode.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        FileNode(final DirNode parent, final File file) {
            super(parent, file);
        }

        /**
         * Construct a FileNode from the code that would have been in a file.
         *
         * @param code  the source code
         */
        public FileNode(final DirNode parent, final String code) {
            super(parent, null);
            m_text = code;
        }

        @Override
        public int depth() {
            int     cDepth     = super.depth();
            DirNode nodeParent = parent();
            if (nodeParent != null && nodeParent.parent() == null) {
                // this is a synthetic "root" parent
                --cDepth;
            }
            return cDepth;
        }

        @Override
        public String name() {
            TypeCompositionStatement stmtType = type();
            if (stmtType != null) {
                return stmtType.getName();
            }

            File file = file();
            if (file != null) {
                String sName = file().getName();
                if (sName.endsWith(".x")) {
                    sName = sName.substring(0, sName.length()-2);
                }
                return sName;
            }

            DirNode parent = parent();
            return parent == null
                    ? "<unknown>"
                    : parent.file().getParent();
        }

        @Override
        public String descriptiveName() {
            return m_stmtType == null
                    ? file().getAbsolutePath()
                    : type().getCategory().getId().TEXT + ' ' + name();
        }

        @Override
        public ResourceDir resourceDir() {
            if (m_resdir == null) {
                DirNode parent = parent();
                if (parent == null || parent.parent() == null && isPackageSource()) {
                    // this is the root node; use the root ResourceDir
                    m_resdir = ModuleInfo.this.getResourceDir();
                } else if (isPackageSource()) {
                    m_resdir = parent.parent().resourceDir();
                } else {
                    m_resdir = parent.resourceDir();
                }
            }
            return m_resdir;
        }

        /**
         * @return true iff this FileNode is the node that represents the module or package code for
         *         the DirNode that contains this node (or if this is a single file module)
         */
        public boolean isPackageSource() {
            DirNode parent = parent();
            return parent == null || this == parent.sourceNode();
        }

        /**
         * @return the source code text for this node, or an empty array if the source code is
         *         unavailable
         */
        public char[] content() {
            if (m_text != null) {
                return m_text.toCharArray();
            }

            try {
                return readFileChars(m_file);
            } catch (IOException e) {
                log(Severity.ERROR, READ_FAILURE, new Object[] {m_file}, null);
            }

            return new char[0];
        }

        /**
         * @return the source code for this node
         */
        public Source source() {
            if (m_source == null) {
                m_source = new Source(this);
            }

            return m_source;
        }

        /**
         * Determine the File referenced by the provided path string, relative to this node.
         *
         * @param path  a path string composed of any legal sequence of a "/" root path indicator,
         *               a "./" current directory indicator, "." directories, ".." parent
         *               directories, named directories, and a file name, following the rules
         *               defined in the Ecstasy BNF
         *
         * @return a File, a ResourceDir, or null if unresolvable
         */
        public Object resolveResource(final String path) {
            String resPath = path;
            ResourceDir dir;
            if (resPath.startsWith("/")) {
                resPath = resPath.substring(1);
                if (resPath.startsWith("/")) {
                    return null;
                }

                dir = ModuleInfo.this.getResourceDir();
            } else {
                dir = resourceDir();
            }

            boolean fRequireDir = resPath.endsWith("/");
            if (fRequireDir) {
                resPath = resPath.substring(0, resPath.length()-1);
                if (resPath.endsWith("/")) {
                    return null;
                }
            }

            if (dir == null || resPath.isEmpty()) {
                return dir;
            }

            String[] segments = parseDelimitedString(resPath, '/');
            for (int i = 0, last = segments.length - 1; i <= last; ++i) {
                String segment = segments[i];
                if (segment.isEmpty() || ".".equals(segment)) {
                    // nothing to do
                } else if ("..".equals(segment)) {
                    dir = dir.getParent();
                    if (dir == null) {
                        return null;
                    }
                } else {
                    Object resource = dir.getByName(segment);
                    if (resource instanceof ResourceDir subDir) {
                        dir = subDir;
                    } else {
                        return i == last && !fRequireDir ? resource : null;
                    }
                }
            }
            return dir;
        }

        @Override
        public void parse() {
            Source source = source();
            try {
                m_stmtAST = new Parser(source, this).parseSource();
            } catch (CompilerException e) {
                if (!hasSeriousErrors()) {
                    log(Severity.FATAL, Parser.FATAL_ERROR, null, source, source.getPosition(), source.getPosition());
                }
            }
        }

        @Override
        public void registerNames() {
            // this can only happen if the errors were ignored
            Statement stmt = ast();
            if (stmt != null) {
                if (stmt instanceof TypeCompositionStatement stmtType) {
                    m_stmtType = stmtType;
                } else {
                    List<Statement> list = ((StatementBlock) stmt).getStatements();
                    m_stmtType = (TypeCompositionStatement) list.getLast();
                }
            }
        }

        @Override
        public Statement ast() {
            return m_stmtAST;
        }

        @Override
        public TypeCompositionStatement type() {
            return m_stmtType;
        }

        @Override
        public String toString() {
            String text   = "";

            Node   parent = parent();
            if (parent != null) {
                text = parent.toString();
                if (text.endsWith("/")) {
                    text = text.substring(0, text.length()-1);
                }
            }

            return text + name() + ".x";
        }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The source code text for the file node.
         */
        private String                   m_text;

        /**
         * The Source object code for the file node; lazily created.
         */
        private Source                   m_source;

        /**
         * The AST for the source code, once it has been parsed.
         */
        private Statement                m_stmtAST;

        /**
         * The primary class (or other type) that the source file declares.
         */
        private TypeCompositionStatement m_stmtType;
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Check if the specified source or binary file contains a module and if so, return the module's
     * name.
     *
     * @param file  the file (source or binary) to examine
     *
     * @return the module's name if the file declares a module; null otherwise
     */
    public static String extractModuleName(final File file) {
        if (file.exists() && file.canRead()) {
            String name = file.getName();
            if (isExplicitSourceFile(name)) {
                try {
                    Source source = new Source(file);
                    Parser parser = new Parser(source, ErrorListener.BLACKHOLE);
                    return parser.parseModuleNameIgnoreEverythingElse();
                } catch (CompilerException | IOException ignore) {}
            } else if (isExplicitCompiledFile(name)) {
                try {
                    return new FileStructure(file).getModuleId().getName();
                } catch (IOException ignore) {}
            }
        }

        return null;
    }

    /**
     * Determine if the specified module name is an explicit Ecstasy source or compiled module file
     * name.
     *
     * @param sFile  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    public static boolean isExplicitEcstasyFile(final String sFile) {
        String sExt = getExtension(sFile);
        return "x".equalsIgnoreCase(sExt) || "xtc".equalsIgnoreCase(sExt);
    }

    /**
     * Determine if the specified module name is an explicit Ecstasy source file name.
     *
     * @param sFile  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    public static boolean isExplicitSourceFile(final String sFile) {
        String sExt = getExtension(sFile);
        return "x".equalsIgnoreCase(sExt);
    }

    /**
     * Obtain an array of files from the specified directory that are Ecstasy source files.
     *
     * @param dir  a directory that may contain Ecstasy source files
     *
     * @return a list of zero or more source files
     */
    public static List<File> sourceFiles(final File dir) {
        return listFiles(dir, "x");
    }

    /**
     * Determine if the specified module name is an explicit Ecstasy compiled module file name.
     *
     * @param sFile  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    public static boolean isExplicitCompiledFile(final String sFile) {
        String sExt = getExtension(sFile);
        return "xtc".equalsIgnoreCase(sExt);
    }

    /**
     * Obtain a list of files from the specified directory that are Ecstasy compiled module files.
     *
     * @param dir  a directory that may contain Ecstasy compiled module files
     *
     * @return a list of zero or more compiled module files
     */
    public static List<File> compiledFiles(final File dir) {
        return listFiles(dir, "xtc");
    }

    /**
     * @param dir  a directory
     *
     * @return true iff the directory appears to be a project directory
     */
    @SuppressWarnings("unused")
    public static boolean isProjectDir(final File dir) {
        return dir != null && dir.isDirectory() &&
            (new File(dir, "src"   ).exists() && !new File(dir, "src.x"   ).exists() ||
             new File(dir, "source").exists() && !new File(dir, "source.x").exists());
    }

    /**
     * Walk up the directory tree to find a project directory (or make the best guess).
     *
     * @param dir  the directory to start from
     *
     * @return the best-guess project directory
     */
    public static File projectDirFromSubDir(final File dir) {
        assert dir != null;

        String name   = dir.getName();
        File   curDir = dir;
        File   prjDir;
        if (   "build" .equalsIgnoreCase(name)
            || "target".equalsIgnoreCase(name)) {
            prjDir = curDir.getParentFile();
        } else {
            prjDir = curDir;

            if (   "x"      .equalsIgnoreCase(name)
                || "xtc"    .equalsIgnoreCase(name)
                || "ecstasy".equalsIgnoreCase(name)) {
                curDir = prjDir.getParentFile();
                if (curDir == null) {
                    return prjDir;
                }
                prjDir = curDir;
                name   = curDir.getName();
            }

            if (   "main".equalsIgnoreCase(name)
                || "test".equalsIgnoreCase(name)) {
                curDir = prjDir.getParentFile();
                if (curDir == null) {
                    return prjDir;
                }
                prjDir = curDir;
                name   = curDir.getName();
            }

            if (   "src"   .equalsIgnoreCase(name)
                || "source".equalsIgnoreCase(name)) {
                curDir = prjDir.getParentFile();
                if (curDir == null) {
                    return prjDir;
                }
                prjDir = curDir;
            }
        }

        if (prjDir == null) {
            prjDir = curDir;
        }

        return prjDir;
    }

    /**
     * Given a project directory, produce the best guess at the location of the source directory.
     *
     * @param prjDir  the project directory
     *
     * @return the best guess at the location of the source directory
     */
    public static File sourceDirFromPrjDir(final File prjDir) {
        assert prjDir != null;

        // Locate the source directory by drilling down through conventional project structures.
        // Tries paths like: prjDir/src/main/x/ or prjDir/source/main/ecstasy/ etc.
        // Each level is optional; we stop when source files are found or no more subdirs match.
        File curDir = prjDir;

        for (int level = 0; level <= 2; level++) {
            if (!sourceFiles(curDir).isEmpty()) {
                return curDir;
            }

            // Try to find a subdirectory at any level from 'level' onward
            File subDir = findSourceSubDir(curDir, level);
            if (subDir == null) {
                break;  // No matching subdirectory found at any remaining level
            }
            curDir = subDir;
        }

        // Check for source files in the final directory we descended to
        if (!sourceFiles(curDir).isEmpty()) {
            return curDir;
        }

        return prjDir;
    }

    /**
     * Find a conventional source subdirectory starting from the given level.
     * Levels represent the typical project structure:
     * <ul>
     *   <li>Level 0: "src" or "source"</li>
     *   <li>Level 1: "main" (note: explicitly NOT "test")</li>
     *   <li>Level 2: "x", "xtc", or "ecstasy"</li>
     * </ul>
     *
     * @param dir   the directory to search in
     * @param level the starting level (0-2)
     * @return the first matching subdirectory, or null if none found
     */
    private static File findSourceSubDir(final File dir, final int level) {
        for (int i = level; i <= 2; i++) {
            File subDir = switch (i) {
                case 0 -> firstDirectory(dir, "src", "source");
                case 1 -> firstDirectory(dir, "main");
                case 2 -> firstDirectory(dir, "x", "xtc", "ecstasy");
                default -> null;
            };
            if (subDir != null) {
                return subDir;
            }
        }
        return null;
    }

    /**
     * Return the first subdirectory that exists from the given list of names.
     *
     * @param parent the parent directory
     * @param names  the subdirectory names to try
     * @return the first existing subdirectory, or null if none exist
     */
    private static File firstDirectory(final File parent, final String... names) {
        return Stream.of(names)
                .map(name -> new File(parent, name))
                .filter(File::isDirectory)
                .findFirst()
                .orElse(null);
    }

    /**
     * Given a project directory, produce the best guess at the location of the binary (compiled
     * module) directory.
     *
     * @param prjDir  the project directory
     *
     * @return the best guess at the location of the binary directory
     */
    public static File binaryDirFromPrjDir(final File prjDir) {
        assert prjDir != null;

        File subDir;
        if ((subDir = new File(prjDir, "build")).isDirectory() ||
            (subDir = new File(prjDir, "target")).isDirectory()) {
            return subDir;
        }

        if (!sourceFiles(prjDir).isEmpty() || !compiledFiles(prjDir).isEmpty()) {
            return prjDir;
        }

        // we assume that the absence of either source or compiled files in the project directory
        // implies that the build (or target) directory has not been created yet, which is a common
        // condition after a "clean" build command
        return new File(prjDir, "build");
    }


    // ----- fields --------------------------------------------------------------------------------

    private enum Status  {Unknown, NotExists, Exists}
    private enum Content {Unknown, Invalid, Module}

    private final File    fileSpec;       // original file spec that was used to create this info
    private final boolean deduce;         // use knowledge of common file layouts to find locations
    private String        fileName;       // selected file name, without the file extension
    private String        moduleName;     // the module name (potentially qualified)
    private File          projectDir;     // the directory containing the "project"

    private final Status  sourceStatus;
    private File          sourceDir;      // directory containing source code
    private File          sourceFile;     // the "module root" source code file
    private boolean       sourceIsTree;   // true iff the module sources include subdirectories
    private Content       sourceContent = Content.Unknown;  // what is known about the module source file content
    private long          sourceTimestamp;// last known change to a source file
    private Node          sourceNode;     // root node of the source tree

    private ResourceDir   resourceDir;
    private long          resourceTimestamp;

    private Status        binaryStatus = Status.Unknown;
    private File          binaryDir;
    private File          binaryFile;
    private Version       binaryVersion;
    private Content       binaryContent = Content.Unknown;  // what is known about the compiled module file content
    private long          binaryTimestamp;
}
