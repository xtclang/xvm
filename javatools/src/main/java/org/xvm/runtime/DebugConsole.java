package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import static org.xvm.util.Handy.dup;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.NO_ARGS;


/**
 * Debugger console.
 *
 * TODO Eval and Watch Eval implementations
 * TODO show which variables changed when stepping
 */
public class DebugConsole
        implements Debugger
    {
    private DebugConsole()
        {
        loadBreakpoints();
        }

    @Override
    public void activate(Frame frame)
        {
        frame.f_context.setDebuggerActive(true);

        // stop at the first possibility
        m_frame    = frame;
        m_stepMode = StepMode.StepInto;
        }

    /**
     * For now, by making this method synchronized we stop all services while debugger is active.
     */
    @Override
    public synchronized int checkBreakPoint(Frame frame, int iPC)
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
                fDebug = m_frame.f_fiber.isAssociated(frame.f_fiber) ||
                           frame.f_fiber.isAssociated(m_frame.f_fiber);
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
            // keep following the exception
            return Op.R_NEXT;
            }

        if (m_fBreakOnAllThrows ||
                 m_setThrowBreaks != null && m_setThrowBreaks.stream().anyMatch(bp -> bp.matches(hEx)))
            {
            if (enterCommand(frame, Op.R_EXCEPTION, true) == Op.R_CALL)
                {
                // natural call by the debugger
                return Op.R_CALL;
                }
            if (m_stepMode != StepMode.None)
                {
                // one of the "step" commands, keep following the exception
                return Op.R_NEXT;
                }
            }

        // unwind the exception naturally without stopping in the debugger
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
                    return iPC >= 0 ? iPC + 1 : iPC;
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

                String[] asParts = parseDelimitedString(sCommand, ' ');
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
                                if (iPC >= 0)
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
                                 break; // invalid command

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
                                        m_fBreakOnAllThrows =
                                            Arrays.stream(aBP).anyMatch(bp -> bp.className.equals("*"));
                                        }
                                    saveBreakpoints();
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
                        int iFrame = cArgs == 0 ? 0 : parseNonNegative(asParts[1]);
                        Frame.StackFrame[] aFrames = m_aFrames;
                        int                cFrames = aFrames == null ? 0 : aFrames.length;
                        if (iFrame >= 0 && iFrame < cFrames)
                            {
                            m_frameFocus = aFrames[iFrame].frame;
                            }

                        writer.println(renderDebugger());
                        continue NextCommand;

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
                        m_viewMode = ViewMode.Console;
                        writer.println(renderConsole());
                        continue NextCommand;

                    case "VD":
                        m_viewMode = ViewMode.Frames;
                        writer.println(renderDebugger());
                        continue NextCommand;

                    case "VF":
                        m_viewMode = ViewMode.Services;
                        writer.println(renderServices());
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

                    case "X":
                        if (cArgs >= 1)
                            {
                            VarDisplay[] aVars    = m_aVars;
                            boolean      fRepaint = false;
                            for (int i = 0; i < cArgs; ++i)
                                {
                                int iVar = parseNonNegative(asParts[i+1]);
                                if (iVar >= 0 && iVar < aVars.length)
                                    {
                                    VarDisplay var = aVars[iVar];
                                    if (var.canExpand)
                                        {
                                        Map<String, Integer> mapExpand = ensureExpandMap(iVar);
                                        if (var.isArray)
                                            {
                                            if (var.name.equals("..."))
                                                {
                                                // find the parent array
                                                int arrayLevel = var.indent - 1;
                                                do
                                                    {
                                                    var = aVars[--iVar];
                                                    }
                                                while (var.indent > arrayLevel);

                                                int cShow = mapExpand.getOrDefault(var.path, 0);
                                                cShow = (cShow + 1) * 10;
                                                mapExpand.put(var.path, cShow);
                                                }
                                            else
                                                {
                                                mapExpand.put(var.path, var.expanded ? 0 : 10);
                                                }
                                            }
                                        else
                                            {
                                            mapExpand.put(var.path, var.expanded ? 0 : 1);
                                            }
                                        fRepaint = true;
                                        }
                                    else
                                        {
                                        writer.println("Cannot expand or contract \"" + var.name + '\"');
                                        }
                                    }

                                if (fRepaint)
                                    {
                                    writer.println(renderDebugger());
                                    }
                                continue NextCommand;
                                }
                            }
                        break;

                    case "V":
                        // TODO toggle between native vs. toString() (and possibly a third, type-specific format for some types)
                        writer.println("View format has not been implemented.");
                        continue NextCommand;

                    case "E":
                        // TODO eval <expr>
                        writer.println("Eval has not been implemented.");
                        continue NextCommand;

                    case "WE":
                        // TODO watch eval <expr>
                        writer.println("Watch Eval has not been implemented.");
                        continue NextCommand;

                    case "WO":
                        if (cArgs >= 1)
                            {
                            VarDisplay[] aVars    = m_aVars;
                            boolean      fRepaint = false;
                            for (int i = 0; i < cArgs; ++i)
                                {
                                int iVar = parseNonNegative(asParts[i+1]);
                                if (iVar >= 0 && iVar < aVars.length)
                                    {
                                    // verify that the referent is available
                                    VarDisplay var = aVars[iVar];
                                    if (var.hVar == null)
                                        {
                                        writer.println("Var #" + iVar + " (\"" + var.name + "\") does not have a referent to Watch");
                                        continue;
                                        }

                                    // verify that it is not already a watched object
                                    Watch watch = var.watch;
                                    if (watch != null && watch.form == Watch.OBJ)
                                        {
                                        writer.println("Var #" + iVar + " (\"" + var.name + "\") is already a Watched Object");
                                        continue;
                                        }

                                    // create the watch
                                    String sName = var.path;
                                    if (sName.startsWith("watch:"))
                                        {
                                        sName = sName.substring("watch:".length());
                                        }
                                    watch = new Watch("watch:" + sName, sName, var.hVar);
                                    getGlobalStash().ensureWatchList().add(watch);
                                    fRepaint = true;
                                    }
                                else
                                    {
                                    writer.println("Var #" + iVar + " does not exist");
                                    }
                                }

                            if (fRepaint)
                                {
                                writer.println(renderDebugger());
                                }
                            continue NextCommand;
                            }
                        break;

                    case "WR":
                        if (cArgs >= 1)
                            {
                            VarDisplay[] aVars    = m_aVars;
                            boolean      fRepaint = false;
                            for (int i = 0; i < cArgs; ++i)
                                {
                                int iVar = parseNonNegative(asParts[i+1]);
                                if (iVar >= 0 && iVar < aVars.length)
                                    {
                                    // verify that it is not already a watch
                                    VarDisplay var = aVars[iVar];
                                    Watch watch = var.watch;
                                    if (watch != null)
                                        {
                                        writer.println("Var #" + iVar + " (\"" + var.name + "\") is already a Watch");
                                        continue;
                                        }

                                    // figure out if it is a variable, property, or element
                                    boolean fGlobal;
                                    if (var.indent == 0)
                                        {
                                        // it's a variable (since it's not a watch)
                                        writer.println("Var #" + iVar + " (\"" + var.name + "\") is already displayed in the current frame");
                                        continue;
                                        }
                                    else if (var.name.startsWith("["))
                                        {
                                        // it's an array element
                                        VarDisplay parent = aVars[findParentNode(iVar)];
                                        int        index  = Integer.valueOf(var.name.substring(1, var.name.length()-1));
                                        watch   = new Watch("watch:" + var.path, var.path, parent.hVar, index);
                                        fGlobal = false;
                                        }
                                    else if (var.name.startsWith("."))
                                        {
                                        // it's "...", i.e. the unexpanded portion of an array
                                        writer.println("Var #" + iVar + " is not Watchable");
                                        continue;
                                        }
                                    else
                                        {
                                        // it must be a property
                                        VarDisplay parent = aVars[findParentNode(iVar)];
                                        watch   = new Watch("watch:" + var.path, var.path, parent.hVar, var.name);
                                        fGlobal = true;
                                        }

                                    (fGlobal ? getGlobalStash() : getFrameStash()).ensureWatchList().add(watch);
                                    fRepaint = true;
                                    }
                                else
                                    {
                                    writer.println("Var #" + iVar + " does not exist");
                                    }
                                }

                            if (fRepaint)
                                {
                                writer.println(renderDebugger());
                                }
                            continue NextCommand;
                            }
                        break;

                    case "W-":
                        if (cArgs >= 1)
                            {
                            VarDisplay[] aVars    = m_aVars;
                            boolean      fRepaint = false;
                            for (int i = 0; i < cArgs; ++i)
                                {
                                int iVar = parseNonNegative(asParts[i+1]);
                                if (iVar >= 0 && iVar < aVars.length)
                                    {
                                    // verify that the var is a watch
                                    VarDisplay var   = aVars[iVar];
                                    Watch      watch = var.watch;
                                    if (watch == null)
                                        {
                                        writer.println("Var #" + iVar + " (\"" + var.name + "\") is not a Watch");
                                        continue;
                                        }

                                    getFrameStash().ensureWatchList().remove(watch);
                                    getGlobalStash().ensureWatchList().remove(watch);
                                    fRepaint = true;
                                    }
                                else
                                    {
                                    writer.println("Var #" + iVar + " does not exist");
                                    }
                                }

                            if (fRepaint)
                                {
                                writer.println(renderDebugger());
                                }
                            continue NextCommand;
                            }
                        break;

                    case "D":
                        if (cArgs >= 1)
                            {
                            VarDisplay[] aVars = m_aVars;
                            int iVar = parseNonNegative(asParts[1]);
                            if (iVar >= 0 && iVar < aVars.length)
                                {
                                ObjectHandle hVar = aVars[iVar].hVar;
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
                            VarDisplay[] aVars = m_aVars;
                            int iVar = parseNonNegative(asParts[1]);
                            if (iVar >= 0 && iVar < aVars.length)
                                {
                                ObjectHandle hVar = aVars[iVar].hVar;
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
        return iPC >= 0 ? iPC + 1 : iPC;
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

    /**
     * @return the DebugState object for the currently displayed frame
     */
    private DebugStash getFrameStash()
        {
        return m_frameFocus.ensureDebugStash();
        }

    /**
     * @return the DebugState object shared across all frames
     */
    private DebugStash getGlobalStash()
        {
        return m_debugState;
        }

    /**
     * @param iVar  the var display index from the previous debugger rendering
     *
     * @return the display index for the "parent" var
     */
    private int findParentNode(int iVar)
        {
        // figure out if the debug info is local or global state
        VarDisplay var    = m_aVars[iVar];
        int        indent = var.indent;
        assert indent > 0;
        do
            {
            var = m_aVars[--iVar];
            }
        while (var.indent >= indent);
        return iVar;
        }

    /**
     * @param iVar  the var display index from the previous debugger rendering
     *
     * @return the expand/contract settings map for the specified var
     */
    private Map<String, Integer> ensureExpandMap(int iVar)
        {
        // figure out if the debug info is local or global state
        VarDisplay var = m_aVars[iVar];
        while (var.indent > 0)
            {
            var = m_aVars[--iVar];
            }
        return (var.watch != null && getGlobalStash().getWatchList().contains(var.watch)
            || var.path.equals("this")
                ? getGlobalStash()
                : getFrameStash() ).ensureExpandMap();
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
        for (String sbp : parseDelimitedString(s, ','))
            {
            try
                {
                String[] settings = parseDelimitedString(sbp, ':');
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
        switch (m_viewMode)
            {
            case Frames: return renderDebugger();
            case Console: return renderConsole();
            case Services: return renderServices();
            default: return "unknown view mode " + m_viewMode;
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
        StringBuilder sb = new StringBuilder();

        String   sFHeader  = "Call stack frames:";
        String[] asFrames  = renderFrames();
        int      cchFrames = Math.max(longestOf(asFrames), sFHeader.length());

        String   sVHeader  = "Variables and watches:";
        String[] asVars    = renderVars();
        int      cchVars   = Math.max(longestOf(asVars), sVHeader.length());
        int      cMax      = m_cWidth - cchFrames - 2;

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

        if (m_cHeight > 0 && m_frameFocus != null && m_frameFocus.f_function != null)
            {
            MethodStructure method   = m_frameFocus.f_function;
            boolean         fPrinted = false;

            int iFirst = 0;
            int cLines = 0;
            int nLine  = method.calculateLineNumber(m_frameFocus.m_iPC); // 1-based
            if (nLine > 0)
                {
                // default to showing the entire method
                iFirst = method.getSourceLineNumber();
                cLines = method.getSourceLineCount();
                if (cLines > m_cHeight)
                    {
                    // need to show just a portion; nLine is the current (1-based) line of code that
                    // the execution is at, so we want to show it in the middle
                    int iDesiredFirst = nLine - (m_cHeight / 2);
                    if (iDesiredFirst > iFirst)
                        {
                        int iLast         = iFirst + cLines - 1;
                        int iDesiredLast  = iDesiredFirst + m_cHeight - 1;
                        if (iDesiredLast > iLast)
                            {
                            iFirst = iLast - m_cHeight + 1;
                            }
                        else
                            {
                            iFirst = iDesiredFirst;
                            }
                        }
                    cLines = m_cHeight;
                    }

                String[] asLine = method.getSourceLines(iFirst, cLines, true);
                if (asLine != null)
                    {
                        cLines  = asLine.length;
                    int cchNum  = numlen(iFirst + cLines);
                    int cchLine = m_cWidth - cchNum - 2;

                    for (int iLine = 0; iLine < cLines; ++iLine)
                        {
                        int nLineNumber = iFirst + iLine + 1;
                        sb.append(nLineNumber == nLine ? '>' : ' ')     // TODO show breakpoints as well
                          .append(rjust(String.valueOf(nLineNumber), cchNum));

                        String sLine = asLine[iLine];
                        if (sLine != null)
                            {
                            if (sLine.length() > cchLine)
                                {
                                sLine = sLine.substring(0, cchLine);
                                }

                            sb.append(' ')
                              .append(sLine);
                            }

                        sb.append('\n');
                        }

                    fPrinted = true;
                    }
                }

            if (!fPrinted)
                {
                // TODO show assembly instead?
                sb.append("(No source available)");
                }

            sb.append(dup('-', m_cWidth))
              .append('\n');
            }

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
                    if (sVar.length() >= cMax)
                        {
                        sVar = sVar.substring(0, cMax - 4) + "...";
                        }
                    sVar = sVar.replace('\n', ' ');
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
        Frame frameFocus = m_frameFocus == null ? m_frame : m_frameFocus;
        Frame frameTop   = m_viewMode == ViewMode.Services ? frameFocus : m_frame;

        Frame.StackFrame[] aFrames = frameTop.getStackFrameArray();
        Frame frameNewTop =   aFrames == null ||   aFrames.length < 1 ? null :   aFrames[0].frame;
        Frame frameOldTop = m_aFrames == null || m_aFrames.length < 1 ? null : m_aFrames[0].frame;
        m_aFrames = aFrames;

        if (m_frameFocus == null || frameOldTop != frameNewTop)
            {
            m_frameFocus = frameFocus = frameNewTop;
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

    private String[] renderVars()
        {
        ArrayList<VarDisplay> listVars = new ArrayList<>();
        Map<String, Integer>  mapExpand;

        boolean fAnyVars = false;
        int     iPass    = 0;
        do
            {
            DebugStash stash = iPass == 0 ? getGlobalStash() : getFrameStash();
            mapExpand = stash.getExpandMap();
            for (Watch watch : stash.getWatchList())
                {
                ObjectHandle hVar = watch.produceHandle(m_frame);
                addVar(0, watch.path, watch.name, hVar, listVars, mapExpand).watch = watch;
                fAnyVars = true;
                }
            }
        while (++iPass < 2);

        Frame frame = m_frameFocus;
        if (frame.isNative() && !fAnyVars)
            {
            return NO_ARGS;
            }

        ExceptionHandle hException = frame.m_hException;
        if (hException != null)
            {
            addVar(0, "throw", "exception", hException, listVars, mapExpand);
            }

        ObjectHandle hThis = frame.f_hThis;
        if (hThis != null)
            {
            addVar(0, "this", "this", hThis, listVars, getGlobalStash().getExpandMap());

            if (hThis.getComposition().getFieldPosition(GenericHandle.OUTER) != -1)
                {
                ObjectHandle hOuter = ((GenericHandle) hThis).getField(GenericHandle.OUTER);
                addVar(0, "outer", "outer", hOuter, listVars, mapExpand);
                }
            }

        int cVars = frame.f_anNextVar == null ? 0 : frame.f_anNextVar[frame.m_iScope];
        for (int i = 0; i < cVars; i++)
            {
            VarInfo info = frame.getVarInfo(i);
            String  sVar = info == null ? "" : info.getName();
            if (!sVar.isEmpty())
                {
                addVar(0, sVar, sVar, frame.f_ahVar[i], listVars, mapExpand);
                }
            }

        cVars   = listVars.size();
        m_aVars = listVars.toArray(new VarDisplay[0]);
        String[] asVars    = new String[cVars];
        int      cchVarNum = numlen(listVars.size());
        for (int i = 0; i < cVars; ++i)
            {
            asVars[i] = m_aVars[i].render(i, cchVarNum);
            }
        return asVars;
        }

    private VarDisplay addVar(int cIndent, String sPath, String sVar, ObjectHandle hVar,
                        ArrayList<VarDisplay> listVars, Map<String, Integer> mapExpand)
        {
        boolean      fCanExpand = false;
        boolean      fArray     = false;
        long         cElements  = 0;
        List<String> listFields = Collections.EMPTY_LIST;
        if (hVar != null)
            {
            TypeComposition composition = hVar.getComposition();
            if (composition != null)
                {
                listFields = composition.getFieldNames();
                if (!listFields.isEmpty())
                    {
                    fCanExpand = true;
                    }

                if (hVar instanceof ArrayHandle)
                    {
                    fArray    = true;
                    cElements = ((ArrayHandle) hVar).m_hDelegate.m_cSize;
                    if (cElements > 0)
                        {
                        fCanExpand = true;
                        }
                    }
                }
            }

        boolean fExpanded = fCanExpand && !sVar.equals("...")
                && mapExpand.getOrDefault(sPath, 0) > 0;

        VarDisplay result = new VarDisplay(cIndent, sPath, sVar, hVar, fCanExpand, fExpanded);
        listVars.add(result);

        if (fExpanded)
            {
            if (fArray)
                {
                int cMax = mapExpand.getOrDefault(sPath, 10);
                for (int i = 0; i < cElements; ++i)
                    {
                    // can't show all the elements; provide a "+ ..." to further expand the array
                    if (i >= cMax)
                        {
                        addVar(cIndent+1, sPath+"...", "...", hVar, listVars, mapExpand);
                        break;
                        }

                    // extractArrayValue returns only R_NEXT or R_EXCEPTION (out of bounds)
                    if (((xArray) hVar.getTemplate()).extractArrayValue(m_frame, hVar, i, Op.A_STACK) != Op.R_NEXT)
                        {
                        break;
                        }

                    ObjectHandle hElement = m_frame.popStack();
                    String sElement = "[" + i + "]";
                    addVar(cIndent+1, sPath+sElement, sElement, hElement, listVars, mapExpand);
                    }
                }
            else
                {
                for (String sField : listFields)
                    {
                    ObjectHandle hField = ((GenericHandle) hVar).getField(sField);
                    addVar(cIndent+1, sPath+'.'+sField, sField, hField, listVars, mapExpand);
                    }
                }
            }

        return result;
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

    private String renderServices()
        {
        List<Frame.StackFrame> listFrames = new ArrayList<>();
        int                    ixFrame    = 0;

        StringBuilder sb = new StringBuilder();
        for (Container container : m_frame.f_context.getRuntime().f_containers)
            {
            // for now, let's show all the containers, rather than the current one
            if (sb.length() > 0)
                {
                sb.append("\n\n");
                }
            sb.append("+container ")
              .append(container.f_idModule);

            if (container.f_parent != null)
                {
                sb.append(" parent=")
                  .append(container.f_parent.f_idModule);
                }

            for (ServiceContext ctx : container.f_setServices)
                {
                sb.append("\n    Service \"")
                  .append(ctx.f_sName)
                  .append("\" (id=")
                  .append(ctx.f_nId)
                  .append("); status=")
                  .append(ctx.getStatus());

                for (Fiber fiber : ctx.getFibers())
                    {
                    sb.append('\n');

                    Frame frame = fiber.getFrame();
                    if (frame == null)
                        {
                        // fiber is in the "initial" state; show the caller's frame
                        Fiber fiberCaller = fiber.f_fiberCaller;
                        if (fiberCaller != null)
                            {
                            frame = fiberCaller.getFrame();
                            }
                        }

                    if (frame == null)
                        {
                        sb.append(dup(' ', 8));
                        }
                    else
                        {
                        sb.append(frame == m_frame ? '>' : ' ');
                        sb.append(ljust(String.valueOf(ixFrame++), 7));
                        }

                    sb.append("Fiber ")
                        .append(fiber.getId())
                        .append(": ")
                        .append(fiber.getStatus());

                    if (frame != null)
                        {
                        Frame.StackFrame stackFrame = new Frame.StackFrame(frame);
                        listFrames.add(stackFrame);

                        sb.append(" @")
                          .append(stackFrame);
                        }
                    }
                }
            }
        m_aFrames = listFrames.toArray(new Frame.StackFrame[0]);
        return sb.toString();
        }

    private String renderHelp()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Command              Description\n");
        sb.append("-------------------  ---------------------------------------------\n");
        sb.append("F <frame#>           Switch to the specified Frame number\n");
        sb.append("X <var#>             Expand (or contract) the specified variable number\n");
        sb.append("V <var#>             Toggle the view mode (output format) for the specified variable number\n");
        sb.append("E <expr>             Evaluate the specified expression\n");
        sb.append("WE <expr>            Add a \"watch\" for the specified expression\n");
        sb.append("WO <var#>            Add a watch on the specified referent (the object itself)\n");
        sb.append("WR <var#>            Add a watch on the specified reference (the property or variable)\n");
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
        sb.append("VF                   View Services and Fibers\n");
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


    // ----- inner class: DebugStash ---------------------------------------------------------------

    /**
     * A stash object for the debugger, that can be stored (for example) inside a frame.
     */
    static class DebugStash
        {
        /**
         * @return read-only var-expand-map (never null)
         */
        Map<String, Integer> getExpandMap()
            {
            return m_mapExpand == null ? Collections.emptyMap() : m_mapExpand;
            }

        /**
         * @return read/write var-expand-map (never null)
         */
        Map<String, Integer> ensureExpandMap()
            {
            if (m_mapExpand == null)
                {
                m_mapExpand = new HashMap<>();
                }
            return m_mapExpand;
            }

        /**
         * @return read-only list of watches (never null)
         */
        List<Watch> getWatchList()
            {
            return m_listWatches == null ? Collections.emptyList() : m_listWatches;
            }

        /**
         * @return read/write list of watches (never null)
         */
        ArrayList<Watch> ensureWatchList()
            {
            if (m_listWatches == null)
                {
                m_listWatches = new ArrayList<>();
                }
            return m_listWatches;
            }

        Map<String, Integer> m_mapExpand;
        ArrayList<Watch>     m_listWatches;
        }


    // ----- inner class: VarDisplay ---------------------------------------------------------------

    static class VarDisplay
        {
        VarDisplay(int          indent,
                   String       path,
                   String       name,
                   ObjectHandle hVar,
                   boolean      canExpand,
                   boolean      expanded)
            {
            this.indent    = indent;
            this.path      = path;
            this.name      = name;
            this.hVar      = hVar;
            this.canExpand = canExpand;
            this.expanded  = expanded;

            isArray = hVar instanceof ArrayHandle;
            if (isArray)
                {
                size = ((ArrayHandle) hVar).m_hDelegate.m_cSize;
                }
            }

        String render(int nIndex, int cchIndex)
            {
            StringBuilder sb = new StringBuilder();
            sb.append(rjust(String.valueOf(nIndex), cchIndex))
              .append(dup(' ', indent*2 + 1))
              .append(canExpand ? (expanded ? '-' : '+') : ' ')
              .append(name);

            if (!name.equals("..."))
                {
                if (isArray)
                    {
                    sb.append('[')
                        .append(size)
                        .append(']');
                    }

                if (canExpand || isArray)
                    {
                    sb.append(" : ");
                    sb.append(hVar.getType().getValueString());
                    }
                else if (hVar == null)
                    {
                    sb.append(" = <unassigned>");
                    }
                else
                    {
                    sb.append(" = ")
                      .append(hVar);
                    }
                }

            return sb.toString();
            }

        int          indent;
        String       path;
        String       name;
        ObjectHandle hVar;
        boolean      isArray;
        long         size;
        boolean      canExpand;
        boolean      expanded;
        Watch        watch;
        }


    // ----- inner class: Watch --------------------------------------------------------------------

    static class Watch
        {
        /**
         * Construct a watch on an object handle.
         */
        Watch(String       path,
              String       name,
              ObjectHandle hRef)
            {
            this.form = OBJ;
            this.path = path;
            this.name = name;
            this.hVar = hRef;
            }

        /**
         * Construct a watch on a property of an object handle.
         */
        Watch(String       path,
              String       name,
              ObjectHandle hObj,
              String       sProp)
            {
            this.form = PROP;
            this.path = path;
            this.name = name;
            this.hVar = hObj;
            this.sVar = sProp;
            }

        /**
         * Construct a watch on an array index.
         */
        Watch(String       path,
              String       name,
              ObjectHandle hRef,
              int          index)
            {
            this.form  = ELEM;
            this.path  = path;
            this.name  = name;
            this.hVar  = hRef;
            this.index = index;
            }

        ObjectHandle produceHandle(Frame frame)
            {
            switch (form)
                {
                case EVAL:
                    // TODO not yet implemented
                    throw new UnsupportedOperationException();

                case OBJ:
                    return hVar;

                case PROP:
                    return ((GenericHandle) hVar).getField(sVar);

                case VAR:
                    // not currently used
                    throw new UnsupportedOperationException();

                case ELEM:
                    long cElements = ((ArrayHandle) hVar).m_hDelegate.m_cSize;

                    // extractArrayValue returns only R_NEXT or R_EXCEPTION (out of bounds)
                    return cElements > 0 && index >= 0 && index < cElements &&
                        ((xArray) hVar.getTemplate()).extractArrayValue(frame, hVar, index, Op.A_STACK) == Op.R_NEXT
                            ? frame.popStack()
                            : null;

                default:
                    return null;
                }
            }

        static final int EVAL = 0;
        static final int OBJ  = 1;
        static final int PROP = 2;
        static final int VAR  = 3;
        static final int ELEM = 4;

        int          form;
        String       path;
        String       name;
        ObjectHandle hVar;
        String       sVar;
        int          index;
        }


    // ----- constants and data fields -------------------------------------------------------------

    public static final DebugConsole INSTANCE = new DebugConsole();

    /**
     * Persistent preference storage.
     */
    Preferences prefs = Preferences.userNodeForPackage(DebugConsole.class);

    /**
     * Current view mode.
     */
    enum ViewMode {Frames, Console, Services}
    private ViewMode m_viewMode = ViewMode.Frames;

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
     * The "global" debug state (visible in any frame).
     */
    private DebugStash m_debugState = new DebugStash();

    /**
     * The displayed array of variable names.
     */
    private VarDisplay[] m_aVars;

    /**
     * The last debugged iPC.
     */
    private int m_iPC;

    /**
     * The debugger screen height (the limit for printing console output).
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
