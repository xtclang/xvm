package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

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

        writer.println(reportFrame());

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
                        if (cArgs == 1)
                            {
                            int nFrame = parseNonNegative(asParts[1]);
                            if (nFrame > 0)
                                {
                                for (int i = nFrame; i > 0; i--)
                                    {
                                    frame = frame.f_framePrev;
                                    if (frame == null)
                                        {
                                        break; // invalid frame number
                                        }
                                    }
                                m_frame = frame;
                                }
                            writer.println(reportFrame());
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
                        writer.println("Console:");
                        writer.println(xTerminalConsole.CONSOLE_LOG.render(m_cWidth, m_cHeight-1));
                        continue NextCommand;

                    case "VD":
                        // TODO
                        writer.println("TODO view debugger");
                        continue NextCommand;

                    case "VW":
                        switch (cArgs)
                            {
                            case 0:
                                writer.println("Current debugger text width: " + m_cWidth + " characters.");
                                continue NextCommand;

                            case 1:
                                if (asParts[1].chars().allMatch(n -> n >= '0' && n <= '9'))
                                    {
                                    int n = Integer.parseInt(asParts[1]);
                                    if (n >= 10 && n < 1000)
                                        {
                                        writer.println("Altering debugger text width from " + m_cWidth + " to " + n + " characters.");
                                        m_cWidth = n;
                                        }
                                    else
                                        {
                                        writer.println("Illegal text width: " + n);
                                        }
                                    continue NextCommand;
                                    }
                                break;
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

    private String reportFrame()
        {
        Frame frame = m_frame;

        String[] asStack = frame.getStackTraceArray();

        StringBuilder sb = new StringBuilder();
// TODO
        sb.append("Frames: ------------------------------------\n");

        for (int i = 0, c = asStack.length; i < c; i++)
            {
            sb.append("\n[")
              .append(i)
              .append("] ")
              .append(asStack[i]);
            }

        sb.append("\n\nVariables: ---------------------------------\n");

        int cVars = frame.f_anNextVar[frame.m_iScope];
        for (int i = 0; i < cVars; i++)
            {
            VarInfo info = frame.getVarInfo(i);
            String  sVar = info.getName();
            if (!sVar.isEmpty())
                {
                ObjectHandle hValue = frame.f_ahVar[i];
                sb.append("\n")
                  .append(sVar)
                  .append(" = ")
                  .append(hValue == null ? "<not assigned>" : hValue.toString());
                }
            }
        return sb.toString();
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
        sb.append("Command            Description\n");
        sb.append("-----------------  ---------------------------------------------\n");
        sb.append("F <frame#>         Switch to the specified Frame number\n");
        sb.append("X <var#>           Expand (or contract) the specified variable number\n");
        sb.append("\n");
        sb.append("S                  Step over\n");
        sb.append("S+                 Step in\n");
        sb.append("S-                 Step out of frame\n");
        sb.append("SL                 Step (run) to current line\n");
        sb.append("R                  Run to next breakpoint\n");
        sb.append("\n");
        sb.append("B+                 Add breakpoint for the current line\n");
        sb.append("B-                 Remove breakpoint for the current line\n");
        sb.append("BT                 Toggle breakpoint for the current line\n");
        sb.append("B+ <name> <line>   Add specified breakpoint\n");
        sb.append("B- <name> <line>   Remove specified breakpoint\n");
        sb.append("BT <name> <line>   Toggle specified breakpoint\n");
        sb.append("BE+ <exception>    Break on exception\n");
        sb.append("BE- <exception>    Remove exception breakpoint\n");
        sb.append("BE+ *              Break on all exceptions\n");
        sb.append("BE- *              Remove the \"all exception\" breakpoint\n");
        sb.append("B- *               Clear all breakpoints\n");
        sb.append("BT *               Toggle all breakpoints (enable all iff all enabled; otherwise disable all)\n");
        sb.append("B                  List current breakpoints\n");
        sb.append("B- <breakpoint#>   Remove specified breakpoint (from the breakpoint list)\n");
        sb.append("BT <breakpoint#>   Toggle specified breakpoint (from the breakpoint list)\n");
        sb.append("\n");
        sb.append("VC                 View Console\n");
        sb.append("VD                 View Debugger\n");
        sb.append("VW <width>         Set view width for debugger and console views\n");
        sb.append("?                  Display this help message");
        return sb.toString();
        }

    private static int numlen(int n)
        {
        return Integer.toString(n).length();
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
     * The last debugged frame.
     */
    private Frame m_frame;

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
    private int m_cWidth = 100;

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
