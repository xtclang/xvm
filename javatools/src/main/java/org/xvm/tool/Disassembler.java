package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * The "disassemble" command:
 *
 *  java org.xvm.tool.Disassembler xtc_path
 */
public class Disassembler
        extends Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        new Disassembler(asArg).run();
        }

    /**
     * Disassembler constructor.
     *
     * @param asArg command line arguments
     */
    public Disassembler(String[] asArg)
        {
        this(asArg, null);
        }

    /**
     * Disassembler constructor.
     *
     * @param asArg    command line arguments
     * @param console  representation of the terminal within which this command is run
     */
    public Disassembler (String[] asArg, Console console)
        {
        super(asArg, console);
        }

    @Override
    protected void process()
        {
        File      fileModule = options().getTarget();
        String    sModule    = fileModule.getName();
        Component component  = null;
        if (sModule.endsWith(".xtc"))
            {
            // it's a file
            log(Severity.INFO, "Loading module file: " + sModule);
            try
                {
                try (FileInputStream in = new FileInputStream(fileModule))
                    {
                    component = new FileStructure(in);
                    }
                }
            catch (IOException e)
                {
                log(Severity.ERROR, "I/O exception (" + e + ") reading module file: " + fileModule);
                }
            }
        else
            {
            // it's a module; set up the repository
            log(Severity.INFO, "Creating and pre-populating library and build repositories");
            ModuleRepository repo = configureLibraryRepo(options().getModulePath());
            checkErrors();

            log(Severity.INFO, "Loading module: " + sModule);
            component = repo.loadModule(sModule);
            }

        if (component == null)
            {
            log(Severity.ERROR, "Unable to load module: " + fileModule);
            }
        checkErrors();

        if (options().specified("files"))
            {
            dumpFiles(component);
            }
        else
            {
            component.visitChildren(this::dump, false, true);
            }
        ConstantPool pool = component.getConstantPool();
        }

    public void dump(Component component)
        {
        if (component instanceof MethodStructure method)
            {
            MethodConstant id = method.getIdentityConstant();

            if (method.hasCode() && method.ensureCode() != null && !method.isNative())
                {
                out("** code for " + id);
                out(method.ensureCode().toString());
                out("");
                }
            else
                {
                out("** no code for " + id);
                out("");
                }
            }
        }

    private static final byte FREE    = 0;
    private static final byte DIR     = 1;
    private static final byte FILE    = 2;
    private static final byte EXISTS  = DIR | FILE;
    private static final byte CLAIMED = 4;
    private static final LocalDateTime SOON   = LocalDateTime.now().plusDays(1);
    private static final LocalDateTime RECENT = LocalDateTime.now().minusMonths(6);

    public void dumpFiles(Component component)
        {
        Constant[] aconst  = component.getConstantPool().getConstants();
        int        cConsts = aconst.length;
        byte[]     aflags  = new byte[cConsts];
        int        maxSize = 0;
        int        maxId   = 0;
        int        cDirs   = 0;
        int        cFiles  = 0;

        // first, identify all filing system nodes
        for (int i = 0; i < cConsts; ++i)
            {
            Constant constant = aconst[i];
            if (constant instanceof FSNodeConstant fsNode)
                {
                if (i > maxId)
                    {
                    maxId = i;
                    }

                switch (constant.getFormat())
                    {
                    case FSDir:
                        aflags[i] |= DIR;
                        for (FSNodeConstant fsChildNode : fsNode.getDirectoryContents())
                            {
                            claimNode(fsChildNode, aflags);
                            }
                        ++cDirs;
                        break;

                    case FSFile:
                        aflags[i] |= FILE;
                        int size = fsNode.getFileBytes().length;
                        if (size > maxSize)
                            {
                            maxSize = size;
                            }
                        ++cFiles;
                        break;

                    case FSLink:
                        aflags[i] |= FILE;
                        claimNode(fsNode.getLinkTarget(), aflags);
                        ++cFiles;
                        break;
                    }
                ++cFiles;
                }
            }

        if (cDirs > 0 || cFiles > 0)
            {
            // print all unclaimed filing system nodes (which recursively will print the claimed nodes)
            out("Module contains " + cDirs + " directories and " + cFiles + " files:");
            boolean[] visited = new boolean[cConsts];
            for (int i = 0; i < cConsts; ++i)
                {
                if ((aflags[i] & EXISTS) != 0 && (aflags[i] & CLAIMED) == 0)
                    {
                    FSNodeConstant fsNode = (FSNodeConstant) aconst[i];
                    printNode(fsNode, "", false, false, visited,
                              String.valueOf(maxId).length(), String.valueOf(maxSize).length());
                    }
                }
            }
        else
            {
            out("Module contains no files.");
            }
        }

    private void claimNode(FSNodeConstant fsNode, byte[] aflags)
        {
        int i = fsNode.getPosition();
        if ((aflags[i] & CLAIMED) == 0)
            {
            aflags[i] |= CLAIMED;
            switch (fsNode.getFormat())
                {
                case FSDir:
                    for (FSNodeConstant fsChildNode : fsNode.getDirectoryContents())
                        {
                        claimNode(fsChildNode, aflags);
                        }
                    break;

                case FSLink:
                    claimNode(fsNode.getLinkTarget(), aflags);
                    break;
                }
            }
        }

    private void printNode(FSNodeConstant fsNode,
                           String         indent,
                           boolean        last,
                           boolean        linked,
                           boolean[]      visited,
                           int            idLen,
                           int            sizeLen)
        {
        StringBuilder buf = new StringBuilder();

        // constant id
        int    id     = fsNode.getPosition();
        String idText = String.valueOf(id);
        buf.append('[');
        for (int i = 0, c = Math.max(0, idLen - idText.length()); i < c; ++i)
            {
            buf.append(' ');
            }
        buf.append(idText)
           .append(']');

        // permissions
        boolean fDir = fsNode.getFormat() == Format.FSDir;
        buf.append(fDir ? " dr-- " : " -r-- ");

        // size
        String sizeText = fsNode.getFormat() == Format.FSFile
                ? String.valueOf(fsNode.getFileBytes().length)
                : "";
        for (int i = 0, c = Math.max(0, sizeLen - sizeText.length()); i < c; ++i)
            {
            buf.append(' ');
            }
        buf.append(sizeText);

        // date/time
        buf.append(' ');
        LocalDateTime time = null;
        try
            {
            time = OffsetDateTime.parse(fsNode.getModified()).toLocalDateTime();
            }
        catch (Exception ignore) {}
        if (time == null)
            {
            buf.append("??? ??  ????");
            }
        else
            {
            buf.append(time.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
               .append(' ');
            int day = time.getDayOfMonth();
            if (day < 10)
                {
                buf.append(' ');
                }
            buf.append(day);
            buf.append(' ');
            // within the last 6 months, we show time, otherwise year
            if (time.isAfter(RECENT) && time.isBefore(SOON))
                {
                int hour = time.getHour();
                if (hour < 10)
                    {
                    buf.append(0);
                    }
                buf.append(hour)
                   .append(':');
                int minute = time.getMinute();
                if (minute < 10)
                    {
                    buf.append(0);
                    }
                buf.append(minute);
                }
            else
                {
                buf.append(' ');
                // four digit year
                String s = String.valueOf(10000+time.getYear());
                buf.append(s.substring(s.length()-4));
                }
            }

        // name
        buf.append(' ');
        if (linked)
            {
            if (indent.length() > 0)
                {
                buf.append(indent.substring(0, indent.length()-4))
                   .append(" |  ");
                }
            buf.append("-> ");
            }
        else
            {
            buf.append(indent);
            }
        String name = fsNode.getName();
        if (name.length() == 0)
            {
            buf.append('/');
            }
        else
            {
            Handy.appendString(buf, name);
            }

        boolean alreadyVisited = visited[id];
        visited[id] = true;
        switch (fsNode.getFormat())
            {
            case FSDir:
                if (alreadyVisited)
                    {
                    buf.append(" (see above)");
                    out(buf);
                    }
                else
                    {
                    out(buf);
                    FSNodeConstant[] aKidNodes  = fsNode.getDirectoryContents();
                    int              cKidNodes  = aKidNodes.length;
                    String           nextIndent = " |- ";
                    if (indent.length() > 0)
                        {
                        nextIndent = indent.substring(0, indent.length()-4)
                                   + (last ? "    " : " |  ")
                                   + nextIndent;
                        }
                    for (int i = 0; i < cKidNodes; ++i)
                        {
                        printNode(aKidNodes[i], nextIndent, i == cKidNodes-1, false, visited, idLen, sizeLen);
                        }
                    }
                break;

            case FSLink:
                out(buf);
                printNode(fsNode.getLinkTarget(), indent, last, true, visited, idLen, sizeLen);
                break;

            default:
                out(buf);
                break;
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy disassembler:

            Examines a compiled Ecstasy module.
            
            Note: The xam command will be removed, and replaced with an option on the xtc command.

            Usage:

                xam <options> <modulename>
            or:
                xam <options> <filename>.xtc
            """;
        }


    // ----- options -------------------------------------------------------------------------------

    @Override
    public Options options()
        {
        return (Options) super.options();
        }

    @Override
    protected Options instantiateOptions()
        {
        return new Options();
        }

    /**
     * Disassembler command-line options implementation.
     */
    public class Options
        extends Launcher.Options
        {
        /**
         * Construct the Disassembler Options.
         */
        public Options()
            {
            super();

            addOption("L",      Form.Repo, true , "Module path; a \"" + File.pathSeparator
                                                  + "\"-delimited list of file and/or directory names");
            addOption("files",  Form.Name, false, "List all files embedded in the module");
            addOption(Trailing, Form.File, false, "Module file name (.xtc) to disassemble");
            }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        public List<File> getModulePath()
            {
            return (List<File>) values().getOrDefault("L", Collections.emptyList());
            }

        /**
         * @return the file to execute
         */
        public File getTarget()
            {
            return (File) values().get(Trailing);
            }

        @Override
        public void validate()
            {
            super.validate();

            // validate the trailing file (to execute)
            File fileModule = getTarget();
            if (fileModule == null || fileModule.length() == 0)
                {
                log(Severity.ERROR, "Module file required");
                }
            else if (fileModule.getName().endsWith(".xtc"))
                {
                if (!fileModule.exists())
                    {
                    log(Severity.ERROR, "Specified module file does not exist");
                    }
                else if (!fileModule.isFile())
                    {
                    log(Severity.ERROR, "Specified module file is not a file");
                    }
                else if (!fileModule.canRead())
                    {
                    log(Severity.ERROR, "Specified module file cannot be read");
                    }
                }
            }
        }
    }

