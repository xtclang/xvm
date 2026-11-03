package org.xvm.runtime.debug;

import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;

import org.xvm.runtime.SessionDebugger;
import org.xvm.runtime.SessionDebugger.Variable;

import org.xvm.runtime.debug.DebugProtocol.Attach;
import org.xvm.runtime.debug.DebugProtocol.Breakpoints;
import org.xvm.runtime.debug.DebugProtocol.Cancel;
import org.xvm.runtime.debug.DebugProtocol.Command;
import org.xvm.runtime.debug.DebugProtocol.Completions;
import org.xvm.runtime.debug.DebugProtocol.Continue;
import org.xvm.runtime.debug.DebugProtocol.ContinueAlone;
import org.xvm.runtime.debug.DebugProtocol.DataBreakpointInfo;
import org.xvm.runtime.debug.DebugProtocol.Debug;
import org.xvm.runtime.debug.DebugProtocol.Detach;
import org.xvm.runtime.debug.DebugProtocol.Evaluate;
import org.xvm.runtime.debug.DebugProtocol.ExceptionInfo;
import org.xvm.runtime.debug.DebugProtocol.ExceptionStops;
import org.xvm.runtime.debug.DebugProtocol.FunctionBreakpoints;
import org.xvm.runtime.debug.DebugProtocol.Logged;
import org.xvm.runtime.debug.DebugProtocol.Modules;
import org.xvm.runtime.debug.DebugProtocol.Pause;
import org.xvm.runtime.debug.DebugProtocol.Request;
import org.xvm.runtime.debug.DebugProtocol.RestartFrame;
import org.xvm.runtime.debug.DebugProtocol.RunCode;
import org.xvm.runtime.debug.DebugProtocol.Scopes;
import org.xvm.runtime.debug.DebugProtocol.SetBreakpoints;
import org.xvm.runtime.debug.DebugProtocol.SetDataBreakpoints;
import org.xvm.runtime.debug.DebugProtocol.SetExceptionStops;
import org.xvm.runtime.debug.DebugProtocol.SetFunctionBreakpoints;
import org.xvm.runtime.debug.DebugProtocol.SetVariable;
import org.xvm.runtime.debug.DebugProtocol.Setup;
import org.xvm.runtime.debug.DebugProtocol.StackTrace;
import org.xvm.runtime.debug.DebugProtocol.Step;
import org.xvm.runtime.debug.DebugProtocol.StepInTargets;
import org.xvm.runtime.debug.DebugProtocol.StepInto;
import org.xvm.runtime.debug.DebugProtocol.Stopped;
import org.xvm.runtime.debug.DebugProtocol.Threads;
import org.xvm.runtime.debug.DebugProtocol.Variables;

/**
 * The debug half of the worker protocol where the program runs: the runtime debugger of each run under the debugger,
 * by the run's ID, and the {@link DebugProtocol} commands that ask it, which it answers over a {@link DebugChannel}.
 * The language server's execution worker serves it over its standard input and output, and the {@link DebugAgent}
 * over a socket, to a debugger that attached to a program the launcher runs.
 */
public class DebugCommands {
    /**
     * The error of a request for the debugger of a run without one, or of one that ended.
     */
    public static final String NOT_DEBUGGED = "The program is not running under the debugger";

    private final DebugChannel channel;

    /**
     * The runtime debugger of each run under the debugger.
     */
    private final Map<Long, SessionDebugger> debuggers = new ConcurrentHashMap<>();

    /**
     * Code that runs on a stopped fiber, by the request that ran it, which a {@link Cancel} names.
     */
    private final Map<Long, CompletableFuture<Variable>> running = new ConcurrentHashMap<>();

    public DebugCommands(DebugChannel channel) {
        this.channel = channel;
    }

    /**
     * @return the debuggers and the code runs held, which the worker counts as what a run kept
     */
    public int held() {
        return debuggers.size() + running.size();
    }

    /**
     * Reports a run's stops as {@link Stopped} events and its logpoints' messages as {@link Logged} events, each after
     * {@code before}, which sends what the program printed before them.
     *
     * @param id      the run's ID
     * @param before  sends what the program printed so far
     *
     * @return the listener of the run's runtime debugger
     */
    public SessionDebugger.Listener listener(long id, Runnable before) {
        return new SessionDebugger.Listener() {
            @Override
            public void stopped(SessionDebugger.Stop stop) {
                before.run();
                channel.send(new Stopped(id, stop));
            }

            @Override
            public void logged(String message) {
                before.run();
                channel.send(new Logged(id, message));
            }
        };
    }

    /**
     * Sets up a run's debugger, before the program starts or as a debugger attaches: installs the setup's
     * breakpoints, reporting how each file's were installed, and its function breakpoints, reporting how they were,
     * its exception stops and, if it asks, the stop on entry. The program's modules are known by then, so the
     * breakpoints in their files are verified and the stop on entry is in the program's code, not in the libraries
     * before it. The commands with the run's ID then ask this debugger.
     *
     * @param id        the run's ID
     * @param debugger  the run's runtime debugger
     * @param setup     the setup, or null for none
     */
    public void start(long id, SessionDebugger debugger, Setup setup) {
        if (setup != null) {
            for (var source : list(setup.breakpoints())) {
                channel.send(new Breakpoints(id, source.path(), debugger.setBreakpoints(source.path(), list(source.breakpoints()))));
            }
            var functions = list(setup.functions());
            if (!functions.isEmpty()) {
                channel.send(new FunctionBreakpoints(id, debugger.setMethodBreakpoints(functions)));
            }
            stops(debugger, setup.exceptions());
            if (setup.stopOnEntry()) {
                debugger.stopOnEntry();
            }
        } else {
            stops(debugger, null);
        }
        debuggers.put(id, debugger);
    }

