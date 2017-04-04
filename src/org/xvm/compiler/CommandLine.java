package org.xvm.compiler;


import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.StructureContainer;
import org.xvm.compiler.ast.BlockStatement;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeDeclarationStatement;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import java.io.File;
import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.xvm.util.Handy.indentLines;


/**
 * This is the command-line harness for the prototype Ecstasy compiler.
 *
 * <p/>Find the root of the module containing the code in the current directory, and compile it, placing
 * the result in the default location:
 *
 * <p/>{@code  xtc}
 *
 * <p/>Compile the specified module, placing the result in the default location:
 *
 * <p/>{@code  xtc ./path/to/module.x}
 *
 * <p/>Compile just the specified file, or the minimum number of files necessary to compile the
 * specified file:
 *
 * <p/>{@code  xtc MyClass.x}
 *
 * <p/>The default location for the resulting {@code .xtc} file is based on the project structure
 * containing the module. The default location is determined by following these steps, and selecting
 * the first one that fits:
 * <ul>
 * <li>If the module is located in a directory with a writable sibling directory named "target", and
 *   the result of the compilation is a module file, then the resulting module will be stored in the
 *   "target" directory.</li>
 * <li>If the module is located in a directory with a writable sibling directory named "build", and
 *   the result of the compilation is a module or package or class file, then the resulting module,
 *   package, or class file will be stored in the "build" directory.</li>
 * <li>If the module is located in a directory with a writable sibling directory named "classes", and
 *   the result of the compilation is a package or class file, then the resulting package or class
 *   file will be stored in the "classes" directory.</li>
 * <li>Otherwise, the {@code .xtc} file will be placed in the directory containing the module root.</li>
 * </ul>
 *
 * <p/>The location of the resulting {@code .xtc} file can be specified by using the {@code -D} option;
 * for example:
 *
 * <p/>{@code  xtc -D ~/modules/}
 *
 * <p/>In addition to built-in Ecstasy modules and modules located in the Ecstasy runtime repository,
 * it is possible to provide a search path for modules that will be used by the compiler:
 *
 * <p/>{@code  xtc -M ~/modules/:../build/}
 *
 * <p/>Other command line options:
 * <ul>
 * <li>{@code -nosrc} - do not include source code in the compiled module</li>
 * <li>{@code -nodbg} - do not include debugging information in the compiled module</li>
 * <li>{@code -nodoc} - do not include documentation in the compiled module</li>
 * <li>{@code -strict} - convert warnings to errors</li>
 * <li>{@code -nowarn} - suppress warnings</li>
 * <li>{@code -verbose} - provide information about the work being done by the compilation process</li>
 * </ul>
 *
 * @author cp 2017.03.23
 */
