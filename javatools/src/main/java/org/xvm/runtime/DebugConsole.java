package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.util.Handy;


/**
 * Debugger console.
 */
public class DebugConsole
        implements Debugger
    {
    private DebugConsole()
        {
        }

    @Override
    public int enter(Frame frame, int iPC)
        {
        return enterCommand(frame, iPC);
        }

    @Override
    public int checkBreakPoint(Frame frame, int iPC)
        {
        boolean fDebug = false;
        switch (m_stepMode)
            {
            case StepOver:
                // "step over" on Return can turn into "step out"
                fDebug = frame.f_context == m_frame.f_context && frame.f_iId <= m_frame.f_iId;
                break;

            case StepOut:
                // TODO: how to "step out" of service?
                fDebug = frame.f_context == m_frame.f_context && frame.f_iId < m_frame.f_iId;
                break;

            case StepInto:
                fDebug = true;
                break;

            case StepLine:
                fDebug = frame.f_context == m_frame.f_context && frame.f_iId == m_frame.f_iId &&
                         iPC == m_iPC;
                break;

            case None:
                fDebug = m_setLineBreaks != null && m_setLineBreaks.stream().anyMatch(bp -> bp.matches(frame, iPC));
                break;
            }

        return fDebug
                ? enterCommand(frame, iPC)
                : iPC + 1;
        }

    @Override
    public void checkBreakPoint(Frame frame, ExceptionHandle hEx)
        {
        if (m_fBreakOnAllThrows || m_setThrowBreaks != null && m_setThrowBreaks.stream().anyMatch(bp -> bp.matches(hEx)))
            {
            enterCommand(frame, -1);
            }
        }

    /**
     * Allow interactive debugger commands.
     *
     * @param frame  the current frame
     * @param iPC    the current PC (-1 for an exception breakpoint)
     *
     * @return the next PC or any of the Op.R_* values
     */
    private int enterCommand(Frame frame, int iPC)
        {
        m_frame    = frame;
        m_iPC      = iPC;
        m_stepMode = StepMode.None;

        PrintWriter    writer = xTerminalConsole.CONSOLE_OUT;
        BufferedReader reader = xTerminalConsole.CONSOLE_IN;

        writer.println(renderDisplay());

        NextCommand:
        while (true)
            {
            try
                {
                writer.print("\nEnter command: ");
                writer.flush();

                String sCommand = reader.readLine();
                if (sCommand == null)
                    {
                    // we don't have a console; ignore
                    writer.println();
                    return iPC + 1;
                    }

                sCommand = sCommand.trim();
                if (sCommand.length() == 0)
                    {
                    continue;
                    }

                String sDedup;
                while (!(sDedup = sCommand.replace("  ", " ")).equals(sCommand))
                    {
                    sCommand = sDedup;
                    }

                String[] asParts = Handy.parseDelimitedString(sCommand, ' ');
                int      cArgs   = asParts.length - 1;
                if (cArgs < 0)
                    {
                    continue;
                    }

                switch (asParts[0].toUpperCase())
                    {
                    case "B":
                        if (cArgs != 0)
                            {
                            break;
                            }

                        writer.println(renderBreakpoints());
                        continue NextCommand;

                    case "B+":
                        switch (cArgs)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    addBP(makeBreakPoint(frame, iPC));
                                    continue NextCommand;
                                    }
                                break; // the command is not allowed

                            case 2:
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                                if (bp != null)
                                    {
                                    addBP(bp);
                                    continue NextCommand;
                                    }
                                break;  // invalid break point
                            }
                        break;

                    case "B-":
                        switch (cArgs)
                            {
                            case 0:
                                // "B-" remove the currrent line from the list of breakpoints
                                if (iPC >= 0)
                                    {
                                    removeBP(makeBreakPoint(frame, iPC));
                                    continue NextCommand;
                                    }
                                break; // the command is not allowed

                            case 1:
                                // "B- *"  (nuke all breakpoints)
                                if (asParts[1].equals("*"))
                                    {
                                    m_fBreakOnAllThrows = false;
                                    m_setLineBreaks     = null;
                                    m_setThrowBreaks    = null;
                                    continue NextCommand;
                                    }
                                // "B- 3"  (# is from the previously displayed list of breakpoints)
                                else if (m_aBreaks != null && asParts[1].chars().allMatch(n -> n >= '0' && n <= '9'))
                                    {
                                    int n = Integer.parseInt(asParts[1]);
                                    if (n >= 0 && n < m_aBreaks.length)
                                        {
                                        removeBP(m_aBreaks[n]);
                                        continue NextCommand;
                                        }
                                    }
                                break; // invalid command

                            case 2:
                                // "B- MyClass 123"  (class name and line number)
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                                if (bp != null)
                                    {
                                    removeBP(bp);
                                    continue NextCommand;
                                    }
                                break; // invalid break point
                            }
                        break;

                    case "BE+":
                        if (cArgs == 1)
                            {
                            addBP(new BreakPoint(asParts[1]));
                            continue NextCommand;
                            }
                        break; // invalid command

                    case "BE-":
                        if (cArgs == 1)
                            {
                            removeBP(new BreakPoint(asParts[1]));
                            continue NextCommand;
                            }
                        break; // invalid command

                    case "BT":
                        switch (cArgs)
                            {
                            case 0:
                                {
                                BreakPoint bp       = makeBreakPoint(frame, iPC);
                                BreakPoint bpExists = findBP(bp);
                                if (bpExists == null)
                                    {
                                    addBP(bp);
                                    }
                                else
                                    {
                                    toggleBP(bpExists);
                                    }
                                continue NextCommand;
                                }

                            case 1:
                                if (asParts[1].equals("*"))
                                    {
                                    BreakPoint[] aBP = allBreakpoints();
                                    if (Arrays.stream(aBP).allMatch(BreakPoint::isEnabled))
                                        {
                                        Arrays.stream(aBP).forEach(BreakPoint::disable);
                                        m_fBreakOnAllThrows = false;
                                        }
                                    else
                                        {
                                        Arrays.stream(aBP).forEach(BreakPoint::enable);
                                        m_fBreakOnAllThrows = Arrays.stream(aBP).anyMatch(bp -> bp.className.equals("*"));
                                        }
                                    continue NextCommand;
                                    }
                                // "BT 3"  (# is from the previously displayed list of breakpoints)
                                else if (m_aBreaks != null && asParts[1].chars().allMatch(n -> n >= '0' && n <= '9'))
                                    {
                                    int n = Integer.parseInt(asParts[1]);
                                    if (n >= 0 && n < m_aBreaks.length)
                                        {
                                        BreakPoint bp = m_aBreaks[n];
                                        if (bp.isEnabled())
                                            {
                                            bp.disable();
                                            }
                                        else
                                            {
                                            bp.enable();
                                            }
                                        continue NextCommand;
                                        }
                                    }
                                else
                                    {
                                    toggleBP(new BreakPoint(asParts[1]));
                                    continue NextCommand;
                                    }
                                break;

                            case 2:
                                {
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                                if (bp != null)
                                    {
                                    toggleBP(bp);
                                    continue NextCommand;
                                    }
                                }
                                break;
                            }
                        break;

                    case "F":
                        if (cArgs == 1 && asParts[1].chars().allMatch(n -> n >= '0' && n <= '9'))
                            {
                            Frame.StackFrame[] aFrames = m_aFrames;
                            int                cFrames = aFrames == null ? 0 : aFrames.length;
                            int                iFrame  = Integer.parseInt(asParts[1]);
                            if (iFrame >= 0 && iFrame < cFrames)
                                {
                                m_frameFocus = aFrames[iFrame].frame;
                                }
                            m_nViewMode = 0;
                            writer.println(renderDebugger());
                            continue NextCommand;
                            }
                        break;

                    case "?": case "H": case "HELP":
                        writer.println(renderHelp());
                        continue NextCommand;

                    case "S":
                        m_stepMode = StepMode.StepOver;
                        break NextCommand;

                    case "S+":
                        m_stepMode = StepMode.StepInto;
                        break NextCommand;

                    case "S-":
                        m_stepMode = StepMode.StepOut;
                        break NextCommand;

                    case "SL":
                        m_stepMode = StepMode.StepLine;
                        break NextCommand;

                    case "R":
                        break NextCommand;

                    case "VC":
                        m_nViewMode = 1;
                        writer.println(renderConsole());
                        continue NextCommand;

                    case "VD":
                        m_nViewMode = 0;
                        writer.println(renderDebugger());
                        continue NextCommand;

                    case "VS":
                        switch (cArgs)
                            {
                            case 0:
                                writer.println("Current debugger text width=" + m_cWidth +
                                               " characters, height= " + m_cHeight + " lines.");
                                continue NextCommand;

                            case 2:
                                {
                                int n = parseNonNegative(asParts[2]);
                                if (n >= 10 && n < 1000)
                                    {
                                    writer.println("Altering debugger text height from " + m_cHeight +
                                                   " to " + n + " lines.");
                                    m_cHeight = n;
                                    }
                                else
                                    {
                                    writer.println("Illegal text height: " + asParts[2]);
                                    continue NextCommand;
                                    }
                                }
                                // fall through
                            case 1:
                                {
                                int n = parseNonNegative(asParts[1]);
                                if (n >= 40 && n < 1000)
                                    {
                                    writer.println("Altering debugger text width from " + m_cWidth +
                                                   " to " + n + " characters.");
                                    m_cWidth = n;
                                    }
                                else
                                    {
                                    writer.println("Illegal text width: " + asParts[1]);
                                    }
                                continue NextCommand;
                                }
                            }
                        break;

                    default:
                        writer.println("Unknown command: \"" + sCommand + "\"; enter '?' for help");
                        continue NextCommand;
                    }

                writer.println("Invalid command: " + sCommand);
                }
            catch (IOException ignored) {}
            }

        frame.f_context.setDebuggerActive(m_setLineBreaks != null || m_setThrowBreaks != null || m_stepMode != StepMode.None);
        return iPC + 1;
        }

    private BreakPoint makeBreakPoint(Frame frame, int iPC)
        {
        MethodStructure method = frame.f_function;
        String          sName  = method.getContainingClass().getName();
        int             nLine  = method.calculateLineNumber(iPC);

        return new BreakPoint(sName, nLine);
        }

    void addBP(BreakPoint bp)
        {
        if (bp == null)
            {
            return;
            }

        Set<BreakPoint> setBP = bp.isException() ? m_setThrowBreaks : m_setLineBreaks;
        if (setBP == null)
            {
            setBP = new HashSet<>();
            if (bp.isException())
                {
                m_setThrowBreaks = setBP;
                }
            else
                {
                m_setLineBreaks = setBP;
                }
            }
        if (setBP.contains(bp))
            {
            findBP(bp).enable();
            }
        else
            {
            setBP.add(bp);
            }

        if (bp.className.equals("*"))
            {
            assert bp.isException();
            m_fBreakOnAllThrows = true;
            }
        }

    void removeBP(BreakPoint bp)
        {
        if (bp == null)
            {
            return;
            }

        Set<BreakPoint> setBP = bp.isException() ? m_setThrowBreaks : m_setLineBreaks;
        if (setBP != null)
            {
            setBP.remove(bp);
            if (setBP.isEmpty())
                {
                if (bp.isException())
                    {
                    m_setThrowBreaks = null;
                    }
                else
                    {
                    m_setLineBreaks = null;
                    }
                }
            }

        if (bp.className.equals("*"))
            {
            assert bp.isException();
            m_fBreakOnAllThrows = false;
            }
        }

    void toggleBP(BreakPoint bp)
        {
        bp = findBP(bp);
        if (bp != null)
            {
            if (bp.isEnabled())
                {
                bp.disable();
                }
            else
                {
                bp.enable();
                }

            if (bp.className.equals("*"))
                {
                assert bp.isException();
                m_fBreakOnAllThrows = bp.isEnabled();
                }
            }
        }

    BreakPoint findBP(BreakPoint bp)
        {
        if (bp != null)
            {
            Set<BreakPoint> setBP = bp.isException() ? m_setThrowBreaks : m_setLineBreaks;
            if (setBP != null)
                {
                Optional<BreakPoint> obp = setBP.stream().filter(bpEach -> bpEach.equals(bp)).findFirst();
                if (obp.isPresent())
                    {
                    return obp.get();
                    }
                }
            }

        return null;
        }

    private static BreakPoint parseBreakPoint(String sName, String sLine)
        {
        int nLine = parseNonNegative(sLine);

        return nLine <= 0 ? null : new BreakPoint(sName, nLine);
        }

    private static int parseNonNegative(String sNumber)
        {
        try
            {
            return Integer.parseInt(sNumber);
            }
        catch (NumberFormatException e)
            {
            return -1;
            }
        }

    /**
     * @return a string for whatever the debugger is supposed to display
     */
    private String renderDisplay()
        {
        switch (m_nViewMode)
            {
            case 0: return renderDebugger();
            case 1: return renderConsole();
            default: return "unknown view mode #" + m_nViewMode;
            }
        }

    /**
     * @return a string for the entire "console" view
     */
    private String renderConsole()
        {
        return "Console:\n" + xTerminalConsole.CONSOLE_LOG.render(m_cWidth, m_cHeight-1);
        }

    private String renderDebugger()
        {
        String   sFHeader  = "Call stack frames:";
        String[] asFrames  = renderFrames();
        int      cchFrames = Math.max(longestOf(asFrames), sFHeader.length());

        String   sVHeader  = "Variables and watches:";
        String[] asVars    = renderVars(m_cWidth - cchFrames - 2);
        int      cchVars   = Math.max(longestOf(asVars), sVHeader.length());
        if (cchFrames + cchVars + 1 > m_cWidth)
            {
            int max = m_cWidth - 1;
            int half = max / 2;
            if (cchFrames <= half)
                {
                cchVars = max - cchFrames;
                }
            else if (cchVars <= half)
                {
                cchFrames = max - cchVars;
                }
            else
                {
                cchFrames = half;
                cchVars   = max - cchFrames;
                }
            }

        StringBuilder sb = new StringBuilder();
        sb.append(ljust(sFHeader, cchFrames))
          .append('|')
          .append(sVHeader)
          .append('\n')
          .append(dup('-', cchFrames))
          .append('|')
          .append(dup('-', cchVars));

        int    iFrame  = 0;
        int    cFrames = asFrames.length;
        String sFrame  = null;
        int    iVar    = 0;
        int    cVars   = asVars.length;
        String sVar    = null;
        while (true)
            {
            // load the next frame to render
            if (sFrame == null)
                {
                if (iFrame < cFrames)
                    {
                    sFrame = asFrames[iFrame++];
                    }
                }
            else
                {
                // indent trailing lines
                sFrame = "    " + sFrame;
                }

            // load the next var to render
            if (sVar == null)
                {
                if (iVar < cVars)
                    {
                    sVar = asVars[iVar++];
                    }
                }
            else
                {
                // indent trailing lines
                sVar = "    " + sVar;
                }

            if (sFrame == null && sVar == null)
                {
                return sb.toString();
                }

            sb.append('\n');

            // render the frame
            if (sFrame == null)
                {
                sb.append(dup(' ', cchFrames));
                }
            else if (sFrame.length() > cchFrames)
                {
                sb.append(sFrame, 0, cchFrames);
                sFrame = sFrame.substring(cchFrames);
                }
            else
                {
                sb.append(ljust(sFrame, cchFrames));
                sFrame = null;
                }

            sb.append('|');

            // render the var
            if (sVar == null)
                {
                sb.append(dup(' ', cchVars));
                }
            else if (sVar.length() > cchVars)
                {
                sb.append(sVar, 0, cchVars);
                sVar = sVar.substring(cchVars);
                }
            else
                {
                sb.append(ljust(sVar, cchVars));
                sVar = null;
                }
            }
        }

    /**
     * @return a string for the entire "frames and variables" view
     */
    private String[] renderFrames()
        {
        Frame.StackFrame[] aFrames = m_frame.getStackFrameArray();
        Frame frameNewTop =   aFrames == null ||   aFrames.length < 1 ? null :   aFrames[0].frame;
        Frame frameOldTop = m_aFrames == null || m_aFrames.length < 1 ? null : m_aFrames[0].frame;
        m_aFrames = aFrames;

        Frame frameFocus = m_frameFocus;
        if (frameOldTop != frameNewTop)
            {
            frameFocus = frameNewTop;
            m_frameFocus = frameFocus;
            }

        int      cFrames     = aFrames.length;
        int      cchFrameNum = numlen(cFrames-1);
        String[] asFrames    = new String[cFrames];
        for (int i = 0; i < cFrames; ++i)
            {
            Frame.StackFrame segment = aFrames[i];

            asFrames[i] =
                (segment.frame == frameFocus ? '>' : ' ') +
                rjust(Integer.toString(i), cchFrameNum) + "  " + segment;
            }

        return asFrames;
        }

    private String[] renderVars(int cMax)
        {
        Frame              frame     = m_frameFocus;
        int                cVars     = frame.f_anNextVar[frame.m_iScope];
        int                cchVarNum = numlen(cVars-1);
        ArrayList<VarInfo> listVars  = new ArrayList<>(cVars);
        ArrayList<String>  listVals  = new ArrayList<>(cVars);
        for (int i = 0; i < cVars; i++)
            {
            VarInfo info = frame.getVarInfo(i);
            String  sVar = info == null ? "" : info.getName();
            if (!sVar.isEmpty())
                {
                ObjectHandle hValue = frame.f_ahVar[i];
                listVars.add(info);

                String sDescr = hValue.toString();
                sDescr = sDescr.replace('\n', ' ');

                int cDescrMax = cMax - cchVarNum - sVar.length() - 3;
                if (sDescr.length() > cDescrMax)
                    {
                    sDescr = sDescr.substring(0, cDescrMax - 3) + "...";
                    }

                listVals.add(
                    rjust(Integer.toString(i), cchVarNum) +
                    "  " +
                    sVar +
                    '=' +
                    (hValue == null ? "<not assigned>" : sDescr)
                    );
                }
            }
        m_aVars = listVars.toArray(new VarInfo[0]);
        return listVals.toArray(new String[0]);
        }

    private int longestOf(String[] as)
        {
        int max = 0;
        for (int i = 0, c = as.length; i < c; ++i)
            {
            int cch = as[i].length();
            if (cch > max)
                {
                max = cch;
                }
            }
        return max;
        }

    private BreakPoint[] allBreakpoints()
        {
        TreeSet<BreakPoint> setBP = new TreeSet<>();
        if (m_setLineBreaks != null)
            {
            assert !m_setLineBreaks.isEmpty();
            setBP.addAll(m_setLineBreaks);
            }
        if (m_setThrowBreaks != null)
            {
            assert !m_setThrowBreaks.isEmpty();
            setBP.addAll(m_setThrowBreaks);
            }

        return setBP.toArray(new BreakPoint[0]);
        }

    private String renderBreakpoints()
        {
        BreakPoint[] aBP = allBreakpoints();
        m_aBreaks = aBP;

        int cBP = aBP.length;
        if (cBP == 0)
            {
            return "(No breakpoints.)";
            }

        StringBuilder sb = new StringBuilder();
        sb.append("Breakpoint List:\n")
          .append("-------------------------");

        int cch = numlen(cBP);
        for (int i = 0; i < cBP; ++i)
            {
            sb.append('\n')
              .append(rjust(Integer.toString(i), cch))
              .append("  ")
              .append(aBP[i]);
            }
        return sb.toString();
        }

    String renderHelp()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Command              Description\n");
        sb.append("-------------------  ---------------------------------------------\n");
        sb.append("F <frame#>           Switch to the specified Frame number\n");
        sb.append("X <var#>             Expand (or contract) the specified variable number\n");
        sb.append("\n");
        sb.append("S                    Step over\n");
        sb.append("S+                   Step in\n");
        sb.append("S-                   Step out of frame\n");
        sb.append("SL                   Step (run) to current line\n");
        sb.append("R                    Run to next breakpoint\n");
        sb.append("\n");
        sb.append("B+                   Add breakpoint for the current line\n");
        sb.append("B-                   Remove breakpoint for the current line\n");
        sb.append("BT                   Toggle breakpoint for the current line\n");
        sb.append("B+ <name> <line>     Add specified breakpoint\n");
        sb.append("B- <name> <line>     Remove specified breakpoint\n");
        sb.append("BT <name> <line>     Toggle specified breakpoint\n");
        sb.append("BE+ <exception>      Break on exception\n");
        sb.append("BE- <exception>      Remove exception breakpoint\n");
        sb.append("BE+ *                Break on all exceptions\n");
        sb.append("BE- *                Remove the \"all exception\" breakpoint\n");
        sb.append("B- *                 Clear all breakpoints\n");
        sb.append("BT *                 Toggle all breakpoints (enable all iff all enabled; otherwise disable all)\n");
        sb.append("B                    List current breakpoints\n");
        sb.append("B- <breakpoint#>     Remove specified breakpoint (from the breakpoint list)\n");
        sb.append("BT <breakpoint#>     Toggle specified breakpoint (from the breakpoint list)\n");
        sb.append("\n");
        sb.append("VC                   View Console\n");
        sb.append("VD                   View Debugger\n");
        sb.append("VS <width> <height>  Set view width and optional height for debugger and console views\n");
        sb.append("?                    Display this help message");
        return sb.toString();
        }

    private static int numlen(int n)
        {
        return Integer.toString(n).length();
        }

    private static String ljust(String s, int cch)
        {
        int cOld    = s.length();
        int cSpaces = cch - cOld;
        assert cSpaces >= 0;
        return cSpaces > 0
                ? s + dup(' ', cSpaces)
                : s;
        }

    private static String rjust(String s, int cch)
        {
        int cOld    = s.length();
        int cSpaces = cch - cOld;
        assert cSpaces >= 0;
        if (cSpaces > 0)
            {
            StringBuilder sb = new StringBuilder(cch);
            for (int i = 0; i < cSpaces; ++i)
                {
                sb.append(' ');
                }
            s = sb.append(s).toString();
            }
        return s;
        }

    private static String dup(char ch, int cch)
        {
        StringBuilder sb = new StringBuilder(cch);
        for (int i = 0; i < cch; ++i)
            {
            sb.append(ch);
            }
        return sb.toString();
        }


    // ----- inner class: BreakPoint ---------------------------------------------------------------

    static class BreakPoint
            implements Comparable<BreakPoint>
        {
        public BreakPoint(String sName, int nLine)
            {
            className  = sName;
            lineNumber = nLine;
            }

        public BreakPoint(String sException)
            {
            className  = sException;
            lineNumber = -1;
            }

        public boolean isEnabled()
            {
            return !disabled;
            }

        public void enable()
            {
            disabled = false;
            }

        public void disable()
            {
            disabled = true;
            }

        public boolean isException()
            {
            return lineNumber < 0;
            }

        public boolean matches(Frame frame, int iPC)
            {
            if (!isEnabled() || isException())
                {
                return false;
                }

            MethodStructure method = frame.f_function;
            return className.equals(method.getContainingClass().getName()) &&
                   lineNumber == method.calculateLineNumber(iPC);
            }

        public boolean matches(ExceptionHandle hE)
            {
            if (!isEnabled() || !isException())
                {
                return false;
                }

            if (className.equals("*"))
                {
                return true;
                }

            IdentityConstant idException = (IdentityConstant) hE.getType().getDefiningConstant();
            return lineNumber == -1 && className.equals(idException.getName());
            }

        @Override
        public boolean equals(Object o)
            {
            if (!(o instanceof BreakPoint))
                {
                return false;
                }
            BreakPoint that = (BreakPoint) o;
            return lineNumber == that.lineNumber &&
                   className.equals(that.className);
            }

        @Override
        public int hashCode()
            {
            return className.hashCode() ^ lineNumber;
            }

        @Override
        public int compareTo(BreakPoint that)
            {
            if (that == null)
                {
                return 1;
                }

            if (this == that || this.equals(that))
                {
                return 0;
                }

            if (this.isException() != that.isException())
                {
                return this.isException() ? -1 : 1;
                }

            if (this.className.equals("*"))
                {
                return -1;
                }
            if (that.className.equals("*"))
                {
                return 1;
                }

            if (this.className.equals(that.className))
                {
                return this.lineNumber - that.lineNumber;
                }
            else
                {
                return this.className.compareTo(that.className);
                }
            }

        @Override
        public String toString()
            {
            String s = lineNumber < 0
                    ? className.equals("*")
                            ? "On ALL exceptions"
                            : "On exception: " + className
                    : "At " + className + ':' + lineNumber;

            if (disabled)
                {
                s += " (disabled)";
                }

            return s;
            }

        public String  className;  // "*" means all exceptions
        public int     lineNumber; // -1 for exceptions
        public boolean disabled;
        }


    // ----- constants and data fields -------------------------------------------------------------

    public static final DebugConsole INSTANCE = new DebugConsole();

    /**
     * Current view mode.
     * 0=frames & variables
     * 1=console
     */
    private int m_nViewMode = 0;

    /**
     * The order that the frames were last listed in.
     */
    private Frame.StackFrame[] m_aFrames;

    /**
     * The current frame.
     */
    private Frame m_frame;

    /**
     * The selected frame (for showing variables).
     */
    private Frame m_frameFocus;

    /**
     * The displayed list of variables.
     */
    private VarInfo[] m_aVars;

    /**
     * The last debugged iPC.
     */
    private int m_iPC;

    /**
     * The debugger screen height (the limit for printing console output)..
     */
    private int m_cHeight = 30;

    /**
     * The debugger screen width.
     */
    private int m_cWidth = 120;

    /**
     * The set of breakpoints on line numbers.
     */
    private Set<BreakPoint> m_setLineBreaks;

    /**
     * The set of breakpoints on exceptions.
     */
    private Set<BreakPoint> m_setThrowBreaks;

    /**
     * True if the debugger should break on any throw.
     */
    private boolean m_fBreakOnAllThrows;

    /**
     * The order that the breakpoints were last listed in.
     */
    private BreakPoint[] m_aBreaks;

    /**
     * The current step operation.
     */
    enum StepMode {None, StepOver, StepInto, StepOut, StepLine}
    private StepMode m_stepMode = StepMode.None;
    }
