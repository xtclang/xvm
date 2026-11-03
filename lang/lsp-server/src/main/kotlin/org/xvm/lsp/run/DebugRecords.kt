package org.xvm.lsp.run

import org.xvm.debug.BreakpointState
import org.xvm.debug.CompletionState
import org.xvm.debug.CompletionsState
import org.xvm.debug.DataBreakpointRequest
import org.xvm.debug.DataInfoState
import org.xvm.debug.DebugSetup
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.ModuleState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.runtime.SessionDebugger
import org.xvm.runtime.debug.DebugProtocol

// The debug adapter's data, as the records of javatools' DebugProtocol that the worker protocol carries, and back.

internal fun DebugSetup.record() =
    DebugProtocol.Setup(
        breakpoints.map(SourceBreakpoints::record),
        exceptions.record(),
        stopOnEntry,
        functions.map(FunctionBreakpointRequest::record),
    )

internal fun SourceBreakpoints.record() =
    DebugProtocol.Source(path, breakpoints.map { SessionDebugger.Line(it.id, it.line, it.condition, it.hitCondition, it.logMessage) })

internal fun FunctionBreakpointRequest.record() = SessionDebugger.Method(id, name, condition, hitCondition)

internal fun DataBreakpointRequest.record() = SessionDebugger.Data(id, dataId, condition, hitCondition)

internal fun ExceptionStops.record() = DebugProtocol.ExceptionStops(all, allTypes, uncaught, uncaughtTypes)

/** A line or data breakpoint's state; where it is installed is its own file, or none. */
internal fun SessionDebugger.Breakpoint.state() = BreakpointState(id(), line(), verified(), message())

/** A function breakpoint's state, with the file of the method's first line, where it is installed. */
internal fun SessionDebugger.Breakpoint.functionState() = BreakpointState(id(), line(), verified(), message(), path())

internal fun SessionDebugger.ThreadInfo.state() = ThreadState(id(), name())

internal fun SessionDebugger.ModuleInfo.state() = ModuleState(name(), version(), sourceDir(), sources(), own())

internal fun SessionDebugger.StackFrameInfo.state() = FrameState(id(), name(), path(), line(), module(), source(), restartable())

internal fun SessionDebugger.Scope.state() = ScopeState(name(), variablesReference())

internal fun SessionDebugger.Variable.state() = VariableState(name(), value(), type(), variablesReference(), lazy(), elements())

internal fun SessionDebugger.Completions.state() =
    CompletionsState(
        start(),
        length(),
        items().map {
            CompletionState(it.label(), it.kind())
        },
    )

internal fun SessionDebugger.ExceptionInfo.state() = ExceptionInfoState(type(), text(), stackTrace(), uncaught())

internal fun SessionDebugger.StepTarget.state() = StepTargetState(id(), label())

internal fun SessionDebugger.Stop.state() = StopState(reason(), description(), threadId(), breakpointIds())

internal fun SessionDebugger.Watchable.state() = DataInfoState(dataId(), description())