public class CommandLine
    {
    protected String[]          args            = null;
    protected List<File>        sources         = new ArrayList<>();
    protected Options           opts            = new Options();
    protected List<String>      deferred        = new ArrayList<>();
    protected boolean           error           = false;
    protected Map<File, Node>   modules         = new ListMap<>();
    protected Map<String, Node> modulesByName   = new HashMap<>();

    public static void main(String[] args)
            throws Exception
        {
        // parse all the command line arguments, etc.
        CommandLine cmd = new CommandLine();
        cmd.parseArgs(args);
        cmd.checkTerminalFailure();
        cmd.showCompilerOptions();

        // what are the modules that need to be compiled?
        cmd.selectCompileTargets();
        cmd.checkTerminalFailure();

        // parse the modules
        cmd.parseSource();
        cmd.checkTerminalFailure();

        // assign names
        cmd.arrangeSourceByName();
        cmd.checkTerminalFailure();

        // syntax check
        cmd.checkSyntax();
        cmd.checkTerminalFailure();

        // dependency resolution
        cmd.resolveDependencies();
        cmd.checkTerminalFailure();

        // write out the results
        cmd.produceModules();
        cmd.checkTerminalFailure();

        if (cmd.opts.verbose)
            {
            cmd.dump();
            }
        }

    /**
     * Parse and store off the command line arguments.
     *
     * @param args  the arguments from the command line, in the format passed to a "main" method
     */
    protected void parseArgs(String[] args)
        {
        assert this.args == null;
        this.args = args;

        if (args != null)
            {
            String sContinued = null;
            for (String s : args)
                {
                if (s != null && s.length() > 0)
                    {
                    if (s.equals("-D") || s.equals("-M"))
                        {
                        sContinued = s;
                        }
                    else if (s.startsWith("-D") || (sContinued != null && sContinued.equals("-D")))
                        {
                        if (s.startsWith("-D"))
                            {
                            s = s.substring(2);
                            }

                        List<File> list;
                        try
                            {
                            list = resolvePath(s);
                            }
                        catch (IOException e)
                            {
                            deferred.add("xtc: Exception resolving path \"" + s + "\": " + e);
                            continue;
                            }

                        if (list.isEmpty())
                            {
                            deferred.add("xtc: could not resolve: \"" + s + "\"");
                            error = true;
                            }
                        else if (list.size() > 1)
                            {
                            deferred.add("xtc: could not resolve to a single path: \"" + s + "\"");
                            error = true;
                            }
                        else
                            {
                            File file = list.get(0);
                            if (!file.canWrite())
                                {
                                deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                        + " not writable: \"" + file + "\"");
                                error = true;
                                }
                            if (opts.destination != null)
                                {
                                deferred.add("xtc: overwriting destination \"" + opts.destination
                                        + "\" with new destination \"" + s + "\".");
                                }
                            opts.destination = file;
                            sContinued = null;
                            }
                        }
                    else if (s.startsWith("-M") || (sContinued != null && sContinued.equals("-M")))
                        {
                        if (s.startsWith("-M"))
                            {
                            s = s.substring(2);
                            }
                        for (String sPath : Handy.parseDelimitedString(s, File.pathSeparatorChar))
                            {
                            List<File> files;
                            try
                                {
                                files = resolvePath(sPath);
                                }
                            catch (IOException e)
                                {
                                deferred.add("xtc: Exception resolving path \"" + sPath + "\": " + e);
                                continue;
                                }

                            if (files.isEmpty())
                                {
                                deferred.add("xtc: could not resolve: \"" + sPath + "\"");
                                }
                            else
                                {
                                for (File file : files)
                                    {
                                    if (file.canRead())
                                        {
                                        opts.modulePath.add(file);
                                        }
                                    else
                                        {
                                        deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                                + " not readable: \"" + file + "\"");
                                        }
                                    }
                                }
                            }
                        sContinued = null;
                        }
                    else if (s.startsWith("-X"))
                        {
                        // format is -Xname=value or -Xname:value
                        if (s.length() > 2)
                            {
                            int ofDiv = s.indexOf('=');
                            if (ofDiv < 0)
                                {
                                ofDiv = s.indexOf(':');
                                }
                            if (ofDiv > 2)
                                {
                                String key = s.substring(2, ofDiv);
                                String val = s.substring(ofDiv + 1);
                                opts.customCfg.put(key, val);
                                }
                            }
                        }
                    else if (s.equals("-nosrc"))
                        {
                        opts.includeSrc = false;
                        }
                    else if (s.equals("-nodbg"))
                        {
                        opts.includeDbg = false;
                        }
                    else if (s.equals("-nodoc"))
                        {
                        opts.includeDoc = false;
                        }
                    else if (s.equals("-strict"))
                        {
                        opts.strictLevel = Options.Strictness.Stickler;
                        }
                    else if (s.equals("-nowarn"))
                        {
                        opts.strictLevel = Options.Strictness.Suppressed;
                        }
                    else if (s.equals("-verbose"))
                        {
                        opts.verbose = true;
                        }
                    else if (s.startsWith("-"))
                        {
                        deferred.add("xtc: unknown option: " + s);
                        }
                    else
                        {
                        List<File> files;
                        try
                            {
                            files = resolvePath(s);
                            }
                        catch (IOException e)
                            {
                            deferred.add("xtc: Exception resolving path \"" + s + "\": " + e);
                            continue;
                            }

                        for (File file : files)
                            {
                            // this is expected to be the name of a file to compile
                            if (!file.exists())
                                {
                                deferred.add("xtc: no such file to compile: \"" + file + "\"");
                                error = true;
                                }
                            else if (!file.canRead())
                                {
                                deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                        + " not readable: \"" + file + "\"");
                                error = true;
                                }
                            else
                                {
                                sources.add(file);
                                }
                            }
                        }
                    }
                }
            }
        }

    /**
     * If the options suggest the display of the parsed compiler options, then display them.
     */
    protected void showCompilerOptions()
        {
        if (opts.verbose)
            {
            out("xtc: Compiler options:");
            out(indentLines(opts.toString(), "   : "));
            out();

            if (!sources.isEmpty())
                {
                out("xtc: Source files:");
                int i = 0;
                for (File file : sources)
                    {
                    out("   : [" + i++ + "]=" + file);
                    }
                out();
                }
            }
        }

    /**
     * Select the modules to compile.
     */
    protected void selectCompileTargets()
        {
        if (sources.isEmpty())
            {
            sources.add(new File("module.x"));
            }
        for (File file : sources)
            {
            File moduleFile = findModule(file);
            if (moduleFile != null && !modules.containsKey(moduleFile))
                {
                modules.put(moduleFile, buildTree(moduleFile));
                }
            }
        if (modules.isEmpty())
            {
            deferred.add("xtc: No module found to compile.");
            error = true;
            }
        else if (modules.size() > 1 && opts.destination != null && !opts.destination.isDirectory())
            {
            // -D option can't be just a single file if there is more than one module to compile
            deferred.add("xtc: Multiple modules found to compile, but output destination is a single file.");
            error = true;
            }
        }

    /**
     * Find the module to compile.
     *
     * @param file  the file or directory to examine
     *
     * @return the file containing the module, or null
     */
    protected File findModule(File file)
        {
        if (file.isFile())
            {
            // just in case the file is relative to some working
            // directory, resolve its location
            file = file.getAbsoluteFile();

            if (isModule(file))
                {
                return file;
                }

            file = file.getParentFile();
            }

        // we're going to have to walk up the directory tree, so
        // the entire path needs to be resolved
        file = file.getAbsoluteFile();

        while (file != null && file.isDirectory())
            {
            File moduleFile = new File(file, "module.x");
            if (moduleFile.exists() && moduleFile.isFile())
                {
                if (isModule(moduleFile))
                    {
                    return moduleFile;
                    }
                }

            file = file.getParentFile();
            }

        return null;
        }

    /**
     * Check if the specified file contains a module.
     *
     * @param file  the file (NOT a directory) to examine
     *
     * @return true iff the file declares a module
     */
    protected boolean isModule(File file)
        {
        assert file.isFile() && file.canRead();
        if (opts.verbose)
            {
            out("xtc: Parsing file: " + file);
            }

        Statement stmt = null;
        try
            {
            Source    source  = new Source(file);
            ErrorList errlist = new ErrorList(100);
            Parser    parser  = new Parser(source, errlist);
            stmt = parser.parseSource();
            }
        catch (CompilerException e)
            {
            deferred.add("xtc: An exception occurred parsing \"" + file + "\": " + e);
            }
        catch (IOException e)
            {
            deferred.add("xtc: An exception occurred reading \"" + file + "\": " + e);
            }

        if (stmt != null)
            {
            if (stmt instanceof BlockStatement)
                {
                List<Statement> list = ((BlockStatement) stmt).statements;
                stmt = list.get(list.size() - 1);
                }
            if (stmt instanceof TypeDeclarationStatement)
                {
                TypeDeclarationStatement typeStmt = (TypeDeclarationStatement) stmt;
                Token                    category = typeStmt.category;
                if (opts.verbose)
                    {
                    out("xtc: Contains " + category.getId().TEXT + " " + typeStmt.getName());
                    out();
                    }
                return category.getId() == Token.Id.MODULE;
                }

            deferred.add("xtc: File \"" + file + "\" did not contain a type definition.");
            }

        return false;
        }

    /**
     * Build a tree of files that need to be compiled in order to compile
     * a module.
     *
     * @param file  a module file, or a directory that is part of a module
     *
     * @return a node iff there is anything "there" to compile, otherwise null
     */
    protected Node buildTree(File file)
        {
        DirNode node = new DirNode();
        if (file.isDirectory())
            {
            // we're parsing a sub-directory looking for source files
            // (and sub-directories)
            node.fileDir = file;
            File filePkg = new File(file, "package.x");
            if (filePkg.exists() && filePkg.isFile())
                {
                node.filePkg = filePkg;
                node.pkgNode = new FileNode(filePkg);
                }
            }
        else if (file.getName().equalsIgnoreCase("module.x"))
            {
            // this is the module root
            node.filePkg = file;
            node.fileDir = file.getParentFile();
            node.pkgNode = new FileNode(file);
            }
        else
            {
            // this is the entire module
            return new FileNode(file);
            }

        NextChild: for (File child : node.fileDir.listFiles())
            {
            if (child.isDirectory())
                {
                DirNode nodeChild = (DirNode) buildTree(child);
                if (nodeChild != null)
                    {
                    nodeChild.parent = node;
                    node.packages.add(nodeChild);
                    }
                }
            else
                {
                if (node.filePkg != null && child.equals(node.filePkg))
                    {
                    // this is the module.x or package.x file
                    continue;
                    }

                String sChild = child.getName();
                if (!sChild.endsWith(".x"))
                    {
                    continue;
                    }

                if (sChild.equalsIgnoreCase("module.x") || sChild.equalsIgnoreCase("package.x"))
                    {
                    deferred.add("xtc: Illegal file encountered: " + child);
                    switch (opts.strictLevel)
                        {
                        case Normal:
                        case Stickler:
                            error = true;
                        }
                    continue;
                    }

                // it's a source file
                FileNode cmp = new FileNode(child);
                node.sources.put(child, cmp);
                }
            }

        return node.filePkg == null && node.sources.isEmpty() && node.packages.isEmpty() ? null : node;
        }

    /**
     * Parse all of the source code that needs to be compiled.
     */
    protected void parseSource()
        {
        for (Node module : modules.values())
            {
            module.parse();
            module.checkErrors();
            }
        }

    /**
     * Parse all of the source code that needs to be compiled.
     */
    protected void arrangeSourceByName()
        {
        for (Node module : modules.values())
            {
            module.registerNames();
            String name = module.name();
            if (modulesByName.containsKey(name))
                {
                deferred.add("xtc: Duplicate module: \"" + name + "\"");
                error = true;
                continue;
                }

            modulesByName.put(name, module);

            // create a module/package/class structure for each dir/file node in the "module tree"
            FileStructure struct = new FileStructure(module.name(), null, null);
            module.assignStructure((ModuleStructure) struct.getTopmostStructure());
            }
        }

    protected void checkSyntax()
        {
        for (Node module : modules.values())
            {
            module.checkSyntax();
            module.checkErrors();
            }
        }

    protected void resolveDependencies()
        {
        for (Node module : modules.values())
            {
            module.resolveDependencies();
            module.checkErrors();
            }
        }

    protected void produceModules()
        {
        for (Node module : modules.values())
            {
            // figure out where to put the resulting module
            File file = opts.destination;
            if (file == null)
                {
                file = module.getFile();
                if (file.isFile())
                    {
                    file = file.getParentFile();
                    }
                }

            // at this point, we either have a directory or a file to put it in; resolve that to
            // an actual compiled module file name
            if (file.isDirectory())
                {
                String sName = module.name();
                int    ofDot = sName.indexOf('.');
                if (ofDot > 0)
                    {
                    sName = sName.substring(0, ofDot);
                    }
                file = new File(file, sName + ".xtc");
                }

            FileStructure struct = module.getStructure().getFileStructure();

            // TODO - build the module

            try
                {
                struct.writeTo(file);
                }
            catch (IOException e)
                {
                deferred.add("xtc: Exception (" + e
                        + ") occurred while attempting to write module file \""
                        + file.getAbsolutePath() + "\"");
                error = true;
                }
            module.checkErrors();
            }
        }

    /**
     * see where we're at
     */
    public void dump()
        {
        out();
        if (modulesByName.isEmpty())
            {
            out("xtc: dump modules:");
            for (Map.Entry<File, Node> entry : modules.entrySet())
                {
                out(entry.getValue());
                }
            }
        else
            {
            out("xtc: dump modules by name:");
            for (Map.Entry<String, Node> entry : modulesByName.entrySet())
                {
                Node node = entry.getValue();
                out(node);

                TypeDeclarationStatement type = node.getType();
                if (type != null)
                    {
                    out();
                    out(type);
                    }

                StructureContainer struct = node.getStructure();
                if (struct != null)
                    {
                    out();
                    out(struct.getFileStructure());
                    }
                }
            }
        }

    /**
     * Print any accrued errors (subject to the options specified), and exit the command line
     * process if necessary.
     */
    protected void checkTerminalFailure()
        {
        // show accumulated errors/warnings
        if (error || (!deferred.isEmpty() && (opts.verbose
                || opts.strictLevel == Options.Strictness.Normal
                || opts.strictLevel == Options.Strictness.Stickler)))
            {
            for (String s : deferred)
                {
                out(s);
                }
            }

        // determine if the errors are bad enough to quit
        if (error || (!deferred.isEmpty() && opts.strictLevel == Options.Strictness.Stickler))
            {
            out("xtc: Terminating.");
            System.exit(1);
            throw new IllegalStateException();
            }

        // reset error conditions
        error = false;
        deferred.clear();
        }


    // ----- inner class: Progress -----------------------------------------------------------------

    enum Progress {INIT, PARSED, NAMED, SYNTAX_CHECKED, RESOLVED, COMPILED}


    // ----- inner class: Node ---------------------------------------------------------------------

    /**
     * Shared interface for module/package and class nodes.
     */
    interface Node
        {
        File getFile();
        void parse();
        public void registerNames();
        String name();
        String descriptiveName();
        TypeDeclarationStatement getType();
        void assignStructure(StructureContainer struct);
        StructureContainer getStructure();
        void checkSyntax();
        void resolveDependencies();
        void checkErrors();
        }


    // ----- inner class: DirNode ------------------------------------------------------------------

    /**
     * Represents a module or package to compile.
     */
    public class DirNode
            implements Node
        {
        public Progress                 progress    = Progress.INIT;
        public DirNode                  parent;
        public File                     fileDir;
        public File                     filePkg;
        public FileNode                 pkgNode;
        public ListMap<File, FileNode>  sources     = new ListMap<>();
        public List<DirNode>            packages    = new ArrayList<>();
        public Map<String, Node>        children    = new HashMap<>();

        @Override
        public File getFile()
            {
            return fileDir;
            }

        /**
         * Parse this node and all nodes it contains.
         */
        @Override
        public void parse()
            {
            if (progress == Progress.INIT)
                {
                if (pkgNode == null)
                    {
                    // provide a default implementation
                    assert parent != null;
                    pkgNode = new FileNode("package " + fileDir.getName() + "{}");
                    }
                pkgNode.parse();

                for (FileNode cmpFile : sources.values())
                    {
                    cmpFile.parse();
                    }

                for (DirNode child : packages)
                    {
                    child.parse();
                    }

                progress = Progress.PARSED;
                }
            }

        /**
         * Go through all the packages and types in this package and register their names.
         */
        @Override
        public void registerNames()
            {
            assert progress.ordinal() >= Progress.PARSED.ordinal();

            if (progress == Progress.PARSED)
                {
                if (pkgNode != null)
                    {
                    pkgNode.registerNames();
                    }

                for (FileNode clz : sources.values())
                    {
                    clz.registerNames();
                    registerName(clz.name(), clz);
                    }

                for (DirNode pkg : packages)
                    {
                    pkg.registerNames();
                    registerName(pkg.name(), pkg);
                    }

                progress = Progress.NAMED;
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
                if (children.containsKey(name))
                    {
                    deferred.add("xtc: Duplicate name \"" + name + "\" detected in " + descriptiveName());
                    error = true;
                    }
                else
                    {
                    children.put(name, node);
                    }
                }
            }

        /**
         * @return the simple package name, or if this is a module, the fully qualified module name
         */
        @Override
        public String name()
            {
            assert progress.ordinal() >= Progress.NAMED.ordinal();

            String sName = null;
            if (pkgNode != null)
                {
                sName = pkgNode.name();
                }
            if (sName == null && parent != null)
                {
                sName = fileDir.getName();
                }
            return sName;
            }

        @Override
        public String descriptiveName()
            {
            if (parent == null)
                {
                return "module " + name();
                }

            StringBuilder sb = new StringBuilder();
            sb.append("package ")
              .append(name());

            DirNode node = parent;
            while (node.parent != null)
                {
                sb.insert(8, node.name() + '.');
                node = node.parent;
                }

            sb.append(node);
            return sb.toString();
            }

        @Override
        public TypeDeclarationStatement getType()
            {
            return pkgNode == null ? null : pkgNode.getType();
            }

        @Override
        public void assignStructure(StructureContainer struct)
            {
            if (pkgNode == null)
                {
                deferred.add("xtc: no package node for " + descriptiveName());
                error = true;
                }
            else
                {
                StructureContainer.PackageContainer thisPkg = (StructureContainer.PackageContainer) struct;

                pkgNode.assignStructure(struct);

                for (FileNode clzNode : sources.values())
                    {
                    clzNode.assignStructure(thisPkg.ensureClass(clzNode.name()));
                    }

                for (DirNode nestedPkgNode : packages)
                    {
                    // create and assign the package structure
                    nestedPkgNode.assignStructure(thisPkg.ensurePackage(nestedPkgNode.name()));
                    }
                }
            }

        @Override
        public StructureContainer getStructure()
            {
            return pkgNode == null ? null : pkgNode.getStructure();
            }

        @Override
        public void checkSyntax()
            {
            if (pkgNode != null)
                {
                pkgNode.checkSyntax();
                }

            for (FileNode cmpFile : sources.values())
                {
                cmpFile.checkSyntax();
                }

            for (DirNode child : packages)
                {
                child.checkSyntax();
                }
            }

        @Override
        public void resolveDependencies()
            {
            if (pkgNode != null)
                {
                pkgNode.resolveDependencies();
                }

            for (FileNode cmpFile : sources.values())
                {
                cmpFile.resolveDependencies();
                }

            for (DirNode child : packages)
                {
                child.resolveDependencies();
                }
            }

        @Override
        public void checkErrors()
            {
            if (pkgNode != null)
                {
                pkgNode.checkErrors();
                }

            for (FileNode cmpFile : sources.values())
                {
                cmpFile.checkErrors();
                }

            for (DirNode child : packages)
                {
                child.checkErrors();
                }
            }

        @Override
        public String toString()
            {
            boolean fUseNames = progress.ordinal() >= Progress.NAMED.ordinal();

            StringBuilder sb = new StringBuilder();
            sb.append(fUseNames ? name() : fileDir.getName())
              .append(": ")
              .append(filePkg == null ? "(no package.x)" : filePkg.getName());

            for (Map.Entry<File, FileNode> entry : sources.entrySet())
                {
                File     file = entry.getKey();
                FileNode node = entry.getValue();

                sb.append("\n |- ")
                  .append(node == null ? file.getName() : node);
                }

            for (int i = 0, c = packages.size(); i < c; ++i)
                {
                DirNode pkg = packages.get(i);
                sb.append("\n |- ")
                  .append(indentLines(pkg.toString(), (i == c - 1 ? "    " : " |  ")).substring(4));
                }

            return sb.toString();
            }
        }


    // ----- inner class: FileNode -----------------------------------------------------------------

    /**
     * Represents a file to compile.
     */
    public class FileNode
            implements Node
        {
        public Progress  progress = Progress.INIT;
        public File      file;
        public Source    source;
        public ErrorList errs = new ErrorList(100);
        public Statement stmt;
        public TypeDeclarationStatement type;

        /**
         * Construct a FileNode.
         *
         * @param file  the source file containing the code
         */
        public FileNode(File file)
            {
            this.file   = file;
            try
                {
                source = new Source(file);
                }
            catch (IOException e)
                {
                deferred.add("xtc: Failure reading: " + file);
                error = true;
                }
            }

        @Override
        public File getFile()
            {
            return file;
            }

        /**
         * Construct a FileNode from the code that would have been in a file.
         *
         * @param code  the source code
         */
        public FileNode(String code)
            {
            this.source = new Source(code);
            }

        /**
         * Try to parse the source file if it hasn't already been parsed.
         *
         * @return the top level statement
         */
        @Override
        public void parse()
            {
            if (progress == Progress.INIT)
                {
                try
                    {
                    stmt = new Parser(source, errs).parseSource();
                    }
                catch (CompilerException e)
                    {
                    if (errs.getSeriousErrorCount() == 0)
                        {
                        errs.log(Severity.FATAL, Parser.FATAL_ERROR, null,
                                source, source.getPosition(), source.getPosition());
                        }
                    }
                progress = Progress.PARSED;
                }
            }

        /**
         * Go through all the packages and types in this package and register their names.
         */
        @Override
        public void registerNames()
            {
            assert progress.ordinal() >= Progress.PARSED.ordinal();

            if (progress == Progress.PARSED)
                {
                // this can only happen if the errors were ignored
                if (stmt != null)
                    {
                    if (stmt instanceof TypeDeclarationStatement)
                        {
                        type = (TypeDeclarationStatement) stmt;
                        }
                    else
                        {
                        List<Statement> list = ((BlockStatement) stmt).statements;
                        type = (TypeDeclarationStatement) list.get(list.size() - 1);
                        }
                    }

                progress = Progress.NAMED;
                }
            }

        /**
         * @return the simple name
         */
        @Override
        public String name()
            {
            return type == null ? file.getName() : (String) type.getName();
            }

        @Override
        public String descriptiveName()
            {
            return type == null ? file.getAbsolutePath() : (type.category.getId().TEXT + ' ' + name());
            }

        @Override
        public TypeDeclarationStatement getType()
            {
            return type;
            }

        @Override
        public void assignStructure(StructureContainer struct)
            {
            type.setStructure(struct);
            }

        @Override
        public StructureContainer getStructure()
            {
            return type.getStructure();
            }

        @Override
        public void checkSyntax()
            {
            // TODO
            }

        @Override
        public void resolveDependencies()
            {
            // TODO
            }

        @Override
        public void checkErrors()
            {
            if (!errs.getErrors().isEmpty() && errs.getSeverity().ordinal() >= opts.badEnoughToPrint().ordinal())
                {
                out("xtc: Errors in " + descriptiveName());
                int i = 0;
                for (ErrorList.ErrorInfo err : errs.getErrors())
                    {
                    out(" [" + (i++) + "] " + err);
                    }

                error |= errs.getSeverity().ordinal() >= opts.badEnoughToQuit().ordinal();
                errs.clear();
                }
            }

        @Override
        public String toString()
            {
            boolean fUseNames = progress.ordinal() >= Progress.NAMED.ordinal();

            StringBuilder sb = new StringBuilder();
            sb.append(fUseNames ? name() : file.getName());
            boolean fAppend = false;

            if (progress.ordinal() >= Progress.PARSED.ordinal() && stmt == null)
                {
                sb.append(" (not parsed");
                fAppend = true;
                }

            if (errs.getSeverity() != Severity.NONE)
                {
                sb.append(fAppend ? "; " : " (")
                  .append(errs.getErrors().size())
                  .append(" items logged, severity=")
                  .append(errs.getSeverity().name());

                if (errs.getSeriousErrorCount() > 0)
                    {
                    sb.append(", ")
                      .append(errs.getSeriousErrorCount())
                      .append(" serious");
                    }
                fAppend = true;
                }

            if (fAppend)
                {
                sb.append(')');
                }

            return sb.toString();
            }
        }


    // ----- inner class: Options ------------------------------------------------------------------

    /**
     * Represents the command-line options.
     */
    public static class Options
        {
        File    destination = null;
        boolean verbose     = false;
        boolean includeSrc  = true;
        boolean includeDbg  = true;
        boolean includeDoc  = true;

        enum Strictness {None, Suppressed, Normal, Stickler};
        Strictness strictLevel = Strictness.Normal;

        List<File> modulePath = new ArrayList<>();
        Map<String, String> customCfg = new ListMap<>();

        Severity badEnoughToPrint()
            {
            if (verbose)
                {
                return Severity.INFO;
                }
            switch (strictLevel)
                {
                case None:
                case Suppressed:
                    return Severity.ERROR;

                default:
                case Normal:
                case Stickler:
                    return Severity.WARNING;
                }
            }

        Severity badEnoughToQuit()
            {
            return strictLevel == Strictness.Stickler ? Severity.WARNING : Severity.ERROR;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append("destination=")
              .append(destination == null ? "(default)" : destination)
              .append("\nverbose=")
              .append(verbose)
              .append("\nincludeSrc=")
              .append(includeSrc)
              .append("\nincludeDbg=")
              .append(includeDbg)
              .append("\nincludeDoc=")
              .append(includeDoc)
              .append("\nstrictLevel=")
              .append(strictLevel.name())
              .append("\nmodulePath=");

            if (modulePath.isEmpty())
                {
                sb.append("(none)");
                }
            else
                {
                int i = 0;
                for (File file : modulePath)
                    {
                    sb.append("\n[")
                      .append(i++)
                      .append("]=")
                      .append(file);

                    if (!file.exists())
                        {
                        sb.append(" (does not exist)");
                        }
                    else if (!file.canRead())
                        {
                        sb.append(" (no read permissions)");
                        }
                    else if (file.isDirectory())
                        {
                        sb.append(" (dir)");
                        }
                    }
                }

            if (!customCfg.isEmpty())
                {
                sb.append("\ncustomCfg=");
                for (Map.Entry<String, String> entry : customCfg.entrySet())
                    {
                    sb.append("\n[")
                      .append(entry.getKey())
                      .append("]=")
                      .append(entry.getValue());
                    }
                }
            return sb.toString();
            }
        }


    // ----- static helpers ------------------------------------------------------------------------

    /**
     * Print a blank line to the terminal.
     */
    public static final void out()
        {
        out("");
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public static final void out(Object o)
        {
        System.out.println(o);
        }

    /**
     * Resolve the specified "path string" into a list of files.
     *
     * @param sPath   the path to resolve, which may be a file or directory name, and may include
     *                wildcards, etc.
     *
     * @return a list of File objects
     *
     * @throws IOException
     */
    public static List<File> resolvePath(String sPath)
            throws IOException
        {
        List<File> files = new ArrayList<>();

        if (sPath.startsWith("~" + File.separator))
            {
            sPath = System.getProperty("user.home") + sPath.substring(1);
            }

        if (sPath.indexOf('*') >= 0 || sPath.indexOf('?') >= 0)
            {
            // wildcard file names
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), sPath))
                {
                stream.forEach(path -> files.add(path.toFile()));
                }
            }
        else
            {
            files.add(new File(sPath));
            }

        return files;
        }
    }
