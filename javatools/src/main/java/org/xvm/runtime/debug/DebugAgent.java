package org.xvm.runtime.debug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import java.nio.charset.StandardCharsets;

import java.time.Duration;

import java.util.Arrays;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.api.DebuggerProvider;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.SessionDebugger;

import org.xvm.runtime.debug.DebugProtocol.Attach;
import org.xvm.runtime.debug.DebugProtocol.Debug;
import org.xvm.runtime.debug.DebugProtocol.Detach;
import org.xvm.runtime.debug.DebugProtocol.Exit;
import org.xvm.runtime.debug.DebugProtocol.Ready;
import org.xvm.runtime.debug.DebugProtocol.Refused;
import org.xvm.runtime.debug.DebugProtocol.Started;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The debugger agent of a program that the launcher runs with {@code --debug}, as in
 * {@code xec --debug listen:127.0.0.1:5005,suspend App.x}: the launcher finds it through {@code META-INF/services},
 * and a language server's debug session attaches to the program through it, speaking the debug half of the worker
 * protocol ({@link DebugCommands}, {@link DebugProtocol}) over a socket, one JSON object per line, as with a run in
 * the language server's execution worker.
 *
 * <p>The options, after the launcher's: {@code listen:<host>:<port>}, where the agent waits for a debugger to
 * attach, and once one has detached, for the next, while the program runs; port 0 takes a free port, and the agent
 * prints the port it listens on to the program's error stream; or {@code connect:<host>:<port>}, where a debugger
 * waits for the program. The host must be this machine's, a loopback address. {@code suspend} holds the program
 * before its first line until a debugger has attached and sent its setup; {@code token=<token>} refuses a debugger
 * that does not send that token.
 *
 * <p>The exchange: the debugger sends {@link Attach}, which the agent answers with {@link Ready}, or with
 * {@link Refused}, closing the connection; the debugger sends {@link Debug}, which the agent answers as the worker
 * does a run's setup, then with {@link Started}; the debugger's commands to the run follow. {@link Detach}, or the
 * connection closing, drops what the debugger asked for and resumes the program, which runs on; the program's end is
 * an {@link Exit}. With no debugger attached, a stop, as at {@code assert:debug}, is resumed at once. The program's
 * console stays its own: its output and input do not go over the connection.
 */
public class DebugAgent implements DebuggerProvider {
    /**
     * How long closing waits for the agent's thread, which a debugger that does not read could hold up.
     */
    private static final Duration CLOSE_LIMIT = Duration.ofSeconds(5);

    @Override
    public Session start(String options, ModuleRepository repository, ModuleStructure module) throws IOException {
        var agent = new Agent(Options.of(options), repository, module);
        agent.open();
        return agent;
    }

    /**
     * The options of the agent: whether it listens or connects, where, whether the program waits for a debugger
     * before its first line, and the token a debugger must send, or null.
     */
    public record Options(boolean listen, InetAddress address, int port, boolean suspend, String token) {
        public static final String USAGE =
                "--debug takes listen:<host>:<port> or connect:<host>:<port>, then suspend and token=<token>";

