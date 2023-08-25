package org.xvm.compiler;


import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the Source class.
 */
public class SourceTest
    {
    @Test
    public void testSimple()
        {
        String sCode = "hello world!";
        Source source = new Source(sCode);
        StringBuilder builder = new StringBuilder();
        while (source.hasNext())
            {
            builder.append(source.next());
            }
        assertEquals(sCode, builder.toString());
        }

    @Test @Disabled
    public void testFile1()
            throws IOException
        {
        Source source = new Source(
                new File(SourceTest.class.getResource("Source_1.x").getPath()), 0);
        assertEquals(0, source.getLine());
        assertEquals(0, source.getOffset());

        char[] ach = "this is a test\nof Unicode\n".toCharArray();
        int of = 0;
        while (source.hasNext())
            {
            char ch = source.next();
            assertEquals(ach[of++], ch);
            }

        assertEquals(2, source.getLine());
        assertEquals(0, source.getOffset());
        }

    @Test @Disabled
    public void testFile1Rewind()
            throws IOException
        {
        Source source = new Source(
                new File(SourceTest.class.getResource("Source_1.x").getPath()), 0);
        assertEquals(0, source.getLine());
        assertEquals(0, source.getOffset());

        while (source.hasNext())
            {
            int  iLinePre1    = source.getLine();
            int  iOffsetPre1  = source.getOffset();
            char ch1          = source.next();
            int  iLinePost1   = source.getLine();
            int  iOffsetPost1 = source.getOffset();

            source.rewind();

            int  iLinePre2    = source.getLine();
            int  iOffsetPre2  = source.getOffset();
            char ch2          = source.next();
            int  iLinePost2   = source.getLine();
            int  iOffsetPost2 = source.getOffset();

            assertEquals(iLinePre1   , iLinePre2   );
            assertEquals(iOffsetPre1 , iOffsetPre2 );
            assertEquals(ch1         , ch2         );
            assertEquals(iLinePost1  , iLinePost2  );
            assertEquals(iOffsetPost1, iOffsetPost2);
            }

        assertEquals(2, source.getLine());
        assertEquals(0, source.getOffset());
        }

    @Test @Disabled
    public void testFile1Reset()
            throws IOException
        {
        Source source = new Source(
                new File(SourceTest.class.getResource("Source_1.x").getPath()), 0);

        int iLinePre1     = source.getLine();
        int iOffsetPre1   = source.getOffset();
        StringBuilder sb1 = new StringBuilder();
        while (source.hasNext())
            {
            sb1.append(source.next());
            }
        int iLinePost1    = source.getLine();
        int iOffsetPost1  = source.getOffset();

        source.reset();

        int iLinePre2     = source.getLine();
        int iOffsetPre2   = source.getOffset();
        StringBuilder sb2 = new StringBuilder();
        while (source.hasNext())
            {
            sb2.append(source.next());
            }
        int iLinePost2    = source.getLine();
        int iOffsetPost2  = source.getOffset();

        assertEquals(iLinePre1     , iLinePre2     );
        assertEquals(iOffsetPre1   , iOffsetPre2   );
        assertEquals(sb1.toString(), sb2.toString());
        assertEquals(iLinePost1    , iLinePost2    );
        assertEquals(iOffsetPost1  , iOffsetPost2  );
        }

    @Test
    public void testNewLines()
        {
        String sScript = "0\n1\n\r3\r\n4\r5";
        Source source  = new Source(sScript);

        int cChars = 0;
        while (source.hasNext())
            {
            int  iLinePre  = source.getLine();
            int  ofPre     = source.getOffset();
            char ch        = source.next();
            int  iLinePost = source.getLine();
            int  ofPost    = source.getOffset();
            ++cChars;

            if (Lexer.isLineTerminator(ch))
                {
                assertEquals(iLinePre + 1, iLinePost);
                assertEquals(0, ofPost);
                }
            else if (ch >= '0' && ch <= '9')
                {
                assertEquals(ch - '0', source.getLine());
                assertEquals(0, ofPre);
                assertEquals(1, ofPost);
                }

            source.rewind();
            assertEquals(iLinePre, source.getLine());
            assertEquals(ofPre, source.getOffset());
            assertEquals(ch, source.next());
            assertEquals(iLinePost, source.getLine());
            assertEquals(ofPost, source.getOffset());
            }
        }
    }