    /**
     * @param id  the run's ID
     *
     * @return the run's debugger, which no command asks from now on, or null if it has none
     */
    public SessionDebugger remove(long id) {
        return debuggers.remove(id);
    }

    /**
     * Handles a command to a run's debugger.
     *
     * @param command  the command
     *
     * @return false if it is not one, as an agent's own {@link Attach}, {@link Debug} and {@link Detach} are not
     */
    public boolean handle(Command command) {
        switch (command) {
            case SetBreakpoints set         -> reply(set, debugger -> debugger.setBreakpoints(set.source().path(),
                                                       list(set.source().breakpoints())));
            case SetFunctionBreakpoints set -> reply(set, debugger -> debugger.setMethodBreakpoints(list(set.breakpoints())));
            case DataBreakpointInfo info    -> reply(info, debugger -> debugger.watchable(info.variablesReference(), info.name()));
            case SetDataBreakpoints set     -> reply(set, debugger -> debugger.setDataBreakpoints(list(set.breakpoints())));
            case Threads threads            -> reply(threads, SessionDebugger::threads);
            case Modules modules            -> reply(modules, SessionDebugger::modules);
            case StackTrace trace           -> reply(trace, debugger -> debugger.stackTrace(trace.threadId()));
            case Scopes scopes              -> reply(scopes, debugger -> debugger.scopes(scopes.frameId()));
            case Variables read             -> later(read, debugger -> debugger.variables(read.variablesReference(),
                                                       read.hex(), read.filter(), read.start(), read.count()));
            case Evaluate evaluate          -> later(evaluate, debugger -> debugger.evaluate(evaluate.frameId(),
                                                       evaluate.expression(), evaluate.hex()));
            case SetVariable set            -> later(set, debugger -> debugger.setVariable(set.variablesReference(),
                                                       set.name(), set.expression(), set.hex()));
            case Completions complete       -> later(complete, debugger -> debugger.completions(complete.frameId(),
                                                       complete.text(), complete.offset()));
            case RunCode run                -> runCode(run);
            case Cancel cancel              -> Optional.ofNullable(running.get(cancel.request())).ifPresent(code -> code.cancel(false));
            case ExceptionInfo info         -> reply(info, debugger -> debugger.exceptionInfo(info.threadId()));
            case Step step                  -> reply(step, debugger -> debugger.step(step.threadId(), step.step(), step.alone()));
            case StepInTargets targets      -> reply(targets, debugger -> debugger.stepInTargets(targets.frameId()));
            case StepInto into              -> reply(into, debugger -> debugger.stepInto(into.threadId(), into.targetId(), into.alone()));
            case RestartFrame restart       -> reply(restart, debugger -> debugger.restartFrame(restart.frameId()));
            case ContinueAlone alone        -> reply(alone, debugger -> debugger.resume(alone.threadId()));
            case SetExceptionStops set      -> debugger(set.id()).ifPresent(debugger -> stops(debugger, set.stops()));
            case Pause pause                -> debugger(pause.id()).ifPresent(SessionDebugger::pause);
            case Continue resume            -> debugger(resume.id()).ifPresent(SessionDebugger::resume);
            case Attach _, Debug _, Detach _ -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Answers, as {@code evaluate} does, with the value of code run on the fiber that waits at the frame; a
     * {@link Cancel} of its request ends it before its limit.
     */
    private void runCode(RunCode run) {
        later(run, debugger -> {
            var code = debugger.run(run.frameId(), run.expression(), Duration.ofMillis(run.limit()), run.program(), run.hex());
            running.put(run.request(), code);
            return code.whenComplete((_, _) -> running.remove(run.request()));
        });
    }

    /**
     * Sets a run's exception stops; none when there are none.
     */
    private static void stops(SessionDebugger debugger, ExceptionStops stops) {
        if (stops == null) {
            debugger.setExceptionStops(false, List.of(), false, List.of());
        } else {
            debugger.setExceptionStops(stops.all(), list(stops.allTypes()), stops.uncaught(), list(stops.uncaughtTypes()));
        }
    }

    /**
     * Answers a request with what the run's debugger gives; a run without one, or one that ended, answers with an
     * error. The answer is made first, since asking the debugger must not wait while holding the channel.
     */
    private <A> void reply(Request<A> request, Function<SessionDebugger, A> answer) {
        channel.send(debugger(request.id())
                .map(debugger -> DebugProtocol.reply(request, answer.apply(debugger)))
                .orElseGet(() -> DebugProtocol.refusal(request, NOT_DEBUGGED)));
    }

    /**
     * Answers, as {@link #reply} does, once the waiting fiber that holds what the request reads has read it on its own
     * thread, so the commands that follow, such as {@link Continue}, do not wait for the read; a failed read answers
     * with its reason.
     */
    private <A> void later(Request<A> request, Function<SessionDebugger, CompletableFuture<A>> read) {
        var debugger = debugger(request.id());
        if (debugger.isEmpty()) {
            channel.send(DebugProtocol.refusal(request, NOT_DEBUGGED));
            return;
        }
        read.apply(debugger.get()).whenComplete((answer, failure) -> channel.send(failure == null
                ? DebugProtocol.reply(request, answer)
                : DebugProtocol.refusal(request, reason(failure))));
    }

    private Optional<SessionDebugger> debugger(long id) {
        return Optional.ofNullable(debuggers.get(id));
    }

    /**
     * @return a failed read's reason, as the runtime debugger put it
     */
    private static String reason(Throwable failure) {
        var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    /**
     * @return the list, or an empty one for a message that left it out
     */
    private static <T> List<T> list(List<T> list) {
        return list == null ? List.of() : list;
    }
}
