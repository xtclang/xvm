package org.xvm.runtime.debug;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.runtime.SessionDebugger;
import org.xvm.runtime.SessionDebugger.Breakpoint;
import org.xvm.runtime.SessionDebugger.Variable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The messages of the worker protocol that a program's debugging exchanges with its debugger, as records: the
 * {@link Command commands} that the debugger sends, the {@link Event events} that come back, and the {@link Setup}
 * that a run under the debugger starts with. Both ends use these records, the program's in the language server's
 * execution worker or under the launcher's {@code --debug} ({@link DebugCommands}, {@link DebugAgent}), and the
 * debugger's in the language server, so the protocol is defined here alone.
 *
 * <p>A message is one JSON object per line: its record's components, by their names, and its name, its record's
 * simple name with a lower-case first letter, as its {@code command} or {@code event}. The answers to the
 * {@link Request requests} are the runtime debugger's own records, in a {@link Reply}. The worker's own commands and
 * events, of a run's build, console and memory, are the worker's.
 */
public final class DebugProtocol {
    /**
     * The worker protocol's version, which a worker and an agent report in {@link Ready}; raised whenever a command
     * or event, of a run or of its debugger, changes.
     */
    public static final int VERSION = 25;

    private static final Gson GSON = new Gson();

    private static final Map<String, Class<?>> COMMANDS = names(Command.class);

    private static final Map<String, Class<?>> EVENTS = names(Event.class);

    private DebugProtocol() {
    }

    // ----- the setup -----------------------------------------------------------------------------

    /**
     * The breakpoints of one file.
     */
    public record Source(String path, List<SessionDebugger.Line> breakpoints) {
    }

    /**
     * Where an exception stops the program: at every exception, {@code all}, or at an uncaught one,
     * {@code uncaught}, each only at the exception types listed, when there are any.
     */
    public record ExceptionStops(boolean all, List<String> allTypes, boolean uncaught, List<String> uncaughtTypes) {
    }

    /**
     * What a run under the debugger starts with: the breakpoints of its files, its exception stops, whether it stops
     * at its first line, and its function breakpoints.
     */
    public record Setup(List<Source> breakpoints, ExceptionStops exceptions, boolean stopOnEntry,
                        List<SessionDebugger.Method> functions) {
    }

    // ----- the commands --------------------------------------------------------------------------

    /**
     * A command to the debugging of the run {@code id}.
     */
    public sealed interface Command {
        long id();
    }

    /**
     * A command that a {@link Reply} with its {@code request} number answers, with an answer of type {@code A}.
     */
    public sealed interface Request<A> extends Command {
        long request();
    }

    public record SetBreakpoints(long id, long request, Source source) implements Request<List<Breakpoint>> {
    }

    public record SetFunctionBreakpoints(long id, long request, List<SessionDebugger.Method> breakpoints)
            implements Request<List<Breakpoint>> {
    }

    public record DataBreakpointInfo(long id, long request, int variablesReference, String name)
            implements Request<SessionDebugger.Watchable> {
    }

    public record SetDataBreakpoints(long id, long request, List<SessionDebugger.Data> breakpoints)
            implements Request<List<Breakpoint>> {
    }

    public record Threads(long id, long request) implements Request<List<SessionDebugger.ThreadInfo>> {
    }

    public record Modules(long id, long request) implements Request<List<SessionDebugger.ModuleInfo>> {
    }

    public record StackTrace(long id, long request, int threadId) implements Request<List<SessionDebugger.StackFrameInfo>> {
    }

    public record Scopes(long id, long request, int frameId) implements Request<List<SessionDebugger.Scope>> {
    }

    public record Variables(long id, long request, int variablesReference, boolean hex, SessionDebugger.Filter filter,
                            int start, int count) implements Request<List<Variable>> {
    }

    public record Evaluate(long id, long request, int frameId, String expression, boolean hex)
            implements Request<Variable> {
    }

    public record SetVariable(long id, long request, int variablesReference, String name, String expression,
                              boolean hex) implements Request<Variable> {
    }

