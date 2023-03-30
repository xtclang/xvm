package org.xvm.runtime;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import java.util.prefs.Preferences;

import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.asm.op.Nop;

import org.xvm.compiler.EvalCompiler;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.Frame.VarInfo;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.xTerminalConsole;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.dup;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.NO_ARGS;


/**
 * Debugger console.
 *
 * TODO implementation Watch Eval
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
    public synchronized int activate(Frame frame, int iPC)
        {
        frame.f_context.setDebuggerActive(true);

        // Check if there are any natural landing ops beyond this point; otherwise "StepInto" mode
        // may cause the debugger to stop in a completely unexpected place.
        // Note, that the simple algorithm below does not guarantee a stop at this frame since
        // we don't analyze the control flow ops, so there could theoretically be a Jump before the
        // first Nop, preventing a stop to occur. The probability of that is quite low though.
        Op[] aop = frame.f_aOp;
        for (int i = iPC, c = aop.length; i < c; i++)
            {
            if (aop[i] instanceof Nop)
                {
                // stop at the first possibility (most likely in this frame)
                m_frame    = frame;
                m_stepMode = StepMode.StepInto;
                return iPC + 1;
                }
            }

        // a natural landing op is not found; enter the debugger right here
        return enterCommand(frame, iPC, true);
        }

    /**
     * For now, by making this method synchronized we stop all services while debugger is active.
     */
    @Override
    public synchronized int checkBreakPoint(Frame frame, int iPC)
        {
        boolean fDebug  = false;
        boolean fRender = true;

        switch (m_stepMode)
            {
            case NaturalReturn:
                fDebug  = true;
                fRender = false;
                break;

            case StepOut: // handled by onReturn()
            case NaturalCall:
                break;

            case StepOver:
                if (frame == m_frame)
                    {
                    fDebug = m_cSteps == 0 || --m_cSteps == 0;
                    }
                break;

            case StepInto:
               fDebug = frame.f_fiber.isAssociated(m_frame.f_fiber);
               break;

            case StepLine:
                fDebug = frame == m_frame && iPC == m_iPC;
                break;

            case None:
                if (m_setLineBreaks != null)
                    {
                    FindBreakpoint:
                    for (Iterator<BreakPoint> iter = m_setLineBreaks.iterator(); iter.hasNext(); )
                        {
                        BreakPoint bp = iter.next();
                        switch (bp.matches(frame, iPC))
                            {
                            case Op.R_NEXT:
                                // doesn't match
                                break;

                            case Op.R_CALL:
                                // evaluate the condition at this frame
                                m_frame = frame;
                                return Op.R_CALL;

                            default:
                                fDebug = true;
                                if (bp.oneTime)
                                    {
                                    iter.remove();
                                    }
                                // go into the debugger
                                break FindBreakpoint;
                            }
                        }
                    }
                break;
            }

        return fDebug
                ? enterCommand(frame, iPC, fRender)
                : iPC + 1;
        }

    @Override
    public synchronized int checkBreakPoint(Frame frame, ExceptionHandle hEx)
        {
        if (m_stepMode == StepMode.NaturalCall)
            {
            // exception by the natural code called from the debugger ("EVAL", "DS" or "BC")
            if (frame.f_framePrev == m_frame)
                {
                return frame.m_continuation.proceed(frame);
                }

            // keep following the exception
            return Op.R_NEXT;
            }

        if (frame != m_frame && hEx == m_frame.m_hException)
            {
            // this is a re-throw (most probable by FINALLY_END); ignore
            return Op.R_NEXT;
            }

        if (m_fBreakOnAllThrows ||
                 m_setThrowBreaks != null && m_setThrowBreaks.stream().anyMatch(bp -> bp.matches(hEx)))
            {
            boolean fRender = m_stepMode != StepMode.NaturalReturn;
            if (enterCommand(frame, Op.R_EXCEPTION, fRender) == Op.R_CALL)
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
    public synchronized void onReturn(Frame frame)
        {
        if (frame != m_frame)
            {
            switch (m_stepMode)
                {
                case StepInto:
                    if (frame.isNative() || frame.f_function.isSynthetic())
                        {
                        return;
                        }
                    // apparently, there was no op to step at; engage the debugger now
                    // TODO GG: disallow a natural processing (e.g. "DS" command)
                    enterCommand(frame, frame.m_iPC, true);
                    break;

                default:
                    return;
                }
            }

        switch (m_stepMode)
            {
            case StepOver:
            case StepOut:
                // we're exiting the frame; stop at the first chance
                if (frame.f_fiber.isAssociated(m_frame.f_fiber))
                    {
                    m_stepMode = StepMode.StepInto;
                    }
                break;
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
                        continue; // NextCommand

                    case "B+":
                        switch (cArgs)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    BreakPoint bp = makeBreakPointPC(frame, iPC);
                                    if (bp != null)
                                        {
                                        addBP(bp);
                                        continue; // NextCommand
                                        }
                                    }
                                break; // the command is not allowed

                            case 1:
                                int nLine = parseNonNegative(asParts[1]);
                                if (nLine > 0)
                                    {
                                    addBP(makeBreakPointLine(frame, nLine, false));
                                    continue; // NextCommand
                                    }
                                break;  // invalid break point

                            case 2:
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2], false);
                                if (bp != null)
                                    {
                                    addBP(bp);
                                    continue; // NextCommand
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
                                    BreakPoint bp = makeBreakPointPC(frame, iPC);
                                    if (bp != null)
                                        {
                                        removeBP(bp);
                                        continue; // NextCommand
                                        }
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
                                    continue; // NextCommand
                                    }
                                // "B- 3"  (# is from the previously displayed list of breakpoints)
                                else if (m_aBreaks != null)
                                    {
                                    int n = parseNonNegative(asParts[1]);
                                    if (n >= 0 && n < m_aBreaks.length)
                                        {
                                        removeBP(m_aBreaks[n]);
                                        continue;  // NextCommand
                                        }
                                    }
                                break; // invalid command

                            case 2:
                                // "B- MyClass 123"  (class name and line number)
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2], false);
                                if (bp != null)
                                    {
                                    removeBP(bp);
                                    continue; // NextCommand
                                    }
                                break; // invalid break point
                            }
                        break;

                    case "BC":
                        {
                        BreakPoint bp = null;
                        switch (cArgs)
                            {
                            case 0:
                                break; // invalid; args are missing

                            case 1:
                                bp = makeBreakPointPC(frame, iPC);
                                bp.condition = asParts[1];
                                break;

                            case 2:
                                int nLine = parseNonNegative(asParts[1]);
                                if (nLine > 0)
                                    {
                                    bp = makeBreakPointLine(frame, nLine, false);
                                    bp.condition = asParts[2];
                                    }
                                break;

                            default:
                                bp = parseBreakPoint(asParts[1], asParts[2], false);
                                if (bp != null)
                                    {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 3; i < cArgs + 1; i++)
                                        {
                                        sb.append(asParts[i]).append(' ');
                                        }
                                    bp.condition = sb.toString();
                                    }
                                break;
                            }
                        if (bp == null)
                            {
                            break; // invalid command
                            }
                        addBP(bp);
                        continue; // NextCommand
                        }

                    case "BE+":
                        if (cArgs == 1)
                            {
                            addBP(new BreakPoint(asParts[1]));
                            continue; // NextCommand
                            }
                        break; // invalid command

                    case "BE-":
                        if (cArgs == 1)
                            {
                            removeBP(new BreakPoint(asParts[1]));
                            continue; // NextCommand
                            }
                        break; // invalid command

                    case "BT":
                        switch (cArgs)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    BreakPoint bp = makeBreakPointPC(frame, iPC);
                                    if (bp != null)
                                        {
                                        BreakPoint bpExists = findBP(bp);
                                        if (bpExists == null)
                                            {
                                            addBP(bp);
                                            }
                                        else
                                            {
                                            toggleBP(bpExists);
                                            }
                                        continue; // NextCommand
                                        }
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
                                    continue; // NextCommand
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
                                        continue; // NextCommand
                                        }
                                    }
                                else
                                    {
                                    toggleBP(new BreakPoint(asParts[1]));
                                    continue; // NextCommand
                                    }
                                break;

                            case 2:
                                {
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2], false);
                                if (bp != null)
                                    {
                                    toggleBP(bp);
                                    continue; // NextCommand
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
                        continue; // NextCommand

                    case "?": case "H": case "HELP":
                        writer.println(renderHelp());
                        continue; // NextCommand

                    case "N":
                    case "S":
                        m_stepMode = StepMode.StepOver;
                        int cSteps = cArgs == 0 ? 0 : parseNonNegative(asParts[1]);
                        if (cSteps > 0)
                            {
                            m_cSteps = cSteps;
                            }
                        break NextCommand;

                    case "I":
                    case "S+":
                        m_stepMode = StepMode.StepInto;
                        break NextCommand;

                    case "O":
                    case "S-":
                        m_stepMode = StepMode.StepOut;
                        break NextCommand;

                    case "SL":
                        switch (cArgs)
                            {
                            case 0:
                                if (iPC >= 0)
                                    {
                                    int nLine = frame.f_function.calculateLineNumber(iPC);
                                    if (nLine > 0)
                                        {
                                        addBP(makeBreakPointLine(frame, nLine, true));
                                        m_stepMode = StepMode.None;
                                        break NextCommand;
                                        }
                                    }
                                break; // the command is not allowed

                            case 1:
                                int nLine = parseNonNegative(asParts[1]);
                                if (nLine > 0)
                                    {
                                    addBP(makeBreakPointLine(frame, nLine, true));
                                    m_stepMode = StepMode.None;
                                    break NextCommand;
                                    }
                                break;  // invalid break point

                            case 2:
                                BreakPoint bp = parseBreakPoint(asParts[1], asParts[2], true);
                                if (bp != null)
                                    {
                                    addBP(bp);
                                    m_stepMode = StepMode.None;
                                    break NextCommand;
                                    }
                                break;  // invalid break point
                            }
                        break NextCommand;

                    case "R":
                        m_stepMode = StepMode.None;
                        break NextCommand;

                    case "VC":
                        m_viewMode = ViewMode.Console;
                        writer.println(renderConsole());
                        continue; // NextCommand

                    case "VD":
                        m_viewMode = ViewMode.Frames;
                        writer.println(renderDebugger());
                        continue; // NextCommand

                    case "VF":
                        m_viewMode = ViewMode.Services;
                        writer.println(renderServices());
                        continue; // NextCommand

                    case "VS":
                        switch (cArgs)
                            {
                            case 0:
                                writer.println("Current debugger text width=" + m_cWidth +
                                               " characters, height= " + m_cHeight + " lines.");
                                continue; // NextCommand

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
                                    continue; // NextCommand
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
                                continue; // NextCommand
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
                        continue; // NextCommand

                    case "E", "EVAL":
                        {
                        if (cArgs < 1)
                            {
                            break;
                            }

                        if (m_frameFocus != frame || frame.isNative())
                            {
                            writer.println("The \"eval\" command is only supported at the top frame.");
                            continue; // NextCommand
                            }

                        StringBuilder sb = new StringBuilder("{\nreturn {Object r__ = {\nreturn");
                        for (int i = 1; i < cArgs + 1; i++)
                            {
                            sb.append(' ').append(asParts[i]);
                            }
                        sb.append(";\n}; return r__.toString();};\n}");

                        if (performEval(frame, iPC, sb.toString(), writer) == Op.R_CALL)
                            {
                            return Op.R_CALL;
                            }
                        continue; // NextCommand
                        }

                    case "EM", "EVAL_MULTI":
                        {
                        if (cArgs < 1)
                            {
                            break;
                            }

                        if (m_frameFocus != frame || frame.isNative())
                            {
                            writer.println("The \"eval\" command is only supported at the top frame.");
                            continue; // NextCommand
                            }

                        StringBuilder sb = new StringBuilder("{\nreturn {Tuple r__ = {\nreturn");
                        for (int i = 1; i < cArgs + 1; i++)
                            {
                            sb.append(' ').append(asParts[i]);
                            }
                        sb.append(";\n}; return r__.size == 1 ? r__[0].toString() : r__.toString();};\n}");

                        if (performEval(frame, iPC, sb.toString(), writer) == Op.R_CALL)
                            {
                            return Op.R_CALL;
                            }
                        continue; // NextCommand
                        }

                    case "WE":
                        // TODO watch eval <expr>
                        writer.println("Watch Eval has not been implemented.");
                        continue; // NextCommand

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
                            continue; // NextCommand
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
                        continue; // NextCommand
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
                            continue; // NextCommand
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
                                        hVar = ((GenericHandle) hVar).getField(frame, sProp);
                                        }
                                    catch (Throwable e)
                                        {
                                        writer.println("Invalid property: " + sProp);
                                        continue; // NextCommand
                                        }
                                    }

                                StringBuilder sb = new StringBuilder();

                                renderVar(hVar, false, sb, "   +");
                                writer.println(sb);
                                continue; // NextCommand
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
                                if (hVar != null)
                                    {
                                    if (cArgs >= 2)
                                        {
                                        String sProp = asParts[2];
                                        try
                                            {
                                            hVar = ((GenericHandle) hVar).getField(frame, sProp);
                                            }
                                        catch (Throwable e)
                                            {
                                            writer.println("Invalid property: " + sProp);
                                            continue; // NextCommand
                                            }
                                        }
                                    }

                                if (hVar == null)
                                    {
                                    writer.println("<unassigned>");
                                    }
                                else if (callToString(frame, frame.clearException(), hVar, writer) == Op.R_CALL)
                                    {
                                    return Op.R_CALL;
                                    }
                                continue; // NextCommand
                                }
                            }
                        break;

                    default:
                        writer.println("Unknown command: \"" + sCommand + "\"; enter '?' for help");
                        continue; // NextCommand
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
     * Compile and run the "eval" command.
     */
    private int performEval(Frame frame, int iPC, String sEval, PrintWriter writer)
        {
        EvalCompiler    compiler = new EvalCompiler(frame, sEval);
        MethodStructure lambda   = compiler.createLambda(frame.poolContext().typeString());
        if (lambda == null)
            {
            for (ErrorInfo err : compiler.getErrors())
                {
                writer.println(err.getMessageText());
                }
            return Op.R_NEXT;
            }

        Frame.Continuation continuation = frameCaller ->
            {
            ExceptionHandle hEx = frameCaller.clearException();
            if (hEx == null)
                {
                writer.println(((StringHandle) frameCaller.popStack()).getStringValue());
                }
            else
                {
                writer.println("\"Eval\" threw an exception " + hEx);
                writer.println(frameCaller.getStackTrace());
                }
            return iPC;
            };

        ObjectHandle[] ahArgs = getArguments(frame, lambda, compiler.getArgs());
        return resolveEvalArgs(frame, lambda, ahArgs, continuation);
        }

    /**
     * Resolve the "eval" command arguments and run the eval lambda.
     */
    private int resolveEvalArgs(Frame frame, MethodStructure lambda, ObjectHandle[] ahArg,
                                Frame.Continuation continuation)
        {
        if (Op.anyDeferred(ahArg))
            {
            Frame.Continuation stepNext =
                    frameCaller -> callEval(frameCaller, lambda, ahArg, continuation);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return callEval(frame, lambda, ahArg, continuation);
        }

    /**
     * Complete the "eval" command.
     */
    private int callEval(Frame frame, MethodStructure lambda, ObjectHandle[] ahArg,
                         Frame.Continuation continuation)
        {
        ObjectHandle    hThis   = frame.f_function.isFunction() ? null : frame.getThis();
        ExceptionHandle hExPrev = frame.clearException(); // save off the current exception (if any)
        switch (frame.call1(lambda, hThis, ahArg, Op.A_STACK))
            {
            case Op.R_CALL:
                m_stepMode = StepMode.NaturalCall;
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    lambda.getParent().removeChild(lambda);
                    m_stepMode = StepMode.NaturalReturn;

                    int iResult = continuation.proceed(frameCaller);
                    frameCaller.m_hException = hExPrev;
                    return iResult;
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                // this can only be a "stack overflow"
                m_stepMode = StepMode.None;
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Process natural "toString()" call.
     */
    private int callToString(Frame frame, ExceptionHandle hExPrev, ObjectHandle hVar, PrintWriter writer)
        {
        switch (Utils.callToString(frame, hVar))
            {
            case Op.R_NEXT:
                m_stepMode = StepMode.None;
                writer.println(((StringHandle) frame.popStack()).getStringValue());
                frame.m_hException = hExPrev;
                return Op.R_NEXT;

            case Op.R_CALL:
                m_stepMode = StepMode.NaturalCall;
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    ExceptionHandle hEx = frameCaller.clearException();
                    if (hEx == null)
                        {
                        writer.println(((StringHandle) frameCaller.popStack()).getStringValue());
                        }
                    else
                        {
                        writer.println("Call \"toString()\" threw an exception " + hEx);
                        writer.println(frameCaller.getStackTrace());
                        }
                    m_stepMode = StepMode.NaturalReturn;
                    frameCaller.m_hException = hExPrev;
                    return m_iPC;
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                m_stepMode = StepMode.None;
                writer.println("Call \"toString()\" threw an exception " + frame.clearException());
                frame.m_hException = hExPrev;
                return Op.R_NEXT;

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
        return m_debugStash;
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

    private BreakPoint makeBreakPointPC(Frame frame, int iPC)
        {
        int nLine = frame.f_function.calculateLineNumber(iPC);

        return nLine > 0
                ? makeBreakPointLine(frame, nLine, false)
                : null;
        }

    private BreakPoint makeBreakPointLine(Frame frame, int nLine, boolean fOneTime)
        {
        String sName = frame.f_function.getContainingClass().getName();

        return new BreakPoint(sName, nLine, fOneTime);
        }

    private void addBP(BreakPoint bp)
        {
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
        BreakPoint bpExists = findBP(bp);
        if (bpExists == null)
            {
            setBP.add(bp);
            }
        else
            {
            bpExists.enable();
            if (bp.condition != null)
                {
                bpExists.condition = bp.condition;
                }
            bp = bpExists;
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

    private static BreakPoint parseBreakPoint(String sName, String sLine, boolean fOneTime)
        {
        int nLine = parseNonNegative(sLine);

        return nLine <= 0 ? null : new BreakPoint(sName, nLine, fOneTime);
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
                if (settings.length >= 3)
                    {
                    String sCondB64 = settings[2];
                    if (sCondB64.length() > 0)
                        {
                        byte[] abCond = Base64.getDecoder().decode(sCondB64);
                        bp.condition = new String(abCond, StandardCharsets.UTF_8);
                        }

                    if (settings.length >= 4 && settings[3].equals("off"))
                        {
                        bp.disable();
                        }
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
            if (bp.oneTime)
                {
                continue;
                }

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

    /**
     * Obtain the lambda's captures.
     */
    protected ObjectHandle[] getArguments(Frame frame, MethodStructure lambda, int[] aiArgs)
        {
        ObjectHandle[] ahArg = new ObjectHandle[lambda.getMaxVars()];
        for (int i = 0, c = aiArgs.length; i < c; i++)
            {
            try
                {
                ahArg[i] = frame.getArgument(aiArgs[i]);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                ahArg[i] = new DeferredCallHandle(e.getExceptionHandle());
                }
            }
        return ahArg;
        }


    // ----- rendering -----------------------------------------------------------------------------

    /**
     * @return a string for whatever the debugger is supposed to display
     */
    private String renderDisplay()
        {
        return switch (m_viewMode)
            {
            case Frames   -> renderDebugger();
            case Console  -> renderConsole();
            case Services -> renderServices();
            };
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
        int      cchFrames = Math.min(Math.max(longestOf(asFrames), sFHeader.length()), m_cWidth/2);

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

            int iFirst;
            int cLines;
            int nLine = method.calculateLineNumber(m_frameFocus.m_iPC); // 1-based
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

            if (hThis.getComposition().getFieldInfo(GenericHandle.OUTER) != null)
                {
                ObjectHandle hOuter = ((GenericHandle) hThis).getField(frame, GenericHandle.OUTER);
                addVar(0, "outer", "outer", hOuter, listVars, mapExpand);
                }
            }

        int cVars = frame.getCurrentVarCount();
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
        boolean fCanExpand = false;
        boolean fArray     = false;
        long    cElements  = 0;

        if (hVar instanceof RefHandle hRef)
            {
            hVar = hRef.getReferent();
            }

        ListMap<String, FieldInfo> mapLayout = null;
        if (hVar != null)
            {
            TypeComposition composition = hVar.getComposition();
            if (composition != null && !(composition instanceof ProxyComposition))
                {
                mapLayout = getFieldLayout(composition);
                if (!mapLayout.isEmpty())
                    {
                    fCanExpand = true;
                    }

                if (hVar instanceof ArrayHandle hArray)
                    {
                    fArray    = true;
                    cElements = hArray.m_hDelegate.m_cSize;
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
            else if (hVar instanceof GenericHandle hGeneric && mapLayout != null)
                {
                for (Map.Entry<String, FieldInfo> entry : mapLayout.entrySet())
                    {
                    String sName = entry.getKey();

                    ObjectHandle hField = hGeneric.getField(entry.getValue().getIndex());
                    addVar(cIndent+1, sPath+'.'+sName, sName, hField, listVars, mapExpand);
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
        TypeComposition            composition = hVal.getComposition();
        ListMap<String, FieldInfo> mapLayout   = composition == null
                ? null
                : getFieldLayout(composition);
        if (mapLayout != null || mapLayout.isEmpty())
            {
            if (fField)
                {
                sb.append('=');
                }

            if (hVal instanceof ArrayHandle)
                {
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

            for (Map.Entry<String, FieldInfo> entry : mapLayout.entrySet())
                {
                ObjectHandle hField = ((GenericHandle) hVal).getField(entry.getValue().getIndex());

                sb.append('\n')
                  .append(sTab)
                  .append(entry.getKey());
                renderVar(hField, true, sb, sTab + "   ");
                }
            }
        }

    /**
     * Obtain fields to display for the specified composition. The keys are visualized names
     * (simple or composite in the case of collision).
     */
    private ListMap<String, FieldInfo> getFieldLayout(TypeComposition clz)
        {
        // first, split colliding and non-colliding simple names
        Set<String> setSimple    = new HashSet<>();
        Set<String> setColliding = null;

        for (Object enid : clz.getFieldLayout().keySet())
            {
            if (enid instanceof NestedIdentity)
                {
                continue;
                }

            String sSimple = enid instanceof PropertyConstant idProp
                    ? idProp.getName()
                    : (String) enid;
            if (setColliding != null && setColliding.contains(sSimple))
                {
                continue;
                }
            if (!setSimple.add(sSimple))
                {
                setSimple.remove(sSimple);
                if (setColliding == null)
                    {
                    setColliding = new HashSet<>(1);
                    }
                setColliding.add(sSimple);
                }
            }

        ListMap<String, FieldInfo> mapLayout = new ListMap<>();
        for (Map.Entry<Object, FieldInfo> entry : clz.getFieldLayout().entrySet())
            {
            Object    enid  = entry.getKey();
            FieldInfo field = entry.getValue();

            if (enid instanceof NestedIdentity || field.isSynthetic())
                {
                continue;
                }

            String sName = enid instanceof PropertyConstant idProp
                    ? setColliding != null && setColliding.contains(idProp.getName())
                        ? idProp.getPathString()
                        : idProp.getName()
                    : (String) enid;

            mapLayout.put(sName, field);
            }
        return mapLayout;
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
        for (Container container : m_frame.f_context.getRuntime().containers())
            {
            // for now, let's show all the containers, rather than the current one
            if (sb.length() > 0)
                {
                sb.append("\n\n");
                }
            sb.append("+container ")
              .append(container.getModule());

            if (container.f_parent != null)
                {
                sb.append(" parent=")
                  .append(container.f_parent.getModule());
                }

            for (ServiceContext ctx : container.getServices())
                {
                sb.append("\n    Service \"")
                  .append(ctx.f_sName)
                  .append("\" (id=")
                  .append(ctx.f_lId)
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

                    sb.append(fiber);

                    if (fiber.getStatus() == FiberStatus.Waiting)
                        {
                        assert frame != null;
                        frame = frame.f_framePrev;

                        sb.append(fiber.reportWaiting());
                        }

                    if (frame != null)
                        {
                        Frame.StackFrame stackFrame = new Frame.StackFrame(frame);
                        listFrames.add(stackFrame);

                        sb.append(" @")
                          .append(stackFrame);
                        }

                    Frame frameBlocker = fiber.getBlocker();
                    if (frameBlocker != null)
                        {
                        sb.append(" blocked by ")
                          .append(frameBlocker.f_fiber);
                        }

                    }
                }
            }
        m_aFrames = listFrames.toArray(new Frame.StackFrame[0]);
        return sb.toString();
        }

    private String renderHelp()
        {
        return """
            
             Command                  Description
             -------------------      ---------------------------------------------
             F <frame#>               Switch to the specified Frame number
             X <var#>                 Expand (or contract) the specified variable number
             V <var#>                 Toggle the view mode (output format) for the specified variable number
             E <expr>                 Evaluate the specified expression
             EM <expr>                Evaluate the specified expression that produces multiple (or conditional) results
             WE <expr>                Add a "watch" for the specified expression
             WO <var#>                Add a watch on the specified referent (the object itself)
             WR <var#>                Add a watch on the specified reference (the property or variable)
             W- <var#>                Remove the specified watch
             D <var#>                 Display the structure view of the specified variable number
             DS <var#>                Display the "toString()" value of the specified variable number
                                      
             S  (or N)                Step over ("proceed to next line")
             S  (or N) <count>        Repeatedly step over ("proceed to next line") <count> times
             S+ (or I)                Step in
             S- (or O)                Step out of frame
             SL                       Step (run) to current line
             SL <line>                Step (run) to specified line
             SL <name> <line>         Step (run) to specified line
             R                        Run to next breakpoint
                                      
             B+                       Add breakpoint for the current line
             B-                       Remove breakpoint for the current line
             BT                       Toggle breakpoint for the current line
             B+ <line>                Add specified breakpoint
             B+ <name> <line>         Add specified breakpoint
             B- <name> <line>         Remove specified breakpoint
             BC <cond>                Add conditional breakpoint for the current line
             BC <line> <cond>         Add specified conditional breakpoint
             BC <name> <line> <cond>  Add specified conditional breakpoint
             BT <name> <line>         Toggle specified breakpoint
             BE+ <exception>          Break on exception
             BE- <exception>          Remove exception breakpoint
             BE+ *                    Break on all exceptions
             BE- *                    Remove the "all exception" breakpoint
             B- *                     Clear all breakpoints
             BT *                     Toggle all breakpoints (enable all iff all enabled; otherwise disable all)
             B                        List current breakpoints
             B- <breakpoint#>         Remove specified breakpoint (from the breakpoint list)
             BT <breakpoint#>         Toggle specified breakpoint (from the breakpoint list)
                                      
             VC                       View Console
             VD                       View Debugger
             VF                       View Services and Fibers
             VS <width> <height>      Set view width and optional height for debugger and console views
             ?  (or HELP)             Display this help message""";
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
            s = " ".repeat(cSpaces) + s;
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

        public BreakPoint(String sName, int nLine, boolean fOneTime)
            {
            className  = sName;
            lineNumber = nLine;
            oneTime    = fOneTime;
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
            return o instanceof BreakPoint that &&
                    this.lineNumber   == that.lineNumber &&
                    this.className.equals(that.className);
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

        /**
         * Check if the breakpoint matches the current frame/PC.
         *
         * @return {@link Op#R_NEXT} if it does not; {@link Op#R_CALL} if a condition needs to be
         *         evaluated, iPC (>=0) to enter the debugger
         */
        public int matches(Frame frame, int iPC)
            {
            if (!isEnabled() || isException())
                {
                return Op.R_NEXT;
                }

            MethodStructure method = frame.f_function;
            if (className.equals(method.getContainingClass().getName()) &&
                   lineNumber == method.calculateLineNumber(iPC))
                {
                if (condition != null)
                    {
                    PrintWriter writer = xTerminalConsole.CONSOLE_OUT;
                    if (lambda == null)
                        {
                        String       sEval    = "{return " + condition + ";}";
                        EvalCompiler compiler = new EvalCompiler(frame, sEval);

                        lambda = compiler.createLambda(frame.poolContext().typeBoolean());
                        if (lambda == null)
                            {
                            writer.println("Removing invalid breakpoint condition");
                            for (ErrorInfo err : compiler.getErrors())
                                {
                                writer.println(err.getMessageText());
                                condition = null;
                                }
                            return iPC;
                            }
                        lambdaArgs = compiler.getArgs();
                        }

                    Frame.Continuation continuation = frameCaller ->
                        {
                        ExceptionHandle hEx = frameCaller.clearException();
                        if (hEx == null)
                            {
                            if (((BooleanHandle) frameCaller.popStack()).get())
                                {
                                // the condition matched
                                return INSTANCE.enterCommand(frameCaller, iPC, true);
                                }

                            INSTANCE.m_stepMode = StepMode.None;
                            return iPC + 1;
                            }
                        else
                            {
                            writer.println("Breakpoint evaluation threw an exception " + hEx);
                            writer.println(frameCaller.getStackTrace());
                            return iPC;
                            }
                        };

                    ObjectHandle[] ahArgs = INSTANCE.getArguments(frame, lambda, lambdaArgs);
                    return INSTANCE.resolveEvalArgs(frame, lambda, ahArgs, continuation);
                    }
                return iPC;
                }
            return Op.R_NEXT;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append(lineNumber < 0
                    ? className.equals("*")
                            ? "On ALL exceptions"
                            : "On exception: " + className
                    : "At " + className + ':' + lineNumber);

            if (oneTime)
                {
                sb.append(" (one time)");
                }
            else if (disabled)
                {
                sb.append(" (disabled)");
                }

            if (condition != null)
                {
                sb.append(" Condition: ").append(condition);
                }
            return sb.toString();
            }

        String toPrefString()
            {
            StringBuilder sb = new StringBuilder(className);
            sb.append(':');
            if (lineNumber >= 0)
                {
                sb.append(lineNumber);
                }
            sb.append(':');
            if (condition != null)
                {
                byte[] ab = condition.getBytes(StandardCharsets.UTF_8);
                sb.append(Base64.getEncoder().encodeToString(ab));
                }
            if (disabled)
                {
                sb.append(":off");
                }
            return sb.toString();
            }

        public final String     className;  // "*" means all exceptions
        public final int        lineNumber; // -1 for exceptions
        public  String          condition;
        public  boolean         oneTime;
        private boolean         disabled;
        private MethodStructure lambda;
        private int[]           lambdaArgs;
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

            if (hVar instanceof ArrayHandle hArray)
                {
                isArray = true;
                size    = hArray.m_hDelegate.m_cSize;
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
                    return ((GenericHandle) hVar).getField(frame, sVar);

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
     * Remaining steps before stopping.
     */
    private int m_cSteps;

    /**
     * The selected frame (for showing variables).
     */
    private Frame m_frameFocus;

    /**
     * The "global" debug state (visible in any frame).
     */
    private final DebugStash m_debugStash = new DebugStash();

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
    enum StepMode {None, StepOver, StepInto, StepOut, StepLine, NaturalCall, NaturalReturn}
    private StepMode m_stepMode = StepMode.None;
    }