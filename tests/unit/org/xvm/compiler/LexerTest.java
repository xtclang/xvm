package org.xvm.compiler;


import java.util.List;

import org.junit.Test;


/**
 * TODO
 *
 * @author cp 2015.11.13
 */
public class LexerTest
    {
    @Test
    public void testSimple()
        {
        Source    source  = new Source("module Test {}");
        ErrorList errlist = new ErrorList(5);
        Lexer lexer = new Lexer(source, errlist);

        System.out.println("tokens:");
        while (lexer.hasNext())
            {
            System.out.println(lexer.next());
            }

        System.out.println("error list (" + errlist.getSeriousErrorCount()
                + " of " + errlist.getSeriousErrorMax() + ", sev="
                + errlist.getSeverity() + "):");
        List<ErrorList.ErrorInfo> list = errlist.getErrors();
        for (ErrorList.ErrorInfo info : list)
            {
            System.out.println(info);
            }
        }
    }