        /**
         * @param options  the options in {@code --debug}'s form, without the launcher's own
         *
         * @return the options
         *
         * @throws IllegalArgumentException with the reason, if they are not
         */
        public static Options of(String options) {
            var parts = Arrays.stream(options.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
            if (parts.isEmpty()) {
                throw new IllegalArgumentException(USAGE);
            }
            var where = parts.getFirst();
            var mode  = where.contains(":") ? where.substring(0, where.indexOf(':')) : where;
            if (!mode.equals("listen") && !mode.equals("connect")) {
                throw new IllegalArgumentException(USAGE);
            }
            var rest  = where.substring(mode.length());
            var colon = rest.lastIndexOf(':');
            var host  = colon > 0 ? rest.substring(1, colon) : "";
            var port  = colon > 0 ? port(rest.substring(colon + 1)) : -1;
            if (host.isEmpty() || port < 0) {
                throw new IllegalArgumentException(USAGE);
            }
            var listen = mode.equals("listen");
            if (!listen && port == 0) {
                throw new IllegalArgumentException("A debugger to connect to has a port of its own, not 0");
            }
            InetAddress address;
            try {
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unknown host '" + host + "'");
            }
            if (!address.isLoopbackAddress()) {
                throw new IllegalArgumentException(
                        "The debugger attaches on this machine only, and '" + host + "' is not a loopback address");
            }
            var    suspend = false;
            String token   = null;
            for (var part : parts.subList(1, parts.size())) {
                if (part.equals("suspend")) {
                    suspend = true;
                } else if (part.startsWith("token=")) {
                    token = part.substring("token=".length());
                } else {
                    throw new IllegalArgumentException("Unknown --debug option '" + part + "'; " + USAGE);
                }
            }
            return new Options(listen, address, port, suspend, token);
        }

        /**
         * @return the port, or -1 if the text is not one
         */
        private static int port(String text) {
            try {
                var port = Integer.parseInt(text);
                return port >= 0 && port <= 65535 ? port : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    /**
     * One program's debugging: the program's runtime debugger, and the debugger attached to it, if any.
     */
    private static class Agent implements Session {
        private final Options          options;
        private final ModuleRepository repository;
        private final ModuleStructure  module;
        private final SessionDebugger  debugger;

        /**
         * Completes once a debugger has attached and sent its setup, which a suspended program waits for.
         */
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        /**
         * The debugger attached to the program, or null while none is.
         */
        private volatile Attached attached;

        /**
         * The connection to the debugger, while there is one.
         */
        private volatile Socket socket;

        /**
         * The program's exit code, once it has ended.
         */
        private volatile Integer code;

        private volatile boolean closed;

        /**
         * Where a debugger attaches, in {@code listen} mode.
         */
        private ServerSocket server;

        /**
         * The thread that waits for a debugger and serves it, which ends before {@link #close} returns.
         */
        private Thread thread;

        Agent(Options options, ModuleRepository repository, ModuleStructure module) {
            this.options    = options;
            this.repository = repository;
            this.module     = module;
            this.debugger   = new SessionDebugger(new SessionDebugger.Listener() {
                @Override
                public void stopped(SessionDebugger.Stop stop) {
                    var to = attached;
                    if (to == null) {
                        debugger().resume();
                    } else {
                        to.listener().stopped(stop);
                    }
                }

                @Override
                public void logged(String message) {
                    var to = attached;
                    if (to != null) {
                        to.listener().logged(message);
                    }
                }
            });
        }

        /**
         * The debugger attached to the program: the run ID it chose, where it hears from the program, and the
         * listener that reports the program's stops to it.
         */
        private record Attached(long id, DebugChannel channel, SessionDebugger.Listener listener) {
        }

        @Override
        public SessionDebugger debugger() {
            return debugger;
        }

        /**
         * Waits for a debugger, or connects to one, and, with {@code suspend}, until it has sent its setup.
         */
        void open() throws IOException {
            debugger.addModule(module);
            if (options.listen()) {
                var listening = new ServerSocket(options.port(), 1, options.address());
                server = listening;
                System.err.println("Listening for the debugger at " + options.address().getHostAddress() + ":"
                        + listening.getLocalPort());
                daemon(() -> {
                    while (!closed) {
                        Socket accepted;
                        try {
                            accepted = listening.accept();
                        } catch (IOException e) {
                            break;
                        }
                        serve(accepted);
                    }
                });
            } else {
                var connected = new Socket(options.address(), options.port());
                daemon(() -> serve(connected));
            }
            if (options.suspend()) {
                try {
                    released.get();
                } catch (ExecutionException e) {
                    close();
                    throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    close();
                    throw new IOException("Interrupted while waiting for the debugger");
                }
            }
        }

        /**
         * Serves one debugger until it detaches, the connection closes or the program ends.
         */
        private void serve(Socket socket) {
            this.socket = socket;
            // a connection that the program's end raced: close() did not see it
            if (closed) {
                quietly(socket);
                return;
            }
            Long id = null;
            DebugCommands commands = null;
            try {
                var channel = new DebugChannel(new PrintStream(socket.getOutputStream(), false, StandardCharsets.UTF_8));
                var lines   = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                commands    = new DebugCommands(channel);
                id          = hello(lines, channel);
                if (id == null) {
                    return;
                }
                for (var line = lines.readLine(); line != null; line = lines.readLine()) {
                    var json = parse(line);
                    if (json == null) {
                        continue;
                    }
                    try {
                        switch (DebugProtocol.command(json)) {
                            case Detach _     -> {
                                return;
                            }
                            case Debug debug  -> setUp(id, channel, commands, debug);
                            case null         -> channel.log("Unknown debugger command: " + DebugProtocol.commandName(json));
                            case DebugProtocol.Command command -> {
                                if (!commands.handle(command)) {
                                    channel.log("Unknown debugger command: " + DebugProtocol.commandName(json));
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        // a command without what it needs fails alone; the debugger stays attached
                        channel.log("The debugger command " + DebugProtocol.commandName(json) + " failed: " + e);
                    }
                }
            } catch (Exception e) {
                // the connection closed, or what came is not the protocol
            } finally {
                // first no stop goes to this debugger, then the program runs on without what it asked for
                attached = null;
                if (id != null) {
                    commands.remove(id);
                }
                debugger.reset();
                this.socket = null;
                quietly(socket);
                if (!options.listen()) {
                    released.completeExceptionally(new IOException("The debugger left before it attached"));
                }
            }
        }

        /**
         * Takes the debugger's setup, then reports that the program runs, or that it has ended already.
         */
        private void setUp(long id, DebugChannel channel, DebugCommands commands, Debug debug) {
            // the debugger hears of stops from the first breakpoint it installs
            attached = new Attached(id, channel, commands.listener(id, () -> {}));
            if (debug.modules() != null) {
                for (var name : debug.modules()) {
                    var own = repository.loadModule(name);
                    if (own != null && own != module) {
                        debugger.addModule(own);
                    }
                }
            }
            commands.start(id, debugger, debug.setup());
            var ended = code;
            if (ended == null) {
                channel.send(new Started(id));
            } else {
                exit(channel, id, ended);
            }
            released.complete(null);
        }

        /**
         * Checks the debugger's {@code attach} and answers it.
         *
         * @return the run ID it chose, or null if it is refused
         */
        private Long hello(BufferedReader lines, DebugChannel channel) throws IOException {
            var line = lines.readLine();
            var json = line == null ? null : parse(line);
            if (json == null) {
                return null;
            }
            DebugProtocol.Command hello;
            try {
                hello = DebugProtocol.command(json);
            } catch (IllegalArgumentException e) {
                hello = null;
            }
            var refusal = !(hello instanceof Attach attach)
                    ? "A debugger attaches first, naming its run ID"
                    : options.token() != null && !options.token().equals(attach.token())
                            ? "The token does not match the program's"
                            : null;
            if (refusal != null) {
                channel.send(new Refused(refusal));
                return null;
            }
            channel.send(new Ready(ProcessHandle.current().pid(), DebugProtocol.VERSION, null, module.getName()));
            return hello.id();
        }

        @Override
        public void ended(int code) {
            this.code = code;
            var to = attached;
            if (to != null) {
                exit(to.channel(), to.id(), code);
            }
        }

        /**
         * Ends the debugging, and waits for the agent's thread to end: the launcher closes the agent's jar next.
         */
        @Override
        public void close() {
            closed = true;
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    // closed already
                }
            }
            var connection = socket;
            if (connection != null) {
                quietly(connection);
            }
            released.completeExceptionally(new IOException("The program ended"));
            if (thread != null) {
                try {
                    thread.join(CLOSE_LIMIT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private static void exit(DebugChannel channel, long id, int code) {
            channel.send(new Exit(id, (long) code, null));
        }

        private static JsonObject parse(String line) {
            try {
                return JsonParser.parseString(line) instanceof JsonObject json ? json : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static void quietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException e) {
                // closed already
            }
        }

        private void daemon(Runnable task) {
            thread = Thread.ofPlatform().name("xtc-debug-agent").daemon(true).start(task);
        }
    }
}
