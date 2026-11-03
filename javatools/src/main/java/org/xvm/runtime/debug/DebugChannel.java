package org.xvm.runtime.debug;

import java.io.PrintStream;

import java.util.function.Consumer;

import com.google.gson.JsonObject;

/**
 * Where a program's debugging sends the events of the worker protocol, one JSON object per line: the standard
 * output of the language server's execution worker, or the socket of the program's {@link DebugAgent}. Events are
 * sent whole, one at a time.
 */
public class DebugChannel {
    private final PrintStream out;

    public DebugChannel(PrintStream out) {
        this.out = out;
    }

    /**
     * Sends an event of the debugging, as {@link DebugProtocol} defines it.
     *
     * @param event  the event
     */
    public void send(DebugProtocol.Event event) {
        send(DebugProtocol.json(event));
    }

    /**
     * Sends an event of the worker's own, which {@code fill} gives its content.
     *
     * @param event  the event's name
     * @param fill   adds the event's content
     */
    public void send(String event, Consumer<JsonObject> fill) {
        var json = new JsonObject();
        json.addProperty("event", event);
        fill.accept(json);
        send(json);
    }

    /**
     * Sends text for the debugger's log, such as why a command failed.
     *
     * @param text  the text
     */
    public void log(String text) {
        send(new DebugProtocol.Log(text));
    }

    private synchronized void send(JsonObject json) {
        out.println(json);
        out.flush();
    }
}
