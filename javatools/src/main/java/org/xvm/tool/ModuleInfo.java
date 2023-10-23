package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.TreeMap;
import java.util.function.Consumer;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.util.Handy;
import org.xvm.util.Hash;


import static org.xvm.tool.Launcher.getExtension;
import static org.xvm.tool.Launcher.extractModuleName;
import static org.xvm.tool.Launcher.isExplicitCompiledFile;
import static org.xvm.tool.Launcher.isExplicitEcstasyFile;
import static org.xvm.tool.Launcher.isExplicitSourceFile;
import static org.xvm.tool.Launcher.isProjectDir;
import static org.xvm.tool.Launcher.removeExtension;
import static org.xvm.tool.Launcher.resolveFile;

import static org.xvm.util.Handy.parseDelimitedString;


/**
 * Information gleaned about a module from a single specified file. This is a lazily populated
 * structure, not a point-in-time snapshot; as a result, in the presence of realtime changes
 * occurring to the module source/resource files or compiled files after this object is
 * instantiated, this can not be depended upon to reflect either the snapshot state of the
 * module as it was when the ModuleInfo was instantiated, nor the snapshot state of the module
 * as it is right now.
 *
 * TODO test cases:
 * - stand alone .x file (both in normal and prj dir structure)
 * - paired .x / .xtc file (both in normal and prj dir structure)
 * - stand alone .xtc file (both in normal and prj dir structure)
 * - .x tree (both in normal and prj dir structure)
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

        fileSpec = resolveFile(fileSpec);
        Search: if (fileSpec.exists())
            {
            if (fileSpec.isDirectory())
                {
                // could be:
                // 1) project dir
                // 2) any of ./src/main/x (etc.) dir
                // 3) any source dir nested somewhere under ./src/main/x (etc.) dir
                // 4) any of ./build or ./target dir
                File[] srcFiles;
                if ((srcFiles = fileSpec.listFiles(f -> !f.isDirectory()
                       && getExtension(f.getName()).equalsIgnoreCase("x"))).length > 0)
                    {
                    // we're somewhere down in the source directory hierarchy
                    File dir       = fileSpec;
                    File bestGuess = null;
                    while (true)
                        {
                        if (srcFiles.length == 1)
                            {
                            // see if the one file is the module source file
                            File   srcFile = srcFiles[0];
                            String module  = extractModuleName(srcFile);
                            if (module == null)
                                {
                                bestGuess = srcFile;
                                }
                            else
                                {
                                srcFile       = resolveFile(srcFile);
                                fileSpec      = srcFile;
                                moduleName    = module;
                                sourceFile    = srcFile;
                                sourceDir     = srcFile.getParentFile();
                                sourceStatus  = Status.Exists;
                                fileName      = removeExtension(srcFile.getName());
                                sourceIsTree  = new File(sourceDir, fileName).exists();
                                sourceContent = Content.Module;
                                break Search;
                                }
                            }

                        // walk up to the next parent directory and see if it contains .x files
                        dir      = dir.getParentFile();
                        srcFiles = dir == null ? NO_FILES : dir.listFiles(f -> !f.isDirectory()
                                && getExtension(f.getName()).equalsIgnoreCase("x"));
                        if (srcFiles.length == 0)
                            {
                            // failed to find the module source file
                            if (bestGuess == null)
                                {
                                fileName      = removeExtension(fileSpec.getName());
                                sourceDir     = fileSpec;
                                sourceFile    = new File(sourceDir, fileName + ".x");
                                sourceStatus  = Status.NotExists;
                                sourceIsTree  = new File(sourceDir, fileName).exists();
                                sourceContent = Content.Invalid;
                                }
                            else
                                {
                                sourceFile    = bestGuess;
                                sourceDir     = bestGuess.getParentFile();
                                sourceStatus  = Status.Exists;
                                fileName      = removeExtension(bestGuess.getName());
                                sourceIsTree  = new File(sourceDir, fileName).exists();
                                sourceContent = Content.Invalid;
                                }
                            break Search;
                            }
                        }
                    }

                File dir    = fileSpec;
                int  cSteps = 0;
                do
                    {
                    // project dir expected to contain a src (or source) directory
                    if (isProjectDir(fileSpec))
                        {
                        projectDir = dir;
                        fileSpec   = dir;
                        break Search;
                        }

                    String name = dir.getName();
                    if (   name.equalsIgnoreCase("src")
                        || name.equalsIgnoreCase("source")
                        || name.equalsIgnoreCase("main")
                        || name.equalsIgnoreCase("test")
                        || name.equalsIgnoreCase("x")
                        || name.equalsIgnoreCase("ecstasy")
                        || name.equalsIgnoreCase("build")
                        || name.equalsIgnoreCase("target"))
                        {
                        dir = dir.getParentFile();
                        }
                    else
                        {
                        break;
                        }
                    }
                while (++cSteps < 3 && dir != null && dir.isDirectory());

                // no idea what the module is
                fileName      = parseDelimitedString(fileSpec.getName(), '.')[0];
                moduleName    = fileName;
                projectDir    = fileSpec;
                sourceFile    = new File(fileSpec, fileName + ".x");
                sourceDir     = fileSpec;
                sourceStatus  = Status.NotExists;
                sourceContent = Content.Invalid;
                binaryFile    = new File(fileSpec, fileName + ".xtc");
                binaryDir     = fileSpec;
                binaryStatus  = Status.NotExists;
                binaryContent = Content.Invalid;
                }
            else
                {
                // fileSpec is a file (not directory) that exists
                String sName = fileSpec.getName();
                if (isExplicitSourceFile(sName))
                    {
                    moduleName = extractModuleName(fileSpec);
                    if (moduleName != null)
                        {
                        fileName      = parseDelimitedString(sName, '.')[0];
                        sourceFile    = fileSpec;
                        sourceDir     = sourceFile.getParentFile();
                        sourceIsTree  = new File(sourceDir, fileName).exists();
                        sourceStatus  = Status.Exists;
                        sourceContent = Content.Module;
                        break Search;
                        }

                    File   bestGuess = fileSpec;
                    File   dir       = fileSpec.getParentFile().getParentFile();
                    File[] srcFiles  = dir == null ? NO_FILES : dir.listFiles(f -> !f.isDirectory()
                                        && getExtension(f.getName()).equalsIgnoreCase("x"));
                    while (true)
                        {
                        if (srcFiles.length == 1)
                            {
                            // see if the one file is the module source file
                            File   srcFile = srcFiles[0];
                            String module  = extractModuleName(srcFile);
                            if (module == null)
                                {
                                bestGuess = srcFile;
                                }
                            else
                                {
                                srcFile       = resolveFile(srcFile);
                                fileSpec      = srcFile;
                                fileName      = removeExtension(srcFile.getName());
                                moduleName    = module;
                                sourceFile    = srcFile;
                                sourceDir     = srcFile.getParentFile();
                                sourceIsTree  = new File(sourceDir, fileName).exists();
                                sourceStatus  = Status.Exists;
                                sourceContent = Content.Module;
                                break Search;
                                }
                            }

                        // walk up to the next parent directory and see if it contains .x files
                        if (srcFiles.length == 0)
                            {
                            // failed to find the module source file
                            fileName      = removeExtension(bestGuess.getName());
                            sourceFile    = bestGuess;
                            sourceDir     = bestGuess.getParentFile();
                            sourceIsTree  = new File(sourceDir, fileName).exists();
                            sourceStatus  = Status.Exists;
                            sourceContent = Content.Invalid;
                            break Search;
                            }

                        dir      = dir.getParentFile();
                        srcFiles = dir == null ? NO_FILES : dir.listFiles(f -> !f.isDirectory()
                                    && getExtension(f.getName()).equalsIgnoreCase("x"));
                        }
                    }

                if (isExplicitCompiledFile(sName))
                    {
                    binaryDir     = fileSpec.getParentFile();
                    binaryFile    = fileSpec;
                    binaryStatus  = Status.Exists;
                    fileName      = parseDelimitedString(fileSpec.getName(), '.')[0];
                    moduleName    = extractModuleName(fileSpec);
                    if (moduleName == null)
                        {
                        moduleName    = fileName;
                        binaryContent = Content.Invalid;
                        }
                    else
                        {
                        binaryContent = Content.Module;
                        }
                    break Search;
                    }

                // no idea what the module is
                fileName      = parseDelimitedString(fileSpec.getName(), '.')[0];
                moduleName    = fileName;
                projectDir    = fileSpec.getParentFile();
                sourceDir     = projectDir;
                sourceFile    = new File(sourceDir, fileName + ".x");
                sourceStatus  = Status.NotExists;
                sourceContent = Content.Invalid;
                binaryDir     = projectDir;
                binaryFile    = new File(binaryDir, fileName + ".xtc");
                binaryStatus  = Status.NotExists;
                binaryContent = Content.Invalid;
                }
            }
        else // !fileSpec.exists()
            {
            // the tools allow "ModuleName" to be used instead of either "ModuleName.x" or
            // "ModuleName.xtc"; e.g. "xec ModuleName"
            File dirSpec = fileSpec.getParentFile();
            if (dirSpec.isDirectory())
                {
                String sName = fileSpec.getName();
                if (isExplicitEcstasyFile(sName))
                    {
                    sName = removeExtension(sName);
                    }

                File fileSrc = new File(dirSpec, sName + ".x");
                if (fileSrc.exists() && !fileSrc.isDirectory())
                    {
                    fileSpec     = fileSrc;
                    sourceFile   = fileSrc;
                    sourceDir    = fileSrc.getParentFile();
                    sourceStatus = Status.Exists;
                    File subdir  = new File(sourceDir, removeExtension(fileSrc.getName()));
                    sourceIsTree = subdir.exists() && subdir.isDirectory();
                    }

                File fileBin = new File(dirSpec, sName + ".xtc");
                if (fileBin.exists() && !fileBin.isDirectory())
                    {
                    if (sourceFile == null)
                        {
                        fileSpec = fileBin;
                        }
                    binaryFile   = fileBin;
                    binaryDir    = binaryFile.getParentFile();
                    binaryStatus = Status.Exists;
                    }

                if (sourceFile != null || binaryFile != null)
                    {
                    if (Handy.equals(sourceDir, binaryDir))
                        {
                        // both files in the same directory
                        projectDir = sourceDir;
                        }
                    break Search;
                    }
                }

            // it's possible that the fileSpec points to a yet-to-be-created build or target dir
            // or file
            String name = fileSpec.getName();
            if (isExplicitCompiledFile(name))
                {
                // walk the directory up (up to 3 steps) to see if this is within the expected
                // project dir format
                File binDir = dirSpec;
                File prjDir = dirSpec;
                int  cSteps = 0;
                while (++cSteps <= 3 && prjDir != null)
                    {
                    if (isProjectDir(prjDir))
                        {
                        binaryFile   = fileSpec;
                        binaryDir    = binDir;
                        binaryStatus = Status.NotExists;
                        projectDir   = prjDir;
                        fileSpec     = prjDir;
                        break Search;
                        }
                    }
                }

            // assume the name is right but that the file (or dir) doesn't exist
            if (isExplicitSourceFile(name))
                {
                sourceFile    = fileSpec;
                sourceDir     = fileSpec.getParentFile();
                sourceIsTree  = false;
                sourceStatus  = Status.NotExists;
                sourceContent = Content.Invalid;
                projectDir    = sourceDir;
                }
            else if (isExplicitCompiledFile(name))
                {
                binaryFile    = fileSpec;
                binaryDir     = fileSpec.getParentFile();
                binaryStatus  = Status.NotExists;
                binaryContent = Content.Invalid;
                projectDir    = binaryDir;
                }
            else
                {
                name          = parseDelimitedString(name, '.')[0];
                projectDir    = fileSpec;
                sourceFile    = new File(fileSpec, name + ".x");
                sourceDir     = fileSpec;
                sourceStatus  = Status.NotExists;
                sourceContent = Content.Invalid;
                binaryFile    = new File(fileSpec, name + ".xtc");
                binaryDir     = fileSpec;
                binaryStatus  = Status.NotExists;
                binaryContent = Content.Invalid;
                }
            }

        this.fileSpec = resolveFile(fileSpec);

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
                    if (sExt != null && sExt.equals("xtc"))
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
                if (sExt != null && sExt.equals("xtc"))
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

            resourceDir     = new ResourceDir(resourceSpecs);
            resourcesStatus = resourceSpecs.length == 0 ? Status.NotExists : Status.Exists;
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
     * @return the inferred "project directory", which may be one or more steps above the
     *         directory containing the module source code
     */
    public File getProjectDir()
        {
        if (projectDir == null)
            {
            // start from the source and/or build dir
            File dirSrc = sourceDir;
            File dirBin = binaryDir;
            if (Handy.equals(dirSrc, dirBin))
                {
                // note: may be null
                projectDir = dirSrc;
                }

            File fromSrc = dirSrc;
            if (fromSrc != null)
                {
                // e.g. src/main/x
                File   nextdir = fromSrc.getParentFile();
                String dirName = fromSrc.getName();
                if (dirName.equalsIgnoreCase("x") || dirName.equalsIgnoreCase("ecstasy"))
                    {
                    fromSrc = nextdir;
                    nextdir = fromSrc.getParentFile();
                    dirName = fromSrc.getName();
                    }

                if (dirName.equalsIgnoreCase("main") || dirName.equalsIgnoreCase("test"))
                    {
                    fromSrc = nextdir;
                    nextdir = fromSrc.getParentFile();
                    dirName = fromSrc.getName();
                    }

                if (dirName.equalsIgnoreCase("src") || dirName.equalsIgnoreCase("source"))
                    {
                    fromSrc = nextdir;
                    }
                }

            File fromBin = dirBin;
            if (fromBin != null)
                {
                // e.g. ./build/ or ./target/
                File   nextdir = fromBin.getParentFile();
                String dirName = dirBin.getName();
                if (dirName.equalsIgnoreCase("build") || dirName.equalsIgnoreCase("target"))
                    {
                    fromBin = nextdir;
                    }
                }

            if (fromBin == null)
                {
                assert fromSrc != null;
                projectDir = fromSrc;
                }
            else if (fromSrc == null)
                {
                assert fromBin != null;
                projectDir = fromBin;
                }
            else
                {
                // might not be the right answer, but there is no obvious right answer
                projectDir = fromSrc;
                }
            }

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
            && binTimestamp >= getResourcesTimestamp();
        }

    /**
     * @return True if the module name uses a qualified format
     */
    public boolean isModuleNameQualified()
        {
        return getQualifiedModuleName().indexOf('.') >= 0;
        }

    /**
     * @return the module name, which may be qualified
     */
    public String getQualifiedModuleName()
        {
        return getQualifiedModuleName(false);
        }

    /**
     * @param fromGBF  true iff this is being called from the getBinaryFile() method
     *
     * @return the module name, which may be qualified
     */
    private String getQualifiedModuleName(boolean fromGBF)
        {
        if (moduleName == null)
            {
            File fileSrc = getSourceFile();
            if (sourceStatus == Status.Exists)
                {
                // obtain the module name from the source file, if possible
                switch (sourceContent)
                    {
                    case Module:
                        assert moduleName != null;
                        return moduleName;

                    case Unknown:
                        sourceContent = Content.Invalid;
                        try
                            {
                            Source source = new Source(fileSrc, 0);
                            Parser parser = new Parser(source, ErrorListener.BLACKHOLE);
                            moduleName    = parser.parseModuleNameIgnoreEverythingElse();
                            if (moduleName != null)
                                {
                                sourceContent = Content.Module;
                                return moduleName;
                                }
                            }
                        catch (CompilerException | IOException e)
                            {
                            }
                    }
                }

            File fileBin = fromGBF ? binaryFile : getBinaryFile();
            if (binaryStatus == Status.Exists)
                {
                // obtain the module name from the compiled module, if possible
                switch (binaryContent)
                    {
                    case Module:
                        assert moduleName != null;
                        return moduleName;

                    case Unknown:
                        binaryContent = Content.Invalid;
                        try
                            {
                            FileStructure struct = new FileStructure(fileBin);
                            moduleName    = struct.getModuleName();
                            if (moduleName != null)
                                {
                                binaryContent = Content.Module;
                                return moduleName;
                                }
                            }
                        catch (Exception ignore) {}
                    }
                }

            // guess at the module name based on the source file name, binary file name, or
            // project dir name
            assert moduleName == null;
            if (fileSrc != null)
                {
                moduleName = removeExtension(fileSrc.getName());
                }
            else if (fileBin != null)
                {
                moduleName = removeExtension(fileBin.getName());
                }
            else
                {
                File filePrj = getProjectDir();
                if (filePrj == null)
                    {
                    // just use the name from the original file spec
                    moduleName = fileSpec.getName();
                    }
                else
                    {
                    moduleName = filePrj.getName();
                    }
                }
            }

        return moduleName;
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
        if (sourceStatus == Status.Unknown)
            {
            FindSrcDir: if (sourceDir == null && projectDir != null)
                {
                // locate the source directory
                File dir = projectDir;
                for (int i = 0; i <= 3; ++i)
                    {
                    File[] srcFiles = getSourceFiles(dir);
                    switch (srcFiles.length)
                        {
                        case 0:
                            File subdir;
                            switch (i)
                                {
                                case 0:
                                    if ((subdir = new File(dir, "src")).isDirectory() ||
                                        (subdir = new File(dir, "source")).isDirectory())
                                        {
                                        dir = subdir;
                                        continue;
                                        }
                                    ++i;
                                    // fall through
                                case 1:
                                    // note: we explicitly do NOT look for "test"
                                    if ((subdir = new File(dir, "main")).isDirectory())
                                        {
                                        dir = subdir;
                                        continue;
                                        }
                                    ++i;
                                    // fall through
                                case 2:
                                    if ((subdir = new File(dir, "x")).isDirectory() ||
                                        (subdir = new File(dir, "ecstasy")).isDirectory())
                                        {
                                        dir = subdir;
                                        continue;
                                        }
                                    ++i;
                                    // fall through
                                default:
                                    break FindSrcDir;
                                }
                        case 1:
                            sourceDir  = dir;
                            sourceFile = srcFiles[0];
                            break FindSrcDir;

                        default:
                            sourceDir = dir;
                            break FindSrcDir;
                        }
                    }
                }

            if (sourceFile != null && sourceDir == null)
                {
                sourceDir = sourceFile.getParentFile();
                }
            else if (sourceFile == null && sourceDir != null)
                {
                File[] srcFiles = getSourceFiles(projectDir);
                if (srcFiles.length == 1)
                    {
                    sourceFile = srcFiles[0];
                    }
                else if (fileName != null)
                    {
                    sourceFile = new File(sourceDir, fileName + ".x");
                    }
                else if (binaryFile != null)
                    {
                    sourceFile = new File(sourceDir, parseDelimitedString(binaryFile.getName(), '.')[0] + ".x");
                    }
                else if (projectDir != null)
                    {
                    sourceFile = new File(sourceDir, parseDelimitedString(projectDir.getName(), '.')[0] + ".x");
                    }
                }

            if (sourceFile == null)
                {
                sourceStatus = Status.NotExists;
                }
            else
                {
                if (sourceDir == null)
                    {
                    sourceDir = sourceFile.getParentFile();
                    }

                String withoutExtension = parseDelimitedString(sourceFile.getName(), '.')[0];
                if (fileName == null)
                    {
                    fileName = withoutExtension;
                    }

                if (sourceFile.exists())
                    {
                    sourceStatus = Status.Exists;
                    File subdir  = new File(sourceDir, withoutExtension);
                    sourceIsTree = subdir.isDirectory();
                    }
                else
                    {
                    sourceStatus = Status.NotExists;
                    }
                }
            }
        else
            {
            assert sourceFile != null || sourceStatus == Status.NotExists;
            }

        return sourceFile;
        }

    /**
     * @return the module source file, or null if none can be resolved
     */
    public File getSourceDir()
        {
        getSourceFile();
        return sourceDir;
        }

    /**
     * @return true iff the source code appears to be organized using the multiple-file,
     *         hierarchical, name-spaced tree
     */
    public boolean isSourceTree()
        {
        getSourceFile();
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

    /**
     * @return lazily computed and cached hash of the resource directory contents, or 0 if none
     */
    public int getSourceHash()
        {
        if (sourceHash == 0)
            {
            File file = getSourceFile();
            if (file != null)
                {
                // TODO why not SHA3-256 of contents?
                int hash = Hash.of(file.getName(),
                           Hash.of(file.length(),
                           Hash.of(file.lastModified())));

                if (sourceIsTree)
                    {
                    synchronized (this)
                        {
                        accumulator = hash;
                        visitTree(new File(sourceDir, removeExtension(file.getName())), "x",
                                  f -> accumulator = Hash.of(f.getName(),
                                                     Hash.of(file.length(),
                                                     Hash.of(file.lastModified(), (int) accumulator))));
                        sourceHash  = (int) accumulator;
                        accumulator = 0;
                        }
                    }
                }
            }

        return sourceHash;
        }

    /**
     * @param dir  a file, directory, or null
     *
     * @return an array of 0 or more source files; never null
     */
    private File[] getSourceFiles(File dir)
        {
        return dir == null || !dir.isDirectory()
                ? NO_FILES
                : dir.listFiles(f -> !f.isDirectory() && "x".equalsIgnoreCase(getExtension(f.getName())));
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
    public long getResourcesTimestamp()
        {
        if (resourcesTimestamp == 0L)
            {
            resourcesTimestamp = getResourceDir().getTimestamp();
            }

        return resourcesTimestamp;
        }

    /**
     * @return lazily computed and cached hash of the resource directory contents, or 0 if none
     */
    public int getResourcesHash()
        {
        if (resourcesHash == 0)
            {
            resourcesHash = getResourceDir().getHash();
            }

        return resourcesHash;
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
            String fullName  = getQualifiedModuleName(true);
            String shortName = fullName == null ? null : getSimpleModuleName();
            String dftName   = fileName;
            if (fullName == null && shortName == null && dftName == null)
                {
                binaryStatus = Status.NotExists;
                return null;
                }

            // calculating the BinaryFile relies on the BinaryDir, and not vice-versa; if there's an
            // identifiable binary directory, see if the binary file is there
            File binDir = getBinaryDir();
            if (binDir != null && binDir.exists())
                {
                File test;
                if (fullName  != null && (test = new File(binDir, fullName  + ".xtc")).exists() && !test.isDirectory() ||
                    shortName != null && (test = new File(binDir, shortName + ".xtc")).exists() && !test.isDirectory() ||
                    dftName   != null && (test = new File(binDir, dftName   + ".xtc")).exists() && !test.isDirectory())
                    {
                    binaryFile   = test;
                    binaryStatus = Status.Exists;
                    }
                }

            // check to see if someone accidentally left the compiled file next to the source file
            File srcDir = null;
            if (binaryFile == null || !binaryFile.exists()
                    && (srcDir = getSourceDir()) != null && srcDir.exists())
                {
                File test;
                if (fullName  != null && (test = new File(srcDir, fullName  + ".xtc")).exists() && !test.isDirectory() ||
                    shortName != null && (test = new File(srcDir, shortName + ".xtc")).exists() && !test.isDirectory() ||
                    dftName   != null && (test = new File(srcDir, dftName   + ".xtc")).exists() && !test.isDirectory())
                    {
                    binaryFile   = test;
                    binaryDir    = srcDir;     // <-- this may conflict with what we already learned
                    binaryStatus = Status.Exists;
                    }
                }

            if (binaryFile == null && binDir != null)
                {
                String binName;
                if (sourceFile == null)
                    {
                    binName = (dftName   != null ? dftName
                            :  shortName != null ? shortName
                            :  fullName) + ".xtc";
                    }
                else
                    {
                    binName = removeExtension(sourceFile.getName()) + ".xtc";
                    }

                binaryFile   = new File(binDir, binName);
                binaryStatus = Status.NotExists;
                }
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
            if (binaryFile == null)
                {
                // we may have already checked this
                if (binaryStatus == Status.NotExists)
                    {
                    return null;
                    }

                File projectDir = getProjectDir();
                File sourceDir  = getSourceDir();
                if (sourceDir.equals(projectDir))
                    {
                    binaryDir = projectDir;
                    }
                else
                    {
                    File buildDir   = new File(projectDir, "build");
                    File targetDir  = new File(projectDir, "target");
                    if (buildDir.isDirectory())
                        {
                        binaryDir = buildDir;
                        }
                    else if (targetDir.isDirectory())
                        {
                        binaryDir = targetDir;
                        }
                    else
                        {
                        binaryStatus = Status.NotExists;
                        }
                    }
                }
            else
                {
                binaryDir = binaryFile.getParentFile();
                }
            }

        return binaryDir;
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
     * @return lazily computed and cached hash of the compiled module file, or 0 if none
     */
    public int getBinaryHash()
        {
        if (binaryHash == 0)
            {
            File file = getBinaryFile();
            if (file != null && file.exists())
                {
                // TODO why not SHA3-256 of contents?
                binaryHash = Hash.of(file.getName(),
                             Hash.of(file.length(),
                             Hash.of(file.lastModified())));
                }
            }

        return resourcesHash;
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
                : dir.listFiles(f -> !f.isDirectory() && getExtension(f.getName()).equalsIgnoreCase("xtc"));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Module(name=")
          .append(moduleName == null ? "<unknown>" : moduleName)
          .append(", from file=")
          .append(fileSpec);

        // TODO
        //.append(", src=")
        //.append(sourceFile)
        //.append(sourceExists ? " (exists)" : " (missing)")
        //.append(", res=")
        //.append(resourcesDir)
        //.append(resourcesExists ? " (exists)" : " (missing)")
        //.append(", bin=")
        //.append(binaryFile)
        //.append(binaryExists ? " (exists)" : " (missing)")

        return sb.toString();
        }


    // ----- internal ----------------------------------------------------------------------

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
            if (ext == null || getExtension(name).equals(ext))
                {
                assert !children.containsKey(name);
                children.put(name, child);
                }
            }

        for (File child : children.values())
            {
            if (dir.isDirectory())
                {
                visitTree(child, ext, visitor);
                }
            else
                {
                visitor.accept(child);
                }
            }
        }

    private enum Status  {Unknown, NotExists, Exists}
    private enum Content {Unknown, Invalid, Module}

    public static final File[] NO_FILES = new File[0];

    transient long accumulator;

    private File        fileSpec;       // original file spec that was used to create this info
    private String      fileName;       // short file name, sans extension
    private String      moduleName;     // the module name (potentially qualified)
    private File        projectDir;     // the directory containing the "project"

    private Status      sourceStatus = Status.Unknown;
    private File        sourceDir;      // directory containing source code
    private File        sourceFile;     // the "module root" source code file
    private boolean     sourceIsTree;   // true iff the module sources include subdirectories
    private Content     sourceContent = Content.Unknown;  // what is known about the module source file content
    private long        sourceTimestamp;// last known change to a source file
    private int         sourceHash;     // hash of source file attributes

    private Status      resourcesStatus = Status.Unknown;
    private ResourceDir resourceDir;
    private long        resourcesTimestamp;
    private int         resourcesHash;

    private Status      binaryStatus = Status.Unknown;
    private File        binaryDir;
    private File        binaryFile;
    private Content     binaryContent = Content.Unknown;  // what is known about the compiled module file content
    private long        binaryTimestamp;
    private int         binaryHash;
    }