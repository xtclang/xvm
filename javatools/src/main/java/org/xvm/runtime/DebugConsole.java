package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.HashSet;
import java.util.Set;

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
        boolean    fDebug = false;
        switch (m_stepMode)
            {
            case StepOver:
                // over on Return could step out
                fDebug = frame.f_iId <= m_frame.f_iId;
                break;

            case StepOut:
                fDebug = frame.f_iId < m_frame.f_iId;
                break;

            case StepInto:
                fDebug = true;
                break;

            case StepLine:
                fDebug = frame.f_iId == m_frame.f_iId && iPC == m_iPC;
                break;

            case None:
                fDebug = m_setBP.stream().anyMatch(bp -> bp.matches(frame, iPC));
                break;
            }

        return fDebug
                ? enterCommand(frame, iPC)
                : iPC + 1;
        }

    @Override
    public void checkBreakPoint(Frame frame, ExceptionHandle hEx)
        {
        if (m_setBP.stream().anyMatch(bp -> bp.matches(hEx)))
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
                writer.print("\nEnter command:");
                writer.flush();

                String sCommand = reader.readLine();
                if (sCommand == null)
                    {
                    // we don't have a console; ignore
                    writer.println();
                    return iPC + 1;
                    }

                String[] asParts  = Handy.parseDelimitedString(sCommand, ' ');
                int      cParts   = asParts.length - 1;
                if (cParts < 0)
                    {
                    continue;
                    }
                switch (asParts[0])
                    {
                    case "B": case "b":
                        writer.println(m_setBP);
                        continue NextCommand;

                    case "B+": case "b+":
                        switch (cParts)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    m_setBP.add(makeBreakPoint(frame, iPC));
                                    continue NextCommand;
                                    }
                                break; // the command is not allowed

                            case 2:
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                                if (bp != null)
                                    {
                                    m_setBP.add(bp);
                                    continue NextCommand;
                                    }
                                break;  // invalid break point
                            }
                        break;

                    case "B-": case "b-":
                        switch (cParts)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    m_setBP.remove(makeBreakPoint(frame, iPC));
                                    continue NextCommand;
                                    }
                                break; // the command is not allowed

                            case 1:
                                if (asParts[1].equals("*"))
                                    {
                                    m_setBP.clear();
                                    continue NextCommand;
                                    }
                                break; // invalid command

                            case 2:
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2]);
                                if (bp != null)
                                    {
                                    m_setBP.remove(bp);
                                    continue NextCommand;
                                    }
                                break; // invalid break point
                            }
                        break;

                    case "BE+": case "be+":
                        if (cParts == 1)
                            {
                            m_setBP.add(new BreakPoint(asParts[1]));
                            continue NextCommand;
                            }
                        break; // invalid command

                    case "BE-": case "be-":
                        if (cParts == 1)
                            {
                            m_setBP.remove(new BreakPoint(asParts[1]));
                            continue NextCommand;
                            }
                        break; // invalid command

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

                    case "S": case "s":
                        m_stepMode = StepMode.StepOver;
                        break NextCommand;

                    case "S+": case "s+":
                        m_stepMode = StepMode.StepInto;
                        break NextCommand;

                    case "S-": case "s-":
                        m_stepMode = StepMode.StepOut;
                        break NextCommand;

                    case "SL": case "sl":
                        m_stepMode = StepMode.StepLine;
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

        public BreakPoint(String sException)
            {
            className  = sException;
            lineNumber = -1;
            }

        public boolean matches(Frame frame, int iPC)
            {
            MethodStructure method = frame.f_function;
            return className.equals(method.getContainingClass().getName()) &&
                   lineNumber == method.calculateLineNumber(iPC);
            }

        public boolean matches(ExceptionHandle hE)
            {
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
        public String toString()
            {
            return lineNumber == -1
                ? "BreakPoint{exceptionName='" + className + '}'
                : "BreakPoint{className='"     + className + '\'' +
                           ", lineNumber="     + lineNumber + '}';
            }

        public String className;
        public int    lineNumber; // -1 for exceptions
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
     * The set of breakpoints.
     */
    private final Set<BreakPoint> m_setBP = new HashSet<>();

    /**
     * The current step operation.
     */
    enum StepMode {None, StepOver, StepInto, StepOut, StepLine}
    private StepMode m_stepMode = StepMode.None;
    }
