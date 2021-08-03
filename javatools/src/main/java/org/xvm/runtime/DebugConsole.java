package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Frame.VarInfo;

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
        return enterCommand(frame, iPC, null);
        }

    @Override
    public int checkBreakPoint(Frame frame, int iPC)
        {
        boolean    fDebug = false;
        BreakPoint bp     = null;
        switch (m_stepMode)
            {
            case StepOver:
                // over on Return could step out
                fDebug = frame == m_frame || frame == m_frame.f_framePrev;
                break;

            case StepOut:
                fDebug = frame == m_frame;
                break;

            case StepInto:
                fDebug = true;
                break;

            case None:
                bp     = makeBreakPoint(frame, iPC);
                fDebug = m_setBP.contains(bp);
                break;
            }

        return fDebug
                ? enterCommand(frame, iPC, bp)
                : iPC + 1;
        }

    private int enterCommand(Frame frame, int iPC, BreakPoint bpCurrent)
        {
        m_frame    = frame;
        m_stepMode = StepMode.None;

        PrintWriter    writer = xTerminalConsole.CONSOLE_OUT;
        BufferedReader reader = xTerminalConsole.CONSOLE_IN;

        writer.println(reportFrame());

        NextCommand:
        while (true)
            {
            try
                {
                writer.print("\nEnter command:");
                writer.flush();

                String   sCommand = reader.readLine();
                String[] asParts  = Handy.parseDelimitedString(sCommand, ' ');
                int      cParts   = asParts.length - 1;

                switch (asParts[0])
                    {
                    case "B": case "b":
                        writer.println(m_setBP);
                        continue NextCommand;

                    case "B+": case "b+":
                        if (cParts == 0)
                            {
                            if (bpCurrent == null)
                                {
                                m_setBP.add(makeBreakPoint(frame, iPC));
                                continue NextCommand;
                                }
                            }
                        else if (cParts == 2)
                            {
                            BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                            if (bp != null)
                                {
                                m_setBP.add(bp);
                                continue NextCommand;
                                }
                            }
                        break;

                    case "B-": case "b-":
                        if (cParts == 0)
                            {
                            if (bpCurrent != null)
                                {
                                m_setBP.remove(bpCurrent);
                                continue NextCommand;
                                }
                            }
                        else if (cParts == 2)
                            {
                            BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                            if (bp != null)
                                {
                                m_setBP.remove(bp);
                                continue NextCommand;
                                }
                            }
                        break;

                    case "BE": case "be":
                    case "BT": case "bt":
                        throw new UnsupportedOperationException();

                    case "F": case "f":
                        if (cParts == 1)
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

                    case "H": case "h": case "?":
                        writer.println("commands are: TODO");
                        continue NextCommand;

                    case "R": case "r":
                        break NextCommand;

                    case "S": case "s": // step over
                        m_stepMode = StepMode.StepOver;
                        break NextCommand;

                    case "S+": case "s+": // step in
                        m_stepMode = StepMode.StepInto;
                        break NextCommand;

                    case "S-": case "s-": // step out
                        m_stepMode = StepMode.StepOut;
                        m_frame    = frame.f_framePrev;
                        break NextCommand;

                    default:
                        writer.println("unknown command: " + sCommand + "; enter '?' for help");
                        continue NextCommand;
                    }

                writer.println("invalid command: " + sCommand);
                }
            catch (IOException e) {}
            }

        frame.f_context.setDebuggerActive(!m_setBP.isEmpty() || m_stepMode != StepMode.None);
        return iPC + 1;
        }

    private BreakPoint makeBreakPoint(Frame frame, int iPC)
        {
        MethodStructure method = frame.f_function;
        String          sName  = method.getContainingClass().getName();
        int             nLine  = method.calculateLineNumber(iPC);

        return new BreakPoint(sName, nLine);
        }

    private BreakPoint parseBreakPoint(String sName, String sLine)
        {
        int nLine = parseNonNegative(sLine);

        return nLine <= 0 ? null : new BreakPoint(sName, nLine);
        }

    private int parseNonNegative(String sNumber)
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

    static class BreakPoint
        {
        public BreakPoint(String sName, int nLine)
            {
            className  = sName;
            lineNumber = nLine;
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
        public String toString()
            {
            return "BreakPoint{" +
                "className='" + className + '\'' +
                ", lineNumber=" + lineNumber +
                '}';
            }

        public String className;
        public int    lineNumber;
        }

    // ----- constants and data fields -------------------------------------------------------------

    public static final DebugConsole INSTANCE = new DebugConsole();

    /**
     * The current frame.
     */
    private Frame m_frame;

    /**
     * The set of breakpoints.
     */
    private final Set<BreakPoint> m_setBP = new HashSet<>();

    /**
     * The current step operation.
     */
    enum StepMode {None, StepOver, StepInto, StepOut}
    private StepMode m_stepMode = StepMode.None;
    }
