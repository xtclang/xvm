package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import java.util.function.Consumer;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Constants;
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
public class ModuleInfo
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the module information from the specified file.
     *
     * @param fileSpec  the file to analyze, which may or may not exist
     */
    public ModuleInfo(File fileSpec)
        {
        this(fileSpec, null, null);
        }

    /**
     * Construct the module information from the specified file.
     *
     * @param fileSpec       the file or directory to analyze, which may or may not exist
     * @param resourceSpecs  an array of files and/or directories which represent (in aggregate)
     *                       the resource path; null indicates that the default resources
     *                       location should be used, while an empty array explicitly indicates
     *                       that there is no resource path; as provided to the compiler using
     *                       the "-rp" command line switch
     * @param binarySpec     the file or directory which represents the target of the binary; as
     *                       provided to the compiler using the "-o" command line switch
     */
    public ModuleInfo(File fileSpec, File[] resourceSpecs, File binarySpec)
        {
        if (fileSpec == null)
            {
            throw new IllegalArgumentException("A file specification is required for the module");
            }

        this.fileSpec = fileSpec;

        // start by figuring out the directory to work from and the file name to look for (which may
        // not be specified!), based on some combination of the current working directory and the
        // fileSpec; if the fileSpec refers to something that exists, it's either a directory or a
        // file, and the file may be a source or a compiled binary file; if the fileSpec refers to
        // a non-existent location, it might indicate either a directory or a file
        File resolved = resolveFile(fileSpec);
        File dirSpec  = resolved.getParentFile();
        fileName = resolved.getName();
        if (fileName.isEmpty())
            {
            fileName = null;
            }
        if (resolved.isDirectory())
            {
            // it's possible that the module name was specified without the ".x" extension, which
            // would match the name of the sub-directory containing the package and class files
            // within the module; this should be obvious because there will also be a source file
            if (dirSpec == null || fileName != null && !new File(dirSpec, fileName + ".x").exists())
                {
                // we don't know the file name specified, because the name was of a directory
                dirSpec  = resolved;
                fileName = null;
                }
            }
        if (dirSpec == null || !dirSpec.isDirectory())
            {
            throw new IllegalArgumentException("Unable to identify a module directory for " + fileSpec);
            }

        // the specified name may be a source file name, binary file name, qualified module name,
        // or simple module name
        String shorterName = null;
        if (fileName != null)
            {
            if (isExplicitEcstasyFile(fileName))
                {
                fileName = removeExtension(fileName);
                }

            int dot = fileName.indexOf('.');
            if (dot > 0)
                {
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
        do
            {
            File[] srcFiles = sourceFiles(dirSpec);
            File[] binFiles = compiledFiles(dirSpec);
            int    srcCount = srcFiles.length;
            int    binCount = binFiles.length;
            if (srcCount > 0 && binCount > 0)
                {
                // we're in a directory with both source and compiled files; assume that this is
                // where we'll find everything that we need
                projectDir = binaryDir = sourceDir = dirSpec;
                break;
                }

            if (srcCount == 0 && binCount > 0)
                {
                // we're in a directory with compiled files; it's probably a "build" directory under
                // the project directory
                binaryDir = dirSpec;
                break;
                }

            assert binCount == 0;
            if (srcCount == 0)
                {
                // no source files, no binary files, so start looking for the project directory from
                // this point
                curDir = dirSpec;
                }
            else
                {
                File   searchDir = dirSpec;
                String srcName   = fileName    == null ? null : fileName    + ".x";
                String srcName2  = shorterName == null ? null : shorterName + ".x";
                do
                    {
                    sourceDir = searchDir;
                    if (srcName == null)
                        {
                        if (srcCount == 1)
                            {
                            File file = srcFiles[0];
                            moduleName = extractModuleName(file);
                            if (moduleName != null)
                                {
                                // we found "the" module
                                fileName      = removeExtension(file.getName());
                                sourceFile    = file;
                                sourceContent = Content.Module;
                                break;
                                }
                            }
                        }
                    else
                        {
                        if (Arrays.stream(srcFiles).anyMatch(f -> !f.isDirectory()
                                && (f.getName().equals(srcName) || f.getName().equals(srcName2))))
                            {
                            break;
                            }
                        }

                    searchDir = searchDir.getParentFile();
                    if (searchDir == null)
                        {
                        break;
                        }

                    srcFiles = sourceFiles(searchDir);
                    srcCount = srcFiles.length;
                    }
                while (srcCount > 0);
                }
            }
        while (false);

        if (projectDir == null)
            {
            if (sourceDir != null)
                {
                projectDir = projectDirFromSubDir(sourceDir);
                }
            else if (binaryDir != null)
                {
                projectDir = projectDirFromSubDir(binaryDir);
                }
            else if (curDir != null)
                {
                projectDir = projectDirFromSubDir(curDir);
                }
            else
                {
                throw new IllegalArgumentException(
                        "Unable to identify a module project directory for " + fileSpec);
                }
            }

        // at this point, there's a project directory; if we have not already done so, locate the
        // source file for the module from the project directory
        if (sourceFile == null)
            {
            if (sourceDir == null)
                {
                sourceDir = sourceDirFromPrjDir(projectDir);
                }

            if (sourceDir != null)
                {
                if (fileName == null)
                    {
                    File[] files = sourceFiles(sourceDir);
                    if (files.length == 1)
                        {
                        fileName = removeExtension(files[0].getName());
                        }
                    }

                if (fileName != null)
                    {
                    sourceFile = new File(sourceDir, fileName + ".x");
                    }
                }
            }

        if (sourceFile == null || !sourceFile.exists())
            {
            sourceStatus = Status.NotExists;
            }
        else
            {
            sourceStatus = Status.Exists;

            if (sourceDir == null)
                {
                sourceDir = sourceFile.getParentFile();
                }

            sourceIsTree = new File(sourceDir, fileName).exists();
            }

        if (binarySpec != null)
            {
            // if it exists, it must either be an .xtc file or a directory
            binarySpec = resolveFile(binarySpec);
            if (binarySpec.exists())
                {
                if (binarySpec.isDirectory())
                    {
                    binaryDir = binarySpec;
                    }
                else
                    {
                    String sExt = getExtension(binarySpec.getName());
                    if ("xtc".equals(sExt))
                        {
                        binaryFile   = binarySpec;
                        binaryDir    = binarySpec.getParentFile();
                        binaryStatus = Status.Exists;
                        }
                    else
                        {
                        throw new IllegalArgumentException("Target destination " + binarySpec
                                + " must use an .xtc extension");
                        }
                    }
                }
            else
                {
                // if it doesn't exist, it needs to be located somewhere under some directory
                // that does exist
                File fileParent = binarySpec.getParentFile();
                while (true)
                    {
                    if (fileParent == null)
                        {
                        throw new IllegalArgumentException("Target destination " + binarySpec
                            + " is illegal because it does not exist and cannot be created");
                        }

                    if (fileParent.exists())
                        {
                        if (!fileParent.isDirectory())
                            {
                            throw new IllegalArgumentException("Target destination "
                                    + binarySpec + " is illegal because parent file "
                                    + fileParent + " is not a directory");
                            }

                        break;
                        }
                    }

                String sExt = getExtension(binarySpec.getName());
                if ("xtc".equals(sExt))
                    {
                    binaryFile   = binarySpec;
                    binaryDir    = binarySpec.getParentFile();
                    binaryStatus = Status.NotExists;
                    }
                else
                    {
                    binaryDir = binarySpec;
                    }
                }
            }

        if (resourceSpecs != null && resourceSpecs.length > 0)
            {
            for (File file : resourceSpecs)
                {
                if (file == null)
                    {
                    throw new IllegalArgumentException("A resource location is specified as null");
                    }

                if (!file.exists())
                    {
                    throw new IllegalArgumentException("The resource location " + file
                            + " does not exist");
                    }
                }

            // merge the resource directory lists, with the specified ones having priority
            File[] dftResDirs = getResourceDir().getLocations();
            int    cDfts      = dftResDirs.length;
            if (cDfts > 0)
                {
                int    cSpecs = resourceSpecs.length;
                File[] allResDirs = new File[cSpecs + cDfts];
                System.arraycopy(resourceSpecs, 0, allResDirs, 0, cSpecs);
                System.arraycopy(dftResDirs, 0, allResDirs, cSpecs, cDfts);
                resourceSpecs = allResDirs;
                }

            resourceDir = new ResourceDir(resourceSpecs);
            }
        }


    // ----- general -------------------------------------------------------------------------------

    /**
     * @return the "file spec" that the ModuleInfo started from
     */
    public File getFileSpec()
        {
        return fileSpec;
        }

    /**
     * @return the file name (the short name without an extension) that the ModuleInfo is using
     */
    public String getFileName()
        {
        return fileName;
        }

    /**
     * @return the inferred "project directory", which may be one or more steps above the
     *         directory containing the module source code
     */
    public File getProjectDir()
        {
        return projectDir;
        }

    /**
     * @return True if the module binary exists and is at least as up-to-date as all of the
     *         existent source and resource files and directories
     */
    public boolean isUpToDate()
        {
        long binTimestamp = getBinaryTimestamp();
        return binTimestamp >  0L
            && binTimestamp >= getSourceTimestamp()
            && binTimestamp >= getResourceTimestamp();
        }

    /**
     * @return True if the module name uses a qualified format
     */
    public boolean isModuleNameQualified()
        {
        return getQualifiedModuleName().indexOf('.') >= 0;
        }

    /**
     * @return the full module name, which may or may not be qualified
     */
    public String getQualifiedModuleName()
        {
        UnknownModule: if (moduleName == null)
            {
            if (sourceStatus == Status.Exists && sourceContent != Content.Invalid)
                {
                moduleName = extractModuleName(sourceFile);
                if (moduleName == null)
                    {
                    sourceContent = Content.Invalid;
                    }
                else
                    {
                    sourceContent = Content.Module;
                    break UnknownModule;
                    }
                }

            // try to get the module name from the compiled module file
            if (getBinaryFile() != null && binaryContent != Content.Invalid)
                {
                if (loadBinaryFile())
                    {
                    break UnknownModule;
                    }
                }

            // guess at the module name based on the source file name, binary file name, or
            // project dir name
            if (sourceFile != null)
                {
                moduleName = removeExtension(sourceFile.getName());
                }
            else if (binaryFile != null)
                {
                moduleName = removeExtension(binaryFile.getName());
                }
            else if (fileName != null)
                {
                moduleName = fileName;
                }
            else
                {
                moduleName = projectDir.getName();
                }
            }

        return moduleName;
        }

    /**
     * Determine if the specified module node represents a system module.
     *
     * @return true iff this module is for the Ecstasy or native prototype module
     */
    public boolean isSystemModule()
        {
        String sModule = getQualifiedModuleName();
        return sModule.equals(ECSTASY_MODULE)
            || sModule.equals(TURTLE_MODULE);
        }

    /**
     * @return the unqualified module name
     */
    public String getSimpleModuleName()
        {
        String moduleName = getQualifiedModuleName();
        int firstDot = moduleName.indexOf('.');
        return firstDot >= 0 ? moduleName.substring(0, firstDot) : moduleName;
        }


    // ----- source --------------------------------------------------------------------------------

    /**
     * @return the module source file, or null if none can be resolved
     */
    public File getSourceFile()
        {
        return sourceFile;
        }

    /**
     * @return the module source file, or null if none can be resolved
     */
    public File getSourceDir()
        {
        return sourceDir;
        }

    /**
     * @return true iff the source code appears to be organized using the multiple-file,
     *         hierarchical, name-spaced tree
     */
    public boolean isSourceTree()
        {
        return sourceIsTree;
        }

    /**
     * @return lazily computed and cached latest-timestamp of the module source file or source
     *         directory contents, or 0 if none
     */
    public long getSourceTimestamp()
        {
        File fileSrc = getSourceFile();
        if (fileSrc != null && fileSrc.exists() && sourceTimestamp == 0L)
            {
            sourceTimestamp = sourceFile.lastModified();
            if (sourceIsTree)
                {
                synchronized (this)
                    {
                    File subdir = new File(sourceFile.getParentFile(), removeExtension(sourceFile.getName()));
                    if (subdir.isDirectory())
                        {
                        accumulator = sourceTimestamp;
                        visitTree(subdir, "x", f -> accumulator = Math.max(accumulator, f.lastModified()));
                        sourceTimestamp = accumulator;
                        accumulator     = 0L;
                        }
                    }
                }
            }

        return sourceTimestamp;
        }


    // ----- resources -----------------------------------------------------------------------------

    /**
     * @return the ResourceDir object representing the root resource directory
     */
    public ResourceDir getResourceDir()
        {
        if (resourceDir == null)
            {
            resourceDir = ResourceDir.forSource(getSourceFile());
            }

        return resourceDir;
        }

    /**
     * @return lazily computed and cached latest-timestamp of the resource directory contents,
     *         or 0 if none
     */
    public long getResourceTimestamp()
        {
        if (resourceTimestamp == 0L)
            {
            resourceTimestamp = getResourceDir().getTimestamp();
            }

        return resourceTimestamp;
        }


    // ----- binary --------------------------------------------------------------------------------

    /**
     * @return the file indicating the compiled form of the module, or null if the file cannot be
     *         determined, such as when the project "./build/" or "./target/" directory is missing
     */
    public File getBinaryFile()
        {
        if (binaryFile == null)
            {
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
    public File getBinaryDir()
        {
        if (binaryDir == null)
            {
            binaryDir = binaryFile == null
                    ? binaryDirFromPrjDir(projectDir)
                    : binaryFile.getParentFile();
            }

        return binaryDir;
        }

    /**
     * @return return the version of the compiled module, or null if either the compiled module
     *         does not exist or if it has no version
     */
    public Version getModuleVersion()
        {
        if (binaryVersion == null)
            {
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
    private boolean loadBinaryFile()
        {
        if (binaryStatus != Status.NotExists && binaryContent == Content.Unknown)
            {
            File file = getBinaryFile();
            if (file != null && file.exists())
                {
                binaryStatus  = Status.Exists;
                binaryContent = Content.Invalid;
                try
                    {
                    FileStructure struct = new FileStructure(file);
                    moduleName = struct.getModuleName();
                    if (moduleName != null)
                        {
                        binaryVersion = struct.getModule().getVersion().getVersion();
                        binaryContent = Content.Module;
                        return true;
                        }
                    }
                catch (Exception ignore) {}
                }
            else
                {
                binaryStatus = Status.NotExists;
                }
            }

        return false;
        }

    /**
     * @return lazily computed and cached timestamp of the compiled module file, or 0 if none
     */
    public long getBinaryTimestamp()
        {
        if (binaryTimestamp == 0L)
            {
            File file = getBinaryFile();
            if (file != null && file.exists())
                {
                binaryTimestamp = file.lastModified();
                }
            }

        return binaryTimestamp;
        }

    /**
     * @param dir  a file, directory, or null
     *
     * @return an array of 0 or more compiled module files; never null
     */
    private File[] getBinaryFiles(File dir)
        {
        return dir == null || !dir.isDirectory()
                ? NO_FILES
                : dir.listFiles(f -> !f.isDirectory() && "xtc".equalsIgnoreCase(getExtension(f.getName())));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return new StringBuilder()
            .append("Module(name=")        .append(moduleName == null ? "<unknown>" : moduleName)
            .append(", fileSpec=")         .append(fileSpec)
            .append(", fileName=")         .append(fileName)
            .append(", moduleName=")       .append(moduleName == null ? "<unknown>" : moduleName)
            .append(", projectDir=")       .append(projectDir)
            .append(", sourceStatus=")     .append(sourceStatus)
            .append(", sourceDir=")        .append(sourceDir)
            .append(", sourceIsTree=")     .append(sourceIsTree)
            .append(", sourceFile=")       .append(sourceFile)
            .append(", sourceContent=")    .append(sourceContent)
            .append(", sourceTimestamp=")  .append(sourceTimestamp == 0 ? "<unknown>" : dateString(sourceTimestamp))
            .append(", resourceDir=")      .append(resourceDir == null ? "<unknown>" : resourceDir)
            .append(", resourceTimestamp=").append(resourceTimestamp == 0 ? "<unknown>" : dateString(resourceTimestamp))
            .append(", binaryStatus=")     .append(binaryStatus)
            .append(", binaryDir=")        .append(binaryDir == null ? "<unknown>" : binaryDir)
            .append(", binaryFile=")       .append(binaryFile == null ? "<unknown>" : binaryFile)
            .append(", binaryVersion=")    .append(binaryVersion == null ? "<unknown>" : binaryVersion)
            .append(", binaryContent=")    .append(binaryContent)
            .append(", binaryTimestamp=")  .append(binaryTimestamp == 0 ? "<unknown>" : dateString(binaryTimestamp))
            .toString();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Visit all of the subdirectories (recursively) and files matching the optional extension
     * within the specified directory, and do so in a stable, repeatable order.
     *
     * @param dir      a directory
     * @param ext      an optional extension to match; otherwise null
     * @param visitor  the visitor to invoke with each matching non-directory file
     */
    private void visitTree(File dir, String ext, Consumer<File> visitor)
        {
        TreeMap<String, File> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (File child : dir.listFiles())
            {
            String name = child.getName();
            if (ext == null || ext.equalsIgnoreCase(getExtension(name)))
                {
                assert !children.containsKey(name);
                children.put(name, child);
                }
            }

        for (File child : children.values())
            {
            if (child.isDirectory())
                {
                visitTree(child, ext, visitor);
                }
            else
                {
                visitor.accept(child);
                }
            }
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
    public Node getSourceTree(ErrorListener errs)
        {
        if (sourceNode != null)
            {
            return sourceNode;
            }

        File srcDir  = getSourceDir();
        File srcFile = getSourceFile();
        if (srcDir == null || srcFile == null)
            {
            return null;
            }

        if (errs == null)
            {
            errs = new ErrorList(100);
            }

        if (sourceIsTree)
            {
            assert fileName != null;
            File subDir = new File(srcDir, fileName);
            assert subDir.exists();
            DirNode dirNode = new DirNode(/*parent=*/null, subDir, srcFile);
            dirNode.buildSourceTree();
            sourceNode = dirNode;
            }
        else
            {
            sourceNode = new FileNode(null, srcFile);
            }
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired())
            {
            return null;
            }

        sourceNode.parse();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired())
            {
            return null;
            }

        sourceNode.registerNames();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired())
            {
            return null;
            }

        sourceNode.linkParseTrees();
        sourceNode.logErrors(errs);
        if (errs.hasSeriousErrors() || errs.isAbortDesired())
            {
            return null;
            }

        return sourceNode;
        }

    /**
     * Represents either a module/package or a class source node.
     */
    public abstract class Node
            implements ErrorListener
        {
        /**
         * Construct a Node.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        public Node(DirNode parent, File file)
            {
            // at least one of the parameters is required
            assert parent != null || file != null;

            m_parent       = parent;
            m_file         = file;
            }

        /**
         * @return the parent of this node
         */
        public DirNode parent()
            {
            return m_parent;
            }

        /**
         * @return the root node
         */
        public Node root()
            {
            return m_parent == null ? this : m_parent.root();
            }

        /**
         * @return the node represent the module
         */
        public FileNode module()
            {
            Node rootNode = root();
            if (rootNode instanceof DirNode rootDir)
                {
                return rootDir.sourceNode();
                }
            else
                {
                return (FileNode) rootNode;
                }
            }

        /**
         * @return the ModuleInfo for the module
         */
        public ModuleInfo moduleInfo()
            {
            return ModuleInfo.this;
            }

        /**
         * @return the depth of this node
         */
        public int depth()
            {
            return parent() == null ? 0 : 1 + parent().depth();
            }

        /**
         * @return the file that this node represents
         */
        public File file()
            {
            return m_file;
            }

        /**
         * @return the ResourceDir for this node; never null
         */
        public abstract ResourceDir resourceDir();

        /**
         * @return the child directory name for this node's ResourceDir compared to its parent's,
         *         or null to indicate that the parent's ResourceDir should be used
         */
        protected String resourcePathPart()
            {
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
        public void linkParseTrees()
            {
            }

        /**
         * @return the name of this node
         */
        public abstract String name();

        /**
         * @return a descriptive name for this node
         */
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
        public boolean log(ErrorInfo err)
            {
            return errs().log(err);
            }

        @Override
        public boolean isAbortDesired()
            {
            return m_errs != null && m_errs.isAbortDesired();
            }

        @Override
        public boolean hasSeriousErrors()
            {
            return m_errs != null && m_errs.hasSeriousErrors();
            }

        @Override
        public boolean hasError(String sCode)
            {
            return m_errs != null && m_errs.hasError(sCode);
            }

        /**
         * @return the list containing any errors accumulated on (or under) this node
         */
        public ErrorList errs()
            {
            ErrorList errs = m_errs;
            if (errs == null)
                {
                m_errs = errs = new ErrorList(341);
                }
            return errs;
            }

        /**
         * @return log any errors accumulated on (or under) this node
         */
        public void logErrors(ErrorListener errs)
            {
            ErrorList deferred = m_errs;
            if (deferred != null)
                {
                for (ErrorInfo err : deferred.getErrors())
                    {
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
        protected ErrorList m_errs;
        }

    /**
     * A DirNode represents a source directory, which corresponds to a module or package.
     */
    public class DirNode
            extends Node
        {
        /**
         * Construct the root DirNode.
         *
         * @param parent   the parent node, which is null for the root node
         * @param dir      the directory that this node will represent
         * @param fileSrc  the file for the package.x file (or null if it does not exist)
         */
        protected DirNode(DirNode parent, File dir, File fileSrc)
            {
            super(parent, dir);
            assert dir.isDirectory();

            if (fileSrc != null)
                {
                m_fileSrc = fileSrc;
                m_nodeSrc = new FileNode(this, fileSrc);
                }
            }

        /**
         * Build a sub-tree of nodes that are contained within this node.
         */
        void buildSourceTree()
            {
            File thisDir = file();
            for (File file : listFiles(thisDir))
                {
                String name = file.getName();
                if (file.isDirectory())
                    {
                    // if the directory has no corresponding ".x" file, then it is an implied package
                    if (!(new File(thisDir, name + ".x")).exists() && name.indexOf('.') < 0)
                        {
                        DirNode child = new DirNode(this, file, null);
                        packageNodes().add(child);
                        child.buildSourceTree();
                        }
                    }
                else if (name.endsWith(".x"))
                    {
                    // if there is a directory by the same name (minus the ".x"), then recurse to create
                    // a subtree
                    File subDir = new File(thisDir, removeExtension(name));
                    if (subDir.exists() && subDir.isDirectory())
                        {
                        // create a sub-tree
                        DirNode child = new DirNode(this, subDir, file);
                        packageNodes().add(child);
                        child.buildSourceTree();
                        }
                    else
                        {
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
        public String name()
            {
            return file().getName();
            }

        @Override
        public String descriptiveName()
            {
            if (parent() == null)
                {
                return "module " + name();
                }

            StringBuilder sb = new StringBuilder();
            sb.append("package ")
              .append(name());

            DirNode node = parent();
            while (node.parent() != null)
                {
                sb.insert(8, node.name() + '.');
                node = node.parent();
                }

            return sb.toString();
            }

        @Override
        public ResourceDir resourceDir()
            {
            if (m_resdir == null)
                {
                DirNode     parent    = parent();
                ResourceDir parentDir = parent == null
                        ? ModuleInfo.this.getResourceDir()
                        : parent.resourceDir();
                m_resdir = parentDir.getDirectory(resourcePathPart());

                // if no resources could be found, use an empty ResourceDir; this is helpful because
                // we can't "cache" the null result, and the caller doesn't have to check for null,
                // either
                if (m_resdir == null)
                    {
                    m_resdir = NoResources;
                    }
                }
            return m_resdir;
            }

        @Override
        protected String resourcePathPart()
            {
            String name = file().getName();
            int    dot  = name.indexOf('.');
            return dot >= 0 ? name.substring(0, dot) : name;
            }

        /**
         * Parse this node and all nodes it contains.
         */
        @Override
        public void parse()
            {
            if (m_nodeSrc == null)
                {
                // provide a default implementation
                assert m_parent != null;
                m_nodeSrc = new FileNode(this, "package " + file().getName() + "{}");
                }
            m_nodeSrc.parse();

            for (FileNode cmpFile : m_mapClzNodes.values())
                {
                cmpFile.parse();
                }

            for (DirNode child : m_listPkgNodes)
                {
                child.parse();
                }
            }

        /**
         * Go through all the packages and types in this package and register their names.
         */
        public void registerNames()
            {
            // code was created by the parse phase if there was none
            assert sourceNode() != null;

            sourceNode().registerNames();

            for (FileNode clz : classNodes().values())
                {
                clz.registerNames();
                registerName(clz.name(), clz);
                }

            for (DirNode pkg : packageNodes())
                {
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
        public void registerName(String name, Node node)
            {
            if (name != null)
                {
                if (children().containsKey(name))
                    {
                    log(Severity.ERROR, DUP_NAME, new Object[] {name, descriptiveName()}, null);
                    }
                else
                    {
                    children().put(name, node);
                    }
                }
            }

        @Override
        public void linkParseTrees()
            {
            Node nodePkg = sourceNode();
            if (nodePkg == null)
                {
                log(Severity.ERROR, MISSING_PKG_NODE, new Object[]{descriptiveName()}, null);
                }
            else
                {
                TypeCompositionStatement typePkg = nodePkg.type();

                for (FileNode nodeClz : classNodes().values())
                    {
                    typePkg.addEnclosed(nodeClz.ast());
                    }

                for (DirNode nodeNestedPkg : packageNodes())
                    {
                    // nest the package within this package
                    typePkg.addEnclosed(nodeNestedPkg.sourceNode().ast());

                    // recursively nest the classes and packages of the nested package within it
                    nodeNestedPkg.linkParseTrees();
                    }
                }
            }

        @Override
        public Statement ast()
            {
            return sourceNode() == null ? null : sourceNode().ast();
            }

        @Override
        public TypeCompositionStatement type()
            {
            return sourceNode() == null ? null : sourceNode().type();
            }

        @Override
        public ErrorList errs()
            {
            if (sourceNode() != null)
                {
                return sourceNode().errs();
                }

            return null;
            }

        @Override
        public void logErrors(ErrorListener errs)
            {
            super.logErrors(errs);

            if (sourceNode() != null)
                {
                sourceNode().logErrors(errs);
                }

            for (FileNode clz : classNodes().values())
                {
                clz.logErrors(errs);
                }

            for (DirNode pkg : packageNodes())
                {
                pkg.logErrors(errs);
                }
            }

        /**
         * @return the module, package, or class source file, or null if none
         */
        public File sourceFile()
            {
            return m_fileSrc;
            }

        /**
         * @return the corresponding node for the {@link #sourceFile()}
         */
        public FileNode sourceNode()
            {
            return m_nodeSrc;
            }

        /**
         * @return the list of child nodes that are packages
         */
        public List<DirNode> packageNodes()
            {
            return m_listPkgNodes;
            }

        /**
         * @return the map containing all class nodes by file
         */
        public ListMap<File, FileNode> classNodes()
            {
            return m_mapClzNodes;
            }

        /**
         * @return the map containing all children by name
         */
        public Map<String, Node> children()
            {
            return m_mapChildren;
            }

        @Override
        public String toString()
            {
            Node parent = parent();
            return parent == null
                    ? '/' + name() + '/'
                    : parent + name() + '/';
            }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The module.x or package.x file, or null.
         */
        protected File                    m_fileSrc;

        /**
         * The node for the module, package, or class source.
         */
        protected FileNode                m_nodeSrc;

        /**
         * The classes nested directly in the module or package.
         */
        protected ListMap<File, FileNode> m_mapClzNodes  = new ListMap<>();

        /**
         * The packages nested directly in the module or package.
         */
        protected List<DirNode>           m_listPkgNodes = new ArrayList<>();

        /**
         * The child nodes (both packages and classes) nested directly in the module or package.
         */
        protected Map<String, Node>       m_mapChildren  = new HashMap<>();
        }

    /**
     * A FileNode represents an individual ".x" source file.
     */
    public class FileNode
            extends Node
        {
        /**
         * Construct a FileNode.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        public FileNode(DirNode parent, File file)
            {
            super(parent, file);
            }

        /**
         * Construct a FileNode from the code that would have been in a file.
         *
         * @param code  the source code
         */
        public FileNode(DirNode parent, String code)
            {
            super(parent, null);
            m_text = code;
            }

        @Override
        public int depth()
            {
            int     cDepth     = super.depth();
            DirNode nodeParent = parent();
            if (nodeParent != null && nodeParent.parent() == null)
                {
                // this is a synthetic "root" parent
                --cDepth;
                }
            return cDepth;
            }

        @Override
        public String name()
            {
            TypeCompositionStatement stmtType = type();
            if (stmtType != null)
                {
                return stmtType.getName();
                }

            File file = file();
            if (file != null)
                {
                String sName = file().getName();
                if (sName.endsWith(".x"))
                    {
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
        public String descriptiveName()
            {
            return m_stmtType == null
                    ? file().getAbsolutePath()
                    : type().getCategory().getId().TEXT + ' ' + name();
            }

        @Override
        public ResourceDir resourceDir()
            {
            if (m_resdir == null)
                {
                DirNode parent = parent();
                if (parent == null || parent.parent() == null && isPackageSource())
                    {
                    // this is the root node; use the root ResourceDir
                    m_resdir = ModuleInfo.this.getResourceDir();
                    }
                else if (isPackageSource()) {
                    m_resdir = parent.parent().resourceDir();
                    }
                else
                    {
                    m_resdir = parent.resourceDir();
                    }
                }
            return m_resdir;
            }

        /**
         * @return true iff this FileNode is the node that represents the module or package code for
         *         the DirNode that contains this node (or if this is a single file module)
         */
        public boolean isPackageSource()
            {
            DirNode parent = parent();
            return parent == null || this == parent.sourceNode();
            }

        /**
         * @return the source code text for this node, or an empty array if the source code is
         *         unavailable
         */
        public char[] content()
            {
            if (m_text != null)
                {
                return m_text.toCharArray();
                }

            try
                {
                return readFileChars(m_file);
                }
            catch (IOException e)
                {
                log(Severity.ERROR, READ_FAILURE, new Object[] {m_file}, null);
                }

            return new char[0];
            }

        /**
         * @return the source code for this node
         */
        public Source source()
            {
            if (m_source == null)
                {
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
        public Object resolveResource(String path)
            {
            ResourceDir dir;
            if (path.startsWith("/"))
                {
                path = path.substring(1);
                if (path.startsWith("/"))
                    {
                    return null;
                    }

                dir = ModuleInfo.this.getResourceDir();
                }
            else
                {
                dir = resourceDir();
                }

            boolean fRequireDir = path.endsWith("/");
            if (fRequireDir)
                {
                path = path.substring(0, path.length()-1);
                if (path.endsWith("/"))
                    {
                    return null;
                    }
                }

            if (dir == null || path.isEmpty())
                {
                return dir;
                }

            String[] segments = parseDelimitedString(path, '/');
            for (int i = 0, last = segments.length - 1; i <= last; ++i)
                {
                String segment = segments[i];
                if (segment.isEmpty())
                    {
                    return null;
                    }

                if (".".equals(segment))
                    {
                    // nothing to do
                    }
                else if ("..".equals(segment))
                    {
                    dir = dir.getParent();
                    if (dir == null)
                        {
                        return null;
                        }
                    }
                else
                    {
                    Object resource = dir.getByName(segment);
                    if (resource instanceof ResourceDir subdir)
                        {
                        dir = subdir;
                        }
                    else
                        {
                        return i == last && !fRequireDir ? resource : null;
                        }
                    }
                }

            return dir;
            }

        @Override
        public void parse()
            {
            Source source = source();
            try
                {
                m_stmtAST = new Parser(source, this).parseSource();
                }
            catch (CompilerException e)
                {
                if (!hasSeriousErrors())
                    {
                    log(Severity.FATAL, Parser.FATAL_ERROR, null,
                        source, source.getPosition(), source.getPosition());
                    }
                }
            }

        @Override
        public void registerNames()
            {
            // this can only happen if the errors were ignored
            Statement stmt = ast();
            if (stmt != null)
                {
                if (stmt instanceof TypeCompositionStatement stmtType)
                    {
                    m_stmtType = stmtType;
                    }
                else
                    {
                    List<Statement> list = ((StatementBlock) stmt).getStatements();
                    m_stmtType = (TypeCompositionStatement) list.get(list.size() - 1);
                    }
                }
            }

        @Override
        public Statement ast()
            {
            return m_stmtAST;
            }

        @Override
        public TypeCompositionStatement type()
            {
            return m_stmtType;
            }

        @Override
        public String toString()
            {
            String text   = "";

            Node   parent = parent();
            if (parent != null)
                {
                text = parent.toString();
                if (text.endsWith("/"))
                    {
                    text = text.substring(0, text.length()-1);
                    }
                }

            return text + name() + ".x";
            }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The source code text for the file node.
         */
        protected String                   m_text;

        /**
         * The Source object code for the file node; lazily created.
         */
        protected Source                   m_source;

        /**
         * The AST for the source code, once it has been parsed.
         */
        protected Statement                m_stmtAST;

        /**
         * The primary class (or other type) that the source file declares.
         */
        protected TypeCompositionStatement m_stmtType;
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
    public static String extractModuleName(File file)
        {
        if (file.exists() && file.canRead())
            {
            String name = file.getName();
            if (isExplicitSourceFile(name))
                {
                try
                    {
                    Source source = new Source(file);
                    Parser parser = new Parser(source, ErrorListener.BLACKHOLE);
                    return parser.parseModuleNameIgnoreEverythingElse();
                    }
                catch (CompilerException | IOException ignore) {}
                }
            else if (isExplicitCompiledFile(name))
                {
                try
                    {
                    return new FileStructure(file).getModuleName();
                    }
                catch (IOException ignore) {}
                }
            }

        return null;
        }

    /**
     * @return the version of the XVM code that is answering this question
     */
    public static Version getXvmVersion()
        {
        return new Version(new int[] {Constants.VERSION_MAJOR_CUR, Constants.VERSION_MINOR_CUR},
                           fileTimestampToBuildString(getJarFile()));
        }

    /**
     * @return the version of the XDK that is answering this question, or null if this running code
     *         is not part of a well-formed XDK image
     */
    public static Version getXdkVersion()
        {
        try
            {
            return getModuleVersion(new File(getJarFile().getParentFile().getParentFile(), "lib/ecstasy.xtc"));
            }
        catch (Exception ignore)
            {
            return null;
            }
        }

    /**
     * @return the version of the XDK that is answering this question, or null if this running code
     *         is not part of a well-formed XDK image
     */
    private static File getJarFile()
        {
        Class clz    = ModuleInfo.class;
        URL   jarUrl = null;
        try
            {
            jarUrl = clz.getProtectionDomain().getCodeSource().getLocation();
            }
        catch (SecurityException | NullPointerException ignore) {}

        if (jarUrl == null)
            {
            URL clzUrl = clz.getResource(clz.getSimpleName() + ".class");
            if (clzUrl != null)
                {
                String clzPath = clzUrl.toString();
                String clzTail = clz.getCanonicalName().replace('.', '/') + ".class";
                if (clzPath.endsWith(clzTail))
                    {
                    try
                        {
                        jarUrl = new URL(clzPath.substring(0, clzPath.length() - clzTail.length()));
                        }
                    catch (MalformedURLException e) {}
                    }
                }
            }

        if (jarUrl == null)
            {
            return null;
            }

        // possible "jar:" prefix (implies "!/" suffix)
        String jarPath = jarUrl.toString();
        if (jarPath.startsWith("jar:"))
            {
            int dot = jarPath.indexOf("!/");
            jarPath = dot < 0
                    ? jarPath.substring(4)
                    : jarPath.substring(4, dot);
            }

        File jarFile = null;
        try
            {
            if (jarPath.matches("file:[A-Za-z]:.*"))
                {
                jarPath = "file:/" + jarPath.substring(5);
                }
            jarFile = new File(new URL(jarPath).toURI());
            }
        catch (Exception ignore)
            {
            if (jarPath.startsWith("file:"))
                {
                jarFile = new File(jarPath.substring(5));
                }
            }
        return jarFile;
        }

    /**
     * @return the Version that was stamped onto the specified compiled module file
     */
    public static Version getModuleVersion(File moduleFile)
        {
        if (moduleFile == null)
            {
            throw new IllegalArgumentException("Compiled module file required");
            }

        if (!moduleFile.exists())
            {
            throw new IllegalArgumentException("Compiled module file (" + moduleFile + ") does not exist");
            }

        try
            {
            return new FileStructure(moduleFile).getModule().getVersion().getVersion();
            }
        catch (Exception ignore)
            {
            return null;
            }
        }

    /**
     * @param moduleFile  a File that contains a compiled Ecstasy module
     *
     * @return the version of the XVM code that is located in the specified compiled Ecstasy module
     */
    public static Version getModuleXvmVersion(File moduleFile)
        {
        if (moduleFile == null)
            {
            throw new IllegalArgumentException("Compiled module file required");
            }

        if (!moduleFile.exists())
            {
            throw new IllegalArgumentException("Compiled module file (" + moduleFile + ") does not exist");
            }

        try
            {
            FileStructure struct = new FileStructure(moduleFile);
            return new Version(new int[] {struct.getFileMajorVersion(), struct.getFileMinorVersion()},
                               fileTimestampToBuildString(moduleFile));
            }
        catch (IOException e)
            {
            throw new RuntimeException("Failed to read module file: " + moduleFile);
            }
        }

    /**
     * @param file  the file to obtain the modification date from
     *
     * @return the modification date in the format "YYYY-MM-DD.HH-MM-SS", or null if it could not be
     *         determined
     */
    private static String fileTimestampToBuildString(File file)
        {
        try
            {
            long timestamp = file.lastModified();
            return timestamp == 0
                    ? null
                    : dateString(timestamp).replace(':', '-').replace(' ', '.');
            }
        catch (RuntimeException ignore)
            {
            return null;
            }
        }

    /**
     * Determine if the specified module name is an explicit Ecstasy source or compiled module file
     * name.
     *
     * @param sFile  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    public static boolean isExplicitEcstasyFile(String sFile)
        {
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
    public static boolean isExplicitSourceFile(String sFile)
        {
        String sExt = getExtension(sFile);
        return "x".equalsIgnoreCase(sExt);
        }

    /**
     * Obtain an array of files from the specified directory that are Ecstasy source files.
     *
     * @param dir  a directory that may contain Ecstasy source files
     *
     * @return an array of zero or more source files
     */
    public static File[] sourceFiles(File dir)
        {
        return listFiles(dir, "x");
        }

    /**
     * Determine if the specified module name is an explicit Ecstasy compiled module file name.
     *
     * @param sFile  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    public static boolean isExplicitCompiledFile(String sFile)
        {
        String sExt = getExtension(sFile);
        return "xtc".equalsIgnoreCase(sExt);
        }

    /**
     * Obtain an array of files from the specified directory that are Ecstasy compiled module files.
     *
     * @param dir  a directory that may contain Ecstasy compiled module files
     *
     * @return an array of zero or more compiled module files
     */
    public static File[] compiledFiles(File dir)
        {
        return listFiles(dir, "xtc");
        }

    /**
     * @param dir  a directory
     *
     * @return true iff the directory appears to be a project directory
     */
    public static boolean isProjectDir(File dir)
        {
        return dir != null && dir.isDirectory() &&
            (new File(dir, "src"   ).exists() && !new File(dir, "src.x"   ).exists() ||
             new File(dir, "source").exists() && !new File(dir, "source.x").exists());
        }

    /**
     * Walk up the directory tree to find a project directory (or make a best guess).
     *
     * @param dir  the directory to start from
     *
     * @return the best-guess project directory
     */
    public static File projectDirFromSubDir(File dir)
        {
        assert dir != null;

        File   prjDir = null;
        String name   = dir.getName();
        if (   "build" .equalsIgnoreCase(name)
            || "target".equalsIgnoreCase(name))
            {
            prjDir = dir.getParentFile();
            }
        else
            {
            prjDir = dir;

            if (   "x"      .equalsIgnoreCase(name)
                || "xtc"    .equalsIgnoreCase(name)
                || "ecstasy".equalsIgnoreCase(name))
                {
                dir = prjDir.getParentFile();
                if (dir == null)
                    {
                    return prjDir;
                    }
                prjDir = dir;
                name   = dir.getName();
                }

            if (   "main".equalsIgnoreCase(name)
                || "test".equalsIgnoreCase(name))
                {
                dir = prjDir.getParentFile();
                if (dir == null)
                    {
                    return prjDir;
                    }
                prjDir = dir;
                name   = dir.getName();
                }

            if (   "src"   .equalsIgnoreCase(name)
                || "source".equalsIgnoreCase(name))
                {
                dir = prjDir.getParentFile();
                if (dir == null)
                    {
                    return prjDir;
                    }
                prjDir = dir;
                }
            }

        if (prjDir == null)
            {
            prjDir = dir;
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
    public static File sourceDirFromPrjDir(File prjDir)
        {
        assert prjDir != null;

        // locate the source directory, starting by assuming that the project directory could also
        // be the source directory
        File curDir = prjDir;
        File srcDir = curDir;
        FindSrcDir: for (int i = 0; i <= 3; ++i)
            {
            File[] srcFiles = sourceFiles(curDir);
            if (srcFiles.length == 0)
                {
                File subdir;
                switch (i)
                    {
                    case 0:
                        if ((subdir = new File(curDir, "src")).isDirectory() ||
                            (subdir = new File(curDir, "source")).isDirectory())
                            {
                            curDir = subdir;
                            continue;
                            }
                        ++i;
                        // fall through
                    case 1:
                        // note: we explicitly do NOT look for "test"
                        if ((subdir = new File(curDir, "main")).isDirectory())
                            {
                            curDir = subdir;
                            continue;
                            }
                        ++i;
                        // fall through
                    case 2:
                        if ((subdir = new File(curDir, "x")).isDirectory() ||
                            (subdir = new File(curDir, "xtc")).isDirectory() ||
                            (subdir = new File(curDir, "ecstasy")).isDirectory())
                            {
                            curDir = subdir;
                            continue;
                            }
                        ++i;
                        // fall through
                    default:
                        break FindSrcDir;
                    }
                }
            else
                {
                srcDir = curDir;
                break;
                }
            }

        return srcDir;
        }

    /**
     * Given a project directory, produce the best guess at the location of the binary (compiled
     * module) directory.
     *
     * @param prjDir  the project directory
     *
     * @return the best guess at the location of the binary directory
     */
    public static File binaryDirFromPrjDir(File prjDir)
        {
        assert prjDir != null;

        File subdir;
        if ((subdir = new File(prjDir, "build")).isDirectory() ||
            (subdir = new File(prjDir, "target")).isDirectory())
            {
            return subdir;
            }

        return prjDir;
        }


    // ----- fields --------------------------------------------------------------------------------

    private enum Status  {Unknown, NotExists, Exists}
    private enum Content {Unknown, Invalid, Module}

    public static final File[] NO_FILES = new File[0];

    transient long accumulator;

    private File        fileSpec;       // original file spec that was used to create this info
    private String      fileName;       // selected file name, without the file extension
    private String      moduleName;     // the module name (potentially qualified)
    private File        projectDir;     // the directory containing the "project"

    private Status      sourceStatus;
    private File        sourceDir;      // directory containing source code
    private File        sourceFile;     // the "module root" source code file
    private boolean     sourceIsTree;   // true iff the module sources include subdirectories
    private Content     sourceContent = Content.Unknown;  // what is known about the module source file content
    private long        sourceTimestamp;// last known change to a source file
    private Node        sourceNode;     // root node of the source tree

    private ResourceDir resourceDir;
    private long        resourceTimestamp;

    private Status      binaryStatus = Status.Unknown;
    private File        binaryDir;
    private File        binaryFile;
    private Version     binaryVersion;
    private Content     binaryContent = Content.Unknown;  // what is known about the compiled module file content
    private long        binaryTimestamp;
    }