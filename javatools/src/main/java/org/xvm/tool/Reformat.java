package org.xvm.tool;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Stack;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Handy;
import org.xvm.util.Severity;

import static org.xvm.compiler.Token.Id.ENC_COMMENT;
import static org.xvm.compiler.Token.Id.EOL_COMMENT;


/**
 * Reformat Java or Ecstasy source code to the Ecstasy convention.
 *  ../lib_ecstasy/src/main/x/
 *  ../javatools/src/main/java
 *  ../lib_ecstasy/src/main/x/ecstasy/collections/Array.x
 *  ../manualtests/src/main/x/test.x
 */
public class Reformat
        extends Launcher {

    /**
     * Entry point from the command line.
     *
     * @param args  the command line arguments
     */
    public static void main(String[] args) {
        new Reformat(args).run();
    }

    /**
     * Constructor for the Reformat tool.
     *
     * @param args  the command line arguments
     */
    public Reformat(String[] args) {
        super(args);
    }

    @Override
    protected void process() {
        File file = options().getTarget();
        try {
            process(file);
        } catch (IOException e) {
            log(Severity.ERROR, "An IOException occurred: " + e);
            e.printStackTrace();
        }
    }

    protected void process(File file)
            throws IOException {

        if (!file.exists()) {
            out("File does not exist: " + file);
            return;
        }

        if (file.isDirectory()) {
            File[] xfiles  = file.listFiles(f -> f.isFile() && !f.getName().startsWith(".") && f.getName().endsWith(".x"));
            File[] jfiles  = file.listFiles(f -> f.isFile() && !f.getName().startsWith(".") && f.getName().endsWith(".java"));
            File[] subdirs = file.listFiles(f -> f.isDirectory());
            if (xfiles == null || jfiles == null || subdirs == null) {
                out("Directory could not be successfully listed: " + file);
                return;
            }

            if (xfiles.length == 0 && jfiles.length == 0 && subdirs.length == 0) {
                out("Directory contains no reformat-able content: " + file);
                return;
            }

            out("Processing directory: " + file);

            if (jfiles.length > 0) {
                Arrays.sort(jfiles, Comparator.comparing(File::getName));
                for (File jfile : jfiles) {
                    process(jfile);
                }
            }

            if (xfiles.length > 0) {
                Arrays.sort(jfiles, Comparator.comparing(File::getName));
                for (File xfile : xfiles) {
                    process(xfile);
                }
            }

            if (subdirs.length > 0) {
                Arrays.sort(subdirs, Comparator.comparing(File::getName));
                for (File subdir : subdirs) {
                    process(subdir);
                }
            }

        return;
        }

        if (!(file.isFile() && file.canRead() && file.canWrite())) {
            out("File is not readable and writable: " + file);
            return;
        }

        String name = file.getName();
        if (!name.endsWith(".x") && !name.endsWith(".java")) {
            out("File is not an Ecstasy or Java source file: " + file);
            return;
        }

        out("Reformatting source: " + file);
        Source source       = new Source(file, 0);
        BitSet nonComments  = new BitSet();
        for (Lexer lexer = new Lexer(source, new ErrorList(1)); lexer.hasNext(); ) {
            Token token = lexer.next();
            if (token.getId() != EOL_COMMENT && token.getId() != ENC_COMMENT) {
                for (int lineNo  = Source.calculateLine(token.getStartPosition()),
                         end = Source.calculateLine(token.getEndPosition()); lineNo <= end; ++lineNo) {
                    nonComments.set(lineNo);
                }
            }
        }

        String         origSrc        = source.toRawString();
        String[]       lines          = Handy.parseDelimitedString(origSrc, '\n');
        StringBuilder  buf            = new StringBuilder((int) file.length());
        int            expectIndent   = 0;
        int            adjustIndent   = 0;
        Stack<Id>      nestedIds      = new Stack<>();
        Stack<Boolean> implicitIndent = new Stack<>();
        int            closingCurlies = 0;
        for (int lineNo = 0, lineCount = lines.length; lineNo < lineCount; ++lineNo) {
            String line      = lines[lineNo];
            String trimmed   = line.trim();
            String unlabeled = trimmed;
            if (trimmed.length() == 0) {
                buf.append('\n');
                continue;
            }

            // calculate the actual indentation of the line
            int actualIndent = 0;
            while (line.charAt(actualIndent) <= 0x20) {
                ++actualIndent;
            }

            int addToExpect = 0;
            int addToAdjust = 0;

            if (nonComments.get(lineNo)) {
                if (trimmed.startsWith("{") || trimmed.endsWith("{")) {
                    buf.append(' ')
                       .append(trimmed);
                    if (!implicitIndent.empty() && implicitIndent.peek()) {
                        // just replace the implicit indent with an explicit indent (no change to the
                        // amount of indent because we already adjusted it)
                        implicitIndent.pop();
                        implicitIndent.push(false);
                    } else if (Handy.countChar(trimmed, '{') > Handy.countChar(trimmed, '}')) {
                        expectIndent += 4;
                        nestedIds.push(Id.L_CURLY);
                        implicitIndent.push(false);
                    }
                    continue;
                }

                if (trimmed.startsWith("}")) {
                    boolean overlap  = false;
                    if (implicitIndent.empty()) {
                        out("unexpected close curly");
                    } else {
                        boolean implicit = implicitIndent.pop();
                        switch (nestedIds.pop()) {
                        case SWITCH:
                            adjustIndent -= 4;
                            break;
                        case CASE:
                            if (implicit) {
                                // we're actually closing the switch, not the case
                                implicitIndent.pop();
                                if (nestedIds.pop() == Id.SWITCH) {
                                    adjustIndent -= 4;
                                }
                                expectIndent -= 4;
                            } else {
                                nestedIds.push(Id.CASE);
                                implicitIndent.push(true);
                                // stupid C style formatting allows multiple closing braces at same column
                                overlap = true;
                                expectIndent += 4;
                            }
                        }
                    }
                    expectIndent = Math.max(0, expectIndent - 4);
                    actualIndent = expectIndent - adjustIndent - (overlap ? 4 : 0);
                    if (actualIndent == 0) {
                        ++closingCurlies;
                    }
                    if (buf.length() == 0 || buf.charAt(buf.length()-1) != '{') {
                        buf.append('\n')
                           .append(Handy.dup(' ', Math.max(0, actualIndent)));
                    }
                    buf.append(trimmed);
                    continue;
                }

                if (trimmed.indexOf(':') >= 0) {
                    Lexer lexer = new Lexer(new Source(line), new ErrorList(1));
                    try {
                        if (lexer.next().getId() == Id.IDENTIFIER && lexer.next().getId() == Id.COLON && lexer.hasNext()) {
                            unlabeled = line.substring(Source.calculateOffset(lexer.next().getStartPosition())).trim();
                        }
                    } catch (Exception e) {}
                }

                if ((unlabeled.startsWith("if ") || unlabeled.startsWith("if(")
                        || unlabeled.startsWith("for ") || unlabeled.startsWith("for(")
                        || unlabeled.equals("do") || unlabeled.startsWith("do ")
                        || unlabeled.equals("try") || unlabeled.startsWith("try ") || unlabeled.startsWith("try(")
                        || unlabeled.equals("using") || unlabeled.startsWith("using ") || unlabeled.startsWith("using("))
                        && !unlabeled.endsWith("}")) {
                    implicitIndent.push(true);
                    nestedIds.push(switch (unlabeled.charAt(0)) {
                        case 'i' -> Id.IF;
                        case 'f' -> Id.FOR;
                        case 'd' -> Id.DO;
                        case 't' -> Id.TRY;
                        case 'u' -> Id.USING;
                        default ->  Id.ANY;     // whatever
                    });
                    addToExpect = 4;
                } else if (unlabeled.startsWith("switch ") || unlabeled.startsWith("switch(")) {
                    implicitIndent.push(true);
                    nestedIds.push(Id.SWITCH);
                    // code is indented under a switch; undo that indent
                    addToExpect = 4;
                    addToAdjust = 4;

                } else if (unlabeled.startsWith("while ") || unlabeled.startsWith("while(")) {
                    if (unlabeled.endsWith(";")) {
                        buf.append(' ')
                           .append(trimmed);
                        continue;
                    } else if (!unlabeled.endsWith("}")) {
                        // do..while ends with ';', and an "inline while" ends with "{}"
                        implicitIndent.push(true);
                        nestedIds.push(Id.WHILE);
                        addToExpect = 4;
                    }
                } else if (unlabeled.equals("else") || unlabeled.startsWith("else ")
                        || unlabeled.startsWith("catch ") || unlabeled.startsWith("catch(")
                        || unlabeled.equals("finally") || unlabeled.startsWith("finally ")) {
                    buf.append(' ')
                       .append(trimmed);

                    if (!trimmed.endsWith("{}")) {
                        implicitIndent.push(true);
                        nestedIds.push(switch (unlabeled.charAt(0)) {
                            case 'e' -> Id.ELSE;
                            case 'c' -> Id.CATCH;
                            case 'f' -> Id.FINALLY;
                            default -> Id.ANY;     // whatever
                        });
                        expectIndent += 4;
                    }
                    continue;
                } else if (unlabeled.startsWith("case ") || unlabeled.startsWith("case(") || unlabeled.startsWith("default:")) {
                    if (nestedIds.peek() == Id.CASE && implicitIndent.peek()) {
                        implicitIndent.pop();
                        implicitIndent.push(true);
                    } else {
                        nestedIds.push(Id.CASE);
                        implicitIndent.push(true);
                        expectIndent += 4;
                    }
                    buf.append('\n')
                       .append(Handy.dup(' ', Math.max(0, expectIndent - adjustIndent - 4)))
                       .append(trimmed);
                    continue;
                } else if (!implicitIndent.empty() && implicitIndent.peek()) {
                    implicitIndent.pop();
                    implicitIndent.push(true);
                }
            }

            if (actualIndent < expectIndent) {
                // this could be a zero-indent label, a commented line, or ... whatever it is,
                // it needs to be reviewed by eye
                buf.append('\n')
                   .append(Handy.dup(' ', actualIndent))
                   .append(trimmed);
            } else {
                buf.append('\n')
                   .append(Handy.dup(' ', Math.max(0, actualIndent - adjustIndent)))
                   .append(trimmed);
            }

            expectIndent += addToExpect;
            adjustIndent += addToAdjust;
        }

        String newSrc = buf.delete(0,1).toString();
        if (options().isVerbose() || closingCurlies != 1) {
            out("file=" + file);
            if (newSrc.equals(origSrc)) {
                out("no changes");
            } else {
                out(Handy.indentLines(newSrc, " |"));
                out();
            }
        } else if (!options().isTest()) {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(newSrc);
            writer.close();
        }
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
                Ecstasy code reformatter:
        
                Usage:
        
                    xrc <options> <dir|file>
                """;
    }


    // ----- options -------------------------------------------------------------------------------

    @Override
    public Options options() {
        return (Options) super.options();
    }

    @Override
    protected Options instantiateOptions() {
        return new Options();
    }

    /**
     * Reformat command-line options implementation.
     */
    public class Options
            extends Launcher.Options {

        /**
         * Construct the Runner Options.
         */
        public Options() {
            super();
            addOption("t"     , Form.Name, false, "Test mode shows changes but does not modify files");
            addOption(Trailing, Form.File, false, "Source file name (.x or .java) to reformat");
        }

        /**
         * @return the file to execute
         */
        public File getTarget() {
            return (File) values().get(Trailing);
        }

        /**
         * @return true if the "test only" option has been specified
         */
        boolean isTest() {
            return specified("t");
        }

        @Override
        public void validate() {
            super.validate();

            // validate the trailing file / directory (to reformat)
            File fileModule = getTarget();
            if (fileModule == null) {
                log(Severity.ERROR, "Source file or directory name must be specified");
            } else if (!fileModule.exists()) {
                log(Severity.ERROR, "Source file or directory \"" + fileModule + "\" does not exist");
            } else if (fileModule.isFile()
                    && !fileModule.getName().endsWith(".x")
                    && !fileModule.getName().endsWith(".java")) {
                log(Severity.ERROR, "Source file must have a .x or .java extension");
            }
        }
    }
}
