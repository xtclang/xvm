/**
 * Regression test (curtesy of Michael Raasch) for an ambient Timeout bounding a blocking socket
 * read. The short repeated timeouts exercise the race between a due wake-up and its replacement.
 *
 * Start a peer in another terminal; each connection stays silent for 250ms:
 *
 *   python3 - <<'PY'
 *   import socket
 *   import threading
 *   import time
 *
 *   def hold(connection):
 *       time.sleep(0.25)
 *       connection.close()
 *
 *   server = socket.socket()
 *   server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
 *   server.bind(("127.0.0.1", 9997))
 *   server.listen(100)
 *   workers = []
 *   for _ in range(100):
 *       connection, _ = server.accept()
 *       worker = threading.Thread(target=hold, args=(connection,))
 *       worker.start()
 *       workers.append(worker)
 *   server.close()
 *   for worker in workers:
 *       worker.join()
 *   PY
 *
 *   manualTests> xec -L build/xtc/main/lib src/main/x/slow/socketTimeout.x
 */
module socketTimeout {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.IPAddress;
    import net.Network;
    import net.Socket;

    void run(String[] args = ["127.0.0.1", "9997"]) {
        @Inject Network insecureNetwork;
        @Inject Clock   clock;

        String   host   = args.size > 0 ? args[0] : "127.0.0.1";
        UInt16   port   = new UInt16(args.size > 1 ? args[1] : "9997");
        Duration budget = Duration:0.01s;
        Duration slack  = Duration:0.1s;

        for (Int trial : 1..100) {
            assert Socket sock := insecureNetwork.connect((new IPAddress(host), port))
                    as $"connect failed: {host}:{port} — is the fixture peer listening?";

            String outcome;
            Time   start = clock.now;
            try {
                using (new Timeout(budget)) {
                    sock.in.readBytes(4);
                }
                outcome = "EOF (peer closed)";      // peer never sends, so this is EOF
            } catch (TimedOut e) {
                outcome = "TimedOut";
            } catch (Exception e) {
                outcome = $"threw: {e.text}";
            } finally {
                sock.close();
            }
            Duration waited = clock.now - start;

            console.print($"trial {trial}: read unblocked after {waited} via {outcome}");

            assert outcome == "TimedOut" as $"expected TimedOut; actual={outcome}";
            assert waited < budget + slack as $|
                    |LOST WAKE-UP: the read stayed parked for {waited} under a {budget} Timeout
                    |(it only unblocked when the peer closed the socket).
                    |
                    |The fiber's deadline was not honoured while it was parked in
                    |xRTSocket.invokeReadBytesImpl's waitForIO(): the timeout wake-up is
                    |intermittently lost and the read only resumes on socket activity / EOF.
                    ;
        }

        console.print("PASS — all deadlines honoured");
    }
}
