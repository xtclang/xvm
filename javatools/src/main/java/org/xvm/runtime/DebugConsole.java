package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import java.util.prefs.Preferences;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;

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
        loadBreakpoints();
        }

    @Override
    public void activate(ServiceContext ctx)
        {
        ctx.setDebuggerActive(true);

        // stop at the first possibility
        m_stepMode = StepMode.StepInto;
        }

    @Override
    public int checkBreakPoint(Frame frame, int iPC)
        {
        boolean fDebug;
        switch (m_stepMode)
            {
            case NaturalCall:
                fDebug = false;
                break;

            case StepOver:
                fDebug = frame == m_frame;
                break;

            case StepOut:
                // handled by onReturn()
                fDebug = false;
                break;

            case StepInto:
                fDebug = true;
                break;

            case StepLine:
                fDebug = frame == m_frame && iPC == m_iPC;
                break;

            case None:
                fDebug = m_setLineBreaks != null && m_setLineBreaks.stream().anyMatch(bp -> bp.matches(frame, iPC));
                break;

            default:
                throw new IllegalStateException();
            }

        return fDebug
                ? enterCommand(frame, iPC, true)
                : iPC + 1;
        }

    @Override
    public int checkBreakPoint(Frame frame, ExceptionHandle hEx)
        {
        if (m_stepMode == StepMode.NaturalCall)
            {
            // exception by the natural code called from the debugger
            if (frame == m_frame)
                {
                xTerminalConsole.CONSOLE_OUT.println("Call \"toString()\" threw an exception " + hEx);
                frame.m_hException = null;
                return enterCommand(frame, m_iPC, false);
                }
            }
        else if (m_fBreakOnAllThrows ||
                 m_setThrowBreaks != null && m_setThrowBreaks.stream().anyMatch(bp -> bp.matches(hEx)))
            {
            enterCommand(frame, -1, true);
            }

        return Op.R_EXCEPTION;
        }

    @Override
    public void onReturn(Frame frame)
        {
        if (frame == m_frame)
            {
            switch (m_stepMode)
                {
                case StepOver:
                case StepOut:
                    // we're exiting the frame; stop at the first chance
                    m_stepMode = StepMode.StepInto;
                    break;
                }
            }
        }

    /**
     * Allow interactive debugger commands.
     *
     * @param frame    the current frame
     * @param iPC      the current PC (-1 for an exception breakpoint)
     * @param fRender  specifies whether to render the console initially
     *
     * @return the next PC or any of the Op.R_* values
     */
    private int enterCommand(Frame frame, int iPC, boolean fRender)
        {
        m_frame    = frame;
        m_iPC      = iPC;
        m_stepMode = StepMode.None;

        PrintWriter    writer = xTerminalConsole.CONSOLE_OUT;
        BufferedReader reader = xTerminalConsole.CONSOLE_IN;

        if (fRender)
            {
            writer.println(renderDisplay());
            }

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
                    sCommand = "VD";
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
                                // "B-" remove the current line from the list of breakpoints
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
                                    saveBreakpoints();
                                    continue NextCommand;
                                    }
                                // "B- 3"  (# is from the previously displayed list of breakpoints)
                                else if (m_aBreaks != null)
                                    {
                                    int n = parseNonNegative(asParts[1]);
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
                                        saveBreakpoints();
                                        }
                                    else
                                        {
                                        Arrays.stream(aBP).forEach(BreakPoint::enable);
                                        m_fBreakOnAllThrows =
                                            Arrays.stream(aBP).anyMatch(bp -> bp.className.equals("*"));
                                        saveBreakpoints();
                                        }
                                    continue NextCommand;
                                    }
                                // "BT 3"  (# is from the previously displayed list of breakpoints)
                                else if (m_aBreaks != null)
                                    {
                                    int n = parseNonNegative(asParts[1]);
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
                        if (cArgs == 0)
                            {
                            writer.println(renderDebugger());
                            continue NextCommand;
                            }

                        if (cArgs == 1)
                            {
                            Frame.StackFrame[] aFrames = m_aFrames;
                            int                cFrames = aFrames == null ? 0 : aFrames.length;
                            int                iFrame  = parseNonNegative(asParts[1]);
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
                                    prefs.putInt("screen-height", n);
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
                                    prefs.putInt("screen-width", n);
                                    }
                                else
                                    {
                                    writer.println("Illegal text width: " + asParts[1]);
                                    }
                                continue NextCommand;
                                }
                            }
                        break;

                    case "D":
                        if (cArgs >= 1)
                            {
                            String[] asVars = m_asVars;
                            int iVar = parseNonNegative(asParts[1]);
                            if (iVar >= 0 && iVar < asVars.length)
                                {
                                ObjectHandle hVar = getVar(asVars[iVar]);
                                if (cArgs >= 2)
                                    {
                                    String sProp = asParts[2];
                                    try
                                        {
                                        hVar = ((GenericHandle) hVar).getField(sProp);
                                        }
                                    catch (Throwable e)
                                        {
                                        writer.println("Invalid property: " + sProp);
                                        continue NextCommand;
                                        }
                                    }

                                StringBuilder sb = new StringBuilder();

                                renderVar(hVar, false, sb, "   +");
                                writer.println(sb);
                                continue NextCommand;
                                }
                            }
                        break;

                    case "DS":
                        if (cArgs >= 1)
                            {
                            String[] asVars = m_asVars;
                            int iVar = parseNonNegative(asParts[1]);
                            if (iVar >= 0 && iVar < asVars.length)
                                {
                                ObjectHandle hVar = getVar(asVars[iVar]);
                                if (hVar == null)
                                    {
                                    writer.println("<unassigned>");
                                    }
                                else
                                    {
                                    if (cArgs >= 2)
                                        {
                                        String sProp = asParts[2];
                                        try
                                            {
                                            hVar = ((GenericHandle) hVar).getField(sProp);
                                            }
                                        catch (Throwable e)
                                            {
                                            writer.println("Invalid property: " + sProp);
                                            continue NextCommand;
                                            }
                                        }

                                    if (callToString(frame, hVar, writer))
                                        {
                                        return Op.R_CALL;
                                        }
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

        frame.f_context.setDebuggerActive(
            m_setLineBreaks != null || m_setThrowBreaks != null || m_stepMode != StepMode.None);
        return iPC + 1;
        }

    /**
     * Call toString() helper.
     */
    private boolean callToString(Frame frame, ObjectHandle hVar, PrintWriter writer)
        {
        switch (Utils.callToString(frame, hVar))
            {
            case Op.R_NEXT:
                writer.println(((StringHandle) frame.popStack()).getStringValue());
                m_stepMode = StepMode.None;
                return false;

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    writer.println(((StringHandle) frameCaller.popStack()).getStringValue());
                    return enterCommand(frameCaller, m_iPC, false);
                    });
                m_stepMode = StepMode.NaturalCall;
                return true;

            default:
                throw new IllegalStateException();
            }
        }

    private BreakPoint makeBreakPoint(Frame frame, int iPC)
        {
        MethodStructure method = frame.f_function;
        String          sName  = method.getContainingClass().getName();
        int             nLine  = method.calculateLineNumber(iPC);

        return new BreakPoint(sName, nLine);
        }

    private void addBP(BreakPoint bp)
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

        saveBreakpoints();
        }

    private void removeBP(BreakPoint bp)
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

        saveBreakpoints();
        }

    private void toggleBP(BreakPoint bp)
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

        saveBreakpoints();
        }

    private BreakPoint findBP(BreakPoint bp)
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

    private void loadBreakpoints()
        {
        m_setLineBreaks     = stringToBreakpoints(prefs.get("break-points", ""));
        m_setThrowBreaks    = stringToBreakpoints(prefs.get("break-throws", ""));
        m_fBreakOnAllThrows = prefs.getBoolean("break-any-exception", false);
        }

    private void saveBreakpoints()
        {
        prefs.put("break-points", breakpointsToString(m_setLineBreaks));
        prefs.put("break-throws", breakpointsToString(m_setThrowBreaks));
        prefs.putBoolean("break-any-exception", m_fBreakOnAllThrows);
        }

    private Set<BreakPoint> stringToBreakpoints(String s)
        {
        if (s == null)
            {
            return null;
            }

        s = s.trim();
        if (s.length() == 0)
            {
            return null;
            }

        Set<BreakPoint> setBP = new HashSet<>();
        for (String sbp : Handy.parseDelimitedString(s, ','))
            {
            try
                {
                String[] settings = Handy.parseDelimitedString(s, ':');
                String   sName   = settings[0];
                int      nLine   = settings.length >= 2 && settings[1].length() > 0
                        ? Integer.parseInt(settings[1])
                        : -1;
                BreakPoint bp = nLine >= 0
                        ? new BreakPoint(sName, nLine)      // line #
                        : new BreakPoint(sName);            // exception
                if (settings.length >= 3 && settings[2].equals("off"))
                    {
                    bp.disable();
                    }
                setBP.add(bp);
                }
            catch (Exception e)
                {
                System.err.println("Exception parsing breakpoint \"" + sbp + "\": " + e);
                }
            }
        return setBP;
        }

    private String breakpointsToString(Set<BreakPoint> set)
        {
        if (set == null || set.isEmpty())
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        boolean fFirst = true;
        for (BreakPoint bp : set)
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(',');
                }
            sb.append(bp.toPrefString());
            }
        return sb.toString();
        }


    // ----- rendering -----------------------------------------------------------------------------

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
        if (cchFrames + cchVars + 3 > m_cWidth)
            {
            int max = m_cWidth - 3;
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
          .append(" | ")
          .append(sVHeader)
          .append('\n')
          .append(dup('-', cchFrames))
          .append("-|-")
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

            sb.append(" | ");

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
        Frame frame = m_frameFocus;
        if (frame.isNative())
            {
            return Handy.NO_ARGS;
            }

        int               cVars    = frame.f_anNextVar[frame.m_iScope];
        ArrayList<String> listVars = new ArrayList<>(cVars + 1);
        ArrayList<String> listVals = new ArrayList<>(cVars + 1);
        int               cchVarNum;

        int          index = 0;
        ObjectHandle hThis = frame.f_hThis;
        if (hThis == null)
            {
            cchVarNum = numlen(cVars-1);
            }
        else
            {
            cchVarNum = numlen(cVars);
            addVal(listVars, listVals, "this", hThis, index++, cMax, cchVarNum);
            }

        for (int i = 0; i < cVars; i++)
            {
            VarInfo info = frame.getVarInfo(i);
            String  sVar = info == null ? "" : info.getName();
            if (!sVar.isEmpty())
                {
                addVal(listVars, listVals, sVar, frame.f_ahVar[i], index++, cMax, cchVarNum);
                }
            }
        m_asVars = listVars.toArray(Handy.NO_ARGS);
        return listVals.toArray(Handy.NO_ARGS);
        }

    private void addVal(ArrayList<String> listVars, ArrayList<String> listVals,
                        String sVar, ObjectHandle hValue, int iVar,
                        int cMax, int cchVarNum)
        {
        String sDescr = hValue == null ? "<not assigned>" : hValue.toString();
        if (sDescr.indexOf('\n') >= 0)
            {
            sDescr = sDescr.replace('\n', ' ');
            }

        int cDescrMax = cMax - cchVarNum - sVar.length() - 3;
        if (sDescr.length() > cDescrMax)
            {
            sDescr = sDescr.substring(0, cDescrMax - 3) + "...";
            }

        listVars.add(sVar);
        listVals.add(
            rjust(Integer.toString(iVar), cchVarNum) +
            "  " + sVar + '=' + sDescr
            );
        }

    private ObjectHandle getVar(String sName)
        {
        Frame frame = m_frameFocus;
        if (sName.equals("this"))
            {
            return frame.f_hThis;
            }

        int cVars = frame.f_anNextVar[frame.m_iScope];
        for (int i = 0; i < cVars; i++)
            {
            VarInfo info = frame.getVarInfo(i);
            if (info != null && info.getName().equals(sName))
                {
                return frame.f_ahVar[i];
                }
            }
        return null;
        }

    private void renderVar(ObjectHandle hVal, boolean fField, StringBuilder sb, String sTab)
        {
        if (hVal == null)
            {
            if (fField)
                {
                sb.append('=');
                }
            sb.append("<unassigned>");
            return;
            }

        // composition could be null for deferred values (e.g. <default>)
        TypeComposition composition = hVal.getComposition();
        List<String>    listNames   = composition == null
                ? Collections.EMPTY_LIST
                : composition.getFieldNames();
        if (listNames.isEmpty())
            {
            if (fField)
                {
                sb.append('=');
                }

            if (hVal instanceof ArrayHandle)
                {
                ArrayHandle hArray = (ArrayHandle) hVal;
                // TODO GG show the array values
                }
            sb.append(hVal);
            }
        else
            {
            if (fField)
                {
                sb.append(": ");
                }
            sb.append(hVal.getType().getValueString());

            for (String sField : listNames)
                {
                ObjectHandle hField = ((GenericHandle) hVal).getField(sField);

                sb.append('\n')
                  .append(sTab)
                  .append(sField);
                renderVar(hField, true, sb, sTab + "   ");
                }
            }
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
            setBP.addAll(m_setLineBreaks);
            }
        if (m_setThrowBreaks != null)
            {
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
        sb.append("E <expr>             Evaluate the specified expression\n");
        sb.append("W <expr>             Add a \"watch\" for the specified expression\n");
        sb.append("W- <var#>            Remove the specified watch\n");
        sb.append("D <var#>             Display the structure view of the specified variable number\n");
        sb.append("DS <var#>            Display the \"toString()\" value of the specified variable number\n");
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


    // ----- helpers -------------------------------------------------------------------------------

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

        String toPrefString()
            {
            return (lineNumber < 0)
                    ? disabled
                            ? className + "::off"
                            : className
                    : disabled
                            ? className + ":" + lineNumber + ":off"
                            : className + ":" + lineNumber;
            }

        public String  className;  // "*" means all exceptions
        public int     lineNumber; // -1 for exceptions
        public boolean disabled;
        }


    // ----- constants and data fields -------------------------------------------------------------

    public static final DebugConsole INSTANCE = new DebugConsole();

    /**
     * Persistent preference storage.
     */
    Preferences prefs = Preferences.userNodeForPackage(DebugConsole.class);

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
     * The displayed array of variable names.
     */
    private String[] m_asVars;

    /**
     * The last debugged iPC.
     */
    private int m_iPC;

    /**
     * The debugger screen height (the limit for printing console output)..
     */
    private int m_cHeight = prefs.getInt("screen-height", 30);

    /**
     * The debugger screen width.
     */
    private int m_cWidth = prefs.getInt("screen-width", 120);

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
    enum StepMode {None, StepOver, StepInto, StepOut, StepLine, NaturalCall}
    private StepMode m_stepMode = StepMode.None;
    }