    public record Completions(long id, long request, int frameId, String text, int offset)
            implements Request<SessionDebugger.Completions> {
    }

    /**
     * Runs code on the fiber that waits at the frame, for at most {@code limit} milliseconds, and with
     * {@code program}, the whole program meanwhile; a {@link Cancel} of its request ends it before then.
     */
    public record RunCode(long id, long request, int frameId, String expression, long limit, boolean program,
                          boolean hex) implements Request<Variable> {
    }

    /**
     * Ends the code that the {@link RunCode} with the {@code request} number runs; it then answers that it was
     * cancelled.
     */
    public record Cancel(long id, long request) implements Command {
    }

    /**
     * Answers with the exception the fiber stopped the program at; none when it stopped it for another reason.
     */
    public record ExceptionInfo(long id, long request, int threadId) implements Request<SessionDebugger.ExceptionInfo> {
    }

    /**
     * Steps a fiber, {@code alone} while the program's other fibers stay stopped; answers whether it was stepped.
     */
    public record Step(long id, long request, int threadId, SessionDebugger.Step step, boolean alone)
            implements Request<Boolean> {
    }

    public record StepInTargets(long id, long request, int frameId) implements Request<List<SessionDebugger.StepTarget>> {
    }

    public record StepInto(long id, long request, int threadId, int targetId, boolean alone) implements Request<Boolean> {
    }

    public record RestartFrame(long id, long request, int frameId) implements Request<Boolean> {
    }

    /**
     * Continues a fiber alone, while the program's other fibers stay stopped; answers whether it was continued.
     */
    public record ContinueAlone(long id, long request, int threadId) implements Request<Boolean> {
    }

    public record SetExceptionStops(long id, ExceptionStops stops) implements Command {
    }

    public record Pause(long id) implements Command {
    }

    public record Continue(long id) implements Command {
    }

    /**
     * A debugger attaches to a program under the launcher's {@code --debug}, with the run ID it uses from then on and
     * the program's token, if it has one; the agent answers with {@link Ready} or {@link Refused}.
     */
    public record Attach(long id, String token) implements Command {
    }

    /**
     * An attached debugger's setup, with the names of the program's own modules besides its main one; the agent
     * answers as a worker does a run's setup, then with {@link Started}.
     */
    public record Debug(long id, List<String> modules, Setup setup) implements Command {
    }

    /**
     * The debugger leaves the program, which drops what it asked for and runs on.
     */
    public record Detach(long id) implements Command {
    }

    // ----- the events ----------------------------------------------------------------------------

    /**
     * An event of a program's debugging.
     */
    public sealed interface Event {
    }

    /**
     * An event of the run {@code id}.
     */
    public sealed interface RunEvent extends Event {
        long id();
    }

    /**
     * A worker or an agent is ready: its process, the protocol it speaks, and the bundled XDK a worker runs, or the
     * module of the program an agent debugs. A worker of the first protocol reports no protocol.
     */
    public record Ready(long pid, Integer protocol, String xdk, String module) implements Event {
    }

    /**
     * An agent refuses a debugger, saying why, and closes the connection.
     */
    public record Refused(String error) implements Event {
    }

    /**
     * The run started, or the attached program runs on with its debugger.
     */
    public record Started(long id) implements RunEvent {
    }

    /**
     * The run ended, with its exit code or the reason it failed.
     */
    public record Exit(long id, Long code, String error) implements RunEvent {
    }

    /**
     * Text for the debugger's log, such as why a command failed.
     */
    public record Log(String text) implements Event {
    }

    /**
     * How a setup installed one file's breakpoints.
     */
    public record Breakpoints(long id, String path, List<Breakpoint> breakpoints) implements RunEvent {
    }

    /**
     * How a setup installed the function breakpoints, each where the method's first line is.
     */
    public record FunctionBreakpoints(long id, List<Breakpoint> breakpoints) implements RunEvent {
    }

    public record Stopped(long id, SessionDebugger.Stop stop) implements RunEvent {
    }

