package org.xvm.util;


import java.util.ArrayList;


/**
 * A recorder of console output.
 */
public class ConsoleLog
    {
    /**
     * Add text to the log.
     *
     * @param ach         the text to add
     * @param addNewline  if the text is followed by a new line
     */
    public void log(char[] ach, boolean addNewline)
        {
        if (ach == null)
            {
            if (addNewline)
                {
                advance();
                }
            return;
            }

        int    cch     = ach.length;
        int    ofStart = 0;
        int    of      = 0;
        while (of < cch)
            {
            char ch = ach[of];
            if (ch == '\n')
                {
                if (of > ofStart)
                    {
                    append(ach, ofStart, of - ofStart);
                    }
                advance();
                ofStart = of+1;
                }
            else if (!Character.isDefined(ch) || Character.isISOControl(ch))
                {
                ach = ach.clone();
                ach[of] = '?';
                }
            ++of;
            }

        if (of > ofStart)
            {
            append(ach, ofStart, of - ofStart);
            }

        if (addNewline)
            {
            advance();
            }
        }

    /**
     * Obtain the number of lines of text that are available.
     *
     * @return the number of previously logged lines available
     */
    public int size()
        {
        return m_cLines;
        }

    /**
     * Obtain the specified line of text.
     *
     * @param i  the line number, <tt>0 < i < size()</tt>
     *
     * @return the specified line of text
     */
    public String get(int i)
        {
        int iLine = m_iLine - m_cLines + i;
        if (iLine < 0)
            {
            iLine += 1024;
            }
        return m_asLine[iLine];
        }

    /**
     * Obtain up to the specified number of lines of the console, rendered with left and right
     * margin indicating line continuation.
     *
     * @param width   the total width, including margins
     * @param height  the maximum height, in terms of rendered lines
     *
     * @return the specified block of text
     */
    public String render(int width, int height)
        {
        if (m_cLines == 0)
            {
            return "(No logged output.)";
            }

        int cchWide = width-2;
        int cHigh   = height-2;

        StringBuilder sb = new StringBuilder(width);
        sb.append('|');
        for (int i = 0; i < cchWide; ++i)
            {
            sb.append('-');
            }
        sb.append('|');
        String sHeader = sb.toString();

        // render the lines backwards
        ArrayList<String> listLines = new ArrayList<>();
        int               iNext     = m_asLine[m_iLine] == null ? m_iLine-1 : m_iLine;  // neg?
        int               cRemain   = m_cLines;
        String            sChunk    = null;
        for (int iLine = 0; iLine < cHigh; ++iLine)
            {
            boolean fWasLeftover = sChunk != null;
            if (!fWasLeftover)
                {
                // load next chunk
                if (cRemain <= 0)
                    {
                    break;
                    }

                if (iNext < 0)
                    {
                    iNext = 1023;
                    }
                sChunk = m_asLine[iNext--];
                --cRemain;
                }

            int     cchChunk     = sChunk.length();
            boolean fHasLeftover = cchChunk > cchWide;

            // render one line
            sb = new StringBuilder(width);
            if (fHasLeftover)
                {
                sb.append('\\');
                int cchChop = cchChunk % cchWide;
                if (cchChop == 0)
                    {
                    cchChop = cchWide;
                    }
                sb.append(sChunk.substring(cchChunk-cchChop));
                sChunk = sChunk.substring(0, cchChunk-cchChop);
                }
            else
                {
                sb.append('|');
                sb.append(sChunk);
                sChunk = null;
                }

            for (int i = 0, cchFill = cchWide + 1 - sb.length(); i < cchFill; ++i)
                {
                sb.append(' ');
                }

            sb.append(fWasLeftover ? '\\' : '|');

            listLines.add(sb.toString());
            }

        int cLines = listLines.size();
        sb = new StringBuilder((width+1) * (cLines + 2) - 1);
        sb.append(sHeader);
        for (int iLine = 0; iLine < cLines; ++iLine)
            {
            sb.append('\n')
              .append(listLines.get(cLines - iLine - 1));
            }
        sb.append('\n')
          .append(sHeader);

        return sb.toString();
        }


    // ----- internal ------------------------------------------------------------------------------

    private void append(char[] ach, int of, int cch)
        {
        String sAppend = cch <= 0 ? "" : new String(ach, of, cch);
        if (m_asLine[m_iLine] == null)
            {
            m_asLine[m_iLine] = sAppend;
            addLine();
            }
        else
            {
            m_asLine[m_iLine] = m_asLine[m_iLine] + sAppend;
            }
        }

    private void advance()
        {
        if (m_asLine[m_iLine] == null)
            {
            m_asLine[m_iLine] = "";
            addLine();
            }

        if (++m_iLine >= 1024)
            {
            m_iLine = 0;
            }
        m_asLine[m_iLine] = null;
        }

    private void addLine()
        {
        if (++m_cLines >= 1024)
            {
            m_cLines = 1023;
            }
        }

    // ----- data members --------------------------------------------------------------------------

    private final String[] m_asLine = new String[1024];
    private int            m_cLines = 0;
    private int            m_iLine  = 0;
    }
