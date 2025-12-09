package org.xvm.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import java.time.format.TextStyle;

import java.util.Arrays;
import java.util.Locale;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

import org.xvm.tool.LauncherOptions.DisassemblerOptions;

import org.xvm.util.Handy;

import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;
import static org.xvm.compiler.ast.FileExpression.createdTime;
import static org.xvm.compiler.ast.FileExpression.modifiedTime;
import static org.xvm.util.Handy.readFileBytes;
import static org.xvm.util.Handy.readFileChars;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.INFO;

/**
 * The "disassemble" command:
 * <p>
 *  java org.xvm.tool.Disassembler xtc_path
 *
 */
public class Disassembler extends Launcher<DisassemblerOptions> {

    protected static final String COMMAND_NAME = "xac";

    @SuppressWarnings("unused")
    private static final byte FREE    = 0;
    private static final byte DIR     = 1;
    private static final byte FILE    = 2;
    private static final byte EXISTS  = DIR | FILE;
    private static final byte CLAIMED = 4;

    private static final LocalDateTime NOW    = LocalDateTime.now();
    private static final LocalDateTime SOON   = NOW.plusDays(1);
    private static final LocalDateTime RECENT = NOW.minusMonths(6);

    /**
     * Disassembler constructor for programmatic use.
     *
     * @param options     pre-configured disassembler options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Disassembler(final DisassemblerOptions options, final Console console, final ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        Launcher.main(insertCommand(COMMAND_NAME, args));
    }

    @Override
    protected void validateOptions() {
        final var opts = options();
        final var fileModule = opts.getTarget();
        if (fileModule == null) {
            log(ERROR, "No module file specified");
            return;
        }
        if (!fileModule.exists()) {
            log(ERROR, "Module file does not exist: {}", fileModule);
        }
        // Validate the -L path of file(s)/dir(s)
        validateModulePath(opts.getModulePath());
    }

    @Override
    protected int process() {
        final var opts       = options();
        final var fileModule = opts.getTarget();
        final var sModule    = fileModule.getName();

        Component component  = null;

        if (sModule.endsWith(".xtc")) {
            // it's a file
            log(INFO, "Loading module file: {}", sModule);
            try (var in = new FileInputStream(fileModule)) {
                component = new FileStructure(in);
            } catch (final IOException e) {
                log(ERROR, "I/O exception ({}) reading module file: {}", e, fileModule);
            }
        } else {
            // it's a module; set up the repository
            log(INFO, "Creating and pre-populating library and build repositories");
            final var repo = configureLibraryRepo(opts.getModulePath());
            checkErrors();

            log(INFO, "Loading module: {}", sModule);
            component = repo.loadModule(sModule);
        }

        if (component == null) {
            log(ERROR, "Unable to load module: {}", fileModule);
        }
        checkErrors();

        if (component != null) {
            var findFile = opts.getFindFile();
            if (opts.isListFiles()) {
                dumpFiles(component);
            } else if (findFile != null) {
                findFile(component, findFile);
            } else {
                component.visitChildren(this::dump, false, true);
            }
            component.getConstantPool();
        }
        return hasSeriousErrors() ? 1 : 0;
    }

    public void dump(final Component component) {
        if (component instanceof final MethodStructure method) {
            final var id = method.getIdentityConstant();
            if (method.hasCode() && method.ensureCode() != null && !method.isNative()) {
                out("** code for " + id);
                out(method.ensureCode().toString());
                out("");
            } else {
                out("** no code for " + id);
                out("");
            }
        }
    }

    public void dumpFiles(final Component component) {
        Constant[] aconst  = component.getConstantPool().getConstants();
        int        cConsts = aconst.length;
        byte[]     aflags  = new byte[cConsts];
        int        maxSize = 0;
        int        maxId   = 0;
        int        cDirs   = 0;
        int        cFiles  = 0;

        // first, identify all filing system nodes
        for (int i = 0; i < cConsts; ++i) {
            var constant = aconst[i];
            if (constant instanceof final FSNodeConstant fsNode) {
                if (i > maxId) {
                    maxId = i;
                }

                switch (constant.getFormat()) {
                case FSDir:
                    aflags[i] |= DIR;
                    for (final var fsChildNode : fsNode.getDirectoryContents()) {
                        claimNode(fsChildNode, aflags);
                    }
                    ++cDirs;
                    break;

                case FSFile:
                    aflags[i] |= FILE;
                    int size = fsNode.getFileBytes().length;
                    if (size > maxSize) {
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

        if (cDirs > 0 || cFiles > 0) {
            // print all unclaimed filing system nodes (which recursively will print the claimed nodes)
            out("Module contains " + cDirs + " directories and " + cFiles + " files:");
            boolean[] visited = new boolean[cConsts];
            for (int i = 0; i < cConsts; ++i) {
                if ((aflags[i] & EXISTS) != 0 && (aflags[i] & CLAIMED) == 0) {
                    var fsNode = (FSNodeConstant) aconst[i];
                    printNode(fsNode, "", false, false, visited,
                            String.valueOf(maxId).length(), String.valueOf(maxSize).length());
                }
            }
            return;
        }

        out("Module contains no files.");
    }

    private static void claimNode(final FSNodeConstant fsNode, final byte[] aflags) {
        int i = fsNode.getPosition();
        if ((aflags[i] & CLAIMED) == 0) {
            aflags[i] |= CLAIMED;
            switch (fsNode.getFormat()) {
            case FSDir:
                for (final var fsChildNode : fsNode.getDirectoryContents()) {
                    claimNode(fsChildNode, aflags);
                }
                break;

            case FSLink:
                claimNode(fsNode.getLinkTarget(), aflags);
                break;
            }
        }
    }

    private void printNode(final FSNodeConstant fsNode,
                           final String         indent,
                           final boolean        last,
                           final boolean        linked,
                           final boolean[]      visited,
                           final int            idLen,
                           final int            sizeLen) {
        var buf = new StringBuilder();

        // constant id
        int    id     = fsNode.getPosition();
        String idText = String.valueOf(id);
        buf.append('[');
        for (int i = 0, c = Math.max(0, idLen - idText.length()); i < c; ++i) {
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
        for (int i = 0, c = Math.max(0, sizeLen - sizeText.length()); i < c; ++i) {
            buf.append(' ');
        }
        buf.append(sizeText);

        // date/time
        buf.append(' ');
        LocalDateTime time = null;
        try {
            time = OffsetDateTime.parse(fsNode.getModified()).toLocalDateTime();
        } catch (final Exception ignore) {}
        if (time == null) {
            buf.append("??? ??  ????");
        } else {
            buf.append(time.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
               .append(' ');
            int day = time.getDayOfMonth();
            if (day < 10) {
                buf.append(' ');
            }
            buf.append(day);
            buf.append(' ');
            // within the last 6 months, we show time, otherwise year
            if (time.isAfter(RECENT) && time.isBefore(SOON)) {
                int hour = time.getHour();
                if (hour < 10) {
                    buf.append(0);
                }
                buf.append(hour)
                   .append(':');
                int minute = time.getMinute();
                if (minute < 10) {
                    buf.append(0);
                }
                buf.append(minute);
            } else {
                buf.append(' ');
                // four digit year
                String s = String.valueOf(10000+time.getYear());
                buf.append(s.substring(s.length()-4));
            }
        }

        // name
        buf.append(' ');
        if (linked) {
            if (!indent.isEmpty()) {
                buf.append(indent, 0, indent.length() - 4).append(" |  ");
            }
            buf.append("-> ");
        } else {
            buf.append(indent);
        }
        var name = fsNode.getName();
        if (name.isEmpty()) {
            buf.append('/');
        } else {
            Handy.appendString(buf, name);
        }

        boolean alreadyVisited = visited[id];
        visited[id] = true;
        switch (fsNode.getFormat()) {
        case FSDir:
            if (alreadyVisited) {
                buf.append(" (see above)");
                out(buf);
            } else {
                out(buf);
                var      aKidNodes  = fsNode.getDirectoryContents();
                int      cKidNodes  = aKidNodes.length;
                String   nextIndent = " |- ";
                if (!indent.isEmpty()) {
                    nextIndent = indent.substring(0, indent.length()-4)
                               + (last ? "    " : " |  ")
                               + nextIndent;
                }
                for (int i = 0; i < cKidNodes; ++i) {
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

    public void findFile(final Component component, final File target) {
        if (target == null) {
            out("No file specified.");
            return;
        }

        if (!target.exists()) {
            out("File " + target + " does not exist.");
            return;
        }

        if (!target.isFile() || !target.canRead()) {
            out("Can not read file: " + target);
            return;
        }

        // load the file contents to search for; the contents can occur as a String or Byte[]
        // constant in the module
        byte[] findBytes;
        try {
            findBytes = readFileBytes(target);
        } catch (final IOException e) {
            out("Failure reading " + target + ":\n" + e);
            return;
        }
        String findString = null;
        try {
            findString = new String(readFileChars(target));
        } catch (final IOException ignore) {}

        // load the file metadata
        var    findName     = target.getName();
        var    findCreated  = requireNonNull(createdTime(target)).toInstant().atOffset(UTC).toString();
        var    findModified = requireNonNull(modifiedTime(target)).toInstant().atOffset(UTC).toString();
        int    findSize     = findBytes.length;

        var pool = component.getConstantPool();
        int matches = 0;
        for (int i = 0, c = pool.size(); i < c; ++i) {
            var constant = pool.getConstant(i);
            switch (constant.getFormat()) {
            case FSFile -> {
                var fsnode = (FSNodeConstant) constant;
                if (fsnode.getName().equals(findName) && Arrays.equals(fsnode.getFileBytes(), findBytes)) {
                    out("File constant [" + constant.getPosition() + "] matches file name and contents");
                    if (!fsnode.getCreated().equals(findCreated)) {
                        out("  -> File creation " + findCreated + " does not match constant: " + fsnode.getCreated());
                    }
                    if (!fsnode.getModified().equals(findModified)) {
                        out("  -> File modified " + findModified + " does not match constant: " + fsnode.getModified());
                    }
                    ++matches;
                }
            }
            case String -> {
                if (((StringConstant) constant).getValue().equals(findString)) {
                    out("String constant [" + constant.getPosition() + "] matches file contents");
                    ++matches;
                }
            }
            case UInt8Array -> {
                if (findSize > 0 && Arrays.equals(((UInt8ArrayConstant) constant).getValue(), findBytes)) {
                    out("Byte[] constant [" + constant.getPosition() + "] matches file contents");
                    ++matches;
                }
            }
            }
        }

        out(switch (matches) {
            case 0  -> "No matches found.";
            case 1  -> "1 match found.";
            default -> matches + " matches found.";
        });
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy disassembler:

                Examines a compiled Ecstasy module.

                Note: The xam command will be removed, and replaced with an option on the xtc command.""";
    }
}