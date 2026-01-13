package org.xvm.lsp.server;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xvm.lsp.adapter.MockXtcCompilerAdapter;
import org.xvm.lsp.adapter.XtcCompilerAdapter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;

/**
 * Launcher for the XTC Language Server.
 *
 * <p>Usage:
 * <ul>
 *   <li>For stdio communication: {@code java -jar xtc-lsp.jar}</li>
 *   <li>For socket communication: {@code java -jar xtc-lsp.jar --socket 5007}</li>
 * </ul>
 *
 * <p>Important: This LSP server uses stdio for communication. All logging goes to stderr
 * to keep stdout clean for the JSON-RPC protocol.
 */
public final class XtcLanguageServerLauncher {

    // Static initializer runs before any SLF4J initialization to suppress
    // SLF4J's informational messages that would otherwise go to stdout and
    // corrupt the JSON-RPC protocol stream.
    static {
        // Suppress SLF4J internal messages (they go to stdout by default)
        System.setProperty("slf4j.internal.verbosity", "WARN");
    }

    private static final Logger LOG = LoggerFactory.getLogger(XtcLanguageServerLauncher.class);

    public static void main(final String[] args) {
        LOG.info("Starting XTC Language Server");

        // For now, use the mock adapter
        // In production, this would be: new RealXtcCompilerAdapter(...)
        final XtcCompilerAdapter adapter = new MockXtcCompilerAdapter();

        // Create the server
        final XtcLanguageServer server = new XtcLanguageServer(adapter);

        // Launch with stdio
        launchStdio(server, System.in, System.out);
    }

    /**
     * Launch the server using stdio for communication.
     * This is what VS Code and most editors use.
     */
    public static void launchStdio(
            final XtcLanguageServer server,
            final InputStream in,
            final OutputStream out) {

        final Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server,
                in,
                out);

        final LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);

        try {
            launcher.startListening().get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Server interrupted", e);
        } catch (final ExecutionException e) {
            LOG.error("Server error", e);
        }
    }

    private XtcLanguageServerLauncher() {
        // Utility class
    }
}
