package org.xvm.compiler;


import org.junit.Test;
import org.xvm.compiler.ast.Statement;

import java.io.File;
import java.util.List;


/**
 * Test of the Ecstasy parser
 *
 * @author cp 2017.03.28
 */
public class ParserTest
    {
    /**
     * Allow for command-line testing
     *
     * @param args  file name
     */
    public static void main(String[] args)
            throws Exception
        {
        if (args.length < 1 || args[0].length() < 1)
            {
            out("file name required");
            return;
            }

        File file = new File(args[0]);
        if (!(file.exists() && file.canRead()))
            {
            out("cannot read file: " + args[0]);
            return;
            }

        Source source = new Source(file);
        parse(source);
        }

    @Test
    public void testSimple()
        {
        Source source = new Source("module Test {}");
        parse(source);
        }

    static void parse(Source source)
        {
        ErrorList errlist = new ErrorList(5);
        Parser parser = new Parser(source, errlist);

        Statement stmt = parser.parseCompilationUnit();
        out(stmt);

        out("error list (" + errlist.getSeriousErrorCount()
                + " of " + errlist.getSeriousErrorMax() + ", sev="
                + errlist.getSeverity() + "):");
        List<ErrorList.ErrorInfo> list = errlist.getErrors();
        for (ErrorList.ErrorInfo info : list)
            {
            out(info);
            }
        }

    /**
     * Debug output.
     *
     * @param o  something to print
     */
    static void out(Object o)
        {
        System.out.println(o);
        }
    }