    /**
     * A logpoint's message.
     */
    public record Logged(long id, String text) implements RunEvent {
    }

    /**
     * The answer to a {@link Request}, or the reason it has none.
     */
    public record Reply(long id, long request, JsonElement answer, String error) implements RunEvent {
    }

    // ----- JSON ----------------------------------------------------------------------------------

    /**
     * @return the command as its line of the protocol
     */
    public static JsonObject json(Command command) {
        return named(command, "command");
    }

    /**
     * @return the event as its line of the protocol
     */
    public static JsonObject json(Event event) {
        return named(event, "event");
    }

    /**
     * @return the setup as JSON, as a worker's run command carries it
     */
    public static JsonElement json(Setup setup) {
        return GSON.toJsonTree(setup);
    }

    /**
     * @return the command of a line of the protocol, or null if it is not one of these
     *
     * @throws IllegalArgumentException if it is one, but lacks a member of a number or a boolean
     */
    public static Command command(JsonObject json) {
        return (Command) message(json, "command", COMMANDS);
    }

    /**
     * @return the event of a line of the protocol, or null if it is not one of these
     */
    public static Event event(JsonObject json) {
        return (Event) message(json, "event", EVENTS);
    }

    /**
     * @return the name of a line's command, which may not be one of these, or null if it names none
     */
    public static String commandName(JsonObject json) {
        var name = json.get("command");
        return name != null && name.isJsonPrimitive() ? name.getAsString() : null;
    }

    /**
     * @return the setup that a worker's run command carries
     */
    public static Setup setup(JsonElement json) {
        return GSON.fromJson(json, Setup.class);
    }

    /**
     * @return the reply to a request, with its answer
     */
    public static <A> Reply reply(Request<A> request, A answer) {
        return new Reply(request.id(), request.request(), GSON.toJsonTree(answer), null);
    }

    /**
     * @return the reply to a request that has no answer, saying why
     */
    public static Reply refusal(Request<?> request, String error) {
        return new Reply(request.id(), request.request(), null, error);
    }

    /**
     * @return a reply's answer to the request, as its type
     */
    public static <A> A answer(Request<A> request, Reply reply) {
        return reply.answer() == null ? null : GSON.fromJson(reply.answer(), answerType(request.getClass()));
    }

    private static JsonObject named(Object message, String kind) {
        var json = GSON.toJsonTree(message).getAsJsonObject();
        json.addProperty(kind, name(message.getClass()));
        return json;
    }

    /**
     * A message without a member of a number or a boolean is refused, since Gson would make it zero or false; one
     * without an object, a list or a text has null there.
     */
    private static Object message(JsonObject json, String kind, Map<String, Class<?>> types) {
        var name = json.get(kind);
        var type = name != null && name.isJsonPrimitive() ? types.get(name.getAsString()) : null;
        if (type == null) {
            return null;
        }
        for (var component : type.getRecordComponents()) {
            if (component.getType().isPrimitive() && !json.has(component.getName())) {
                throw new IllegalArgumentException(name.getAsString() + " has no " + component.getName());
            }
        }
        return GSON.fromJson(json, type);
    }

    /**
     * @return the records of a sealed interface of messages, and of the sealed interfaces it permits, by their names
     */
    private static Map<String, Class<?>> names(Class<?> sealed) {
        var names = new HashMap<String, Class<?>>();
        for (var type : sealed.getPermittedSubclasses()) {
            if (type.isRecord()) {
                names.put(name(type), type);
            } else {
                names.putAll(names(type));
            }
        }
        return Map.copyOf(names);
    }

    private static String name(Class<?> type) {
        var name = type.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * @return the type of the answer to a request of the type, its {@code A} in {@code Request<A>}
     */
    private static Type answerType(Class<?> type) {
        for (var face : type.getGenericInterfaces()) {
            if (face instanceof ParameterizedType request && request.getRawType() == Request.class) {
                return request.getActualTypeArguments()[0];
            }
        }
        throw new IllegalArgumentException(type + " is not a request");
    }
}
