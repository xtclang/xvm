/**
 * A Hello-style web application with a WebSocket chat room.
 *
 * Run WebSocketHello and open http://localhost:8080/ in two browser tabs. An optional argument
 * selects a different binding, for example "localhost:8081/8091".
 */
@WebApp
module WebSocketHello {
    package net   import net.xtclang.org;
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import web.Get;
    import web.ResponseOut;
    import web.WebApp;
    import web.WebService;
    import web.WebSocket;
    import web.http.HostInfo;
    import web.responses.HtmlResponse;

    void run(String[] args = []) {
        @Inject Console console;
        HostInfo binding = new HostInfo(args.empty ? "localhost:8080/8090" : args[0]);
        xenia.createServer(this, route=binding, binding=binding);
        console.print($"Open http://{binding.host}:{binding.httpPort}/ in two browser tabs");
    }

    @Lazy Room room.calc() = new Room();

    @WebService("/")
    service Site {
        @Get
        ResponseOut home() = new HtmlResponse(File:/resources/websocket/index.html);

        @Get("socket")
        WebSocket connect() = new RoomMember(room, counter++);

        @Atomic Int counter = 1;
    }

    /**
     * The room is independent of the HTTP dispatcher and of each connection's receive loop.
     */
    service Room {
        private RoomMember[] members = new RoomMember[];

        void join(RoomMember member) {
            members.add(member);
        }

        void leave(RoomMember member) {
            members.remove(member);
        }

        void broadcast(String message) {
            for (RoomMember member : members) {
                member.send^(message);
            }
        }
    }

    service RoomMember(Room room, Int id)
            extends WebSocket<String>(maxMessageSize=1Ki) {
        @Override
        void onConnect() {
            room.broadcast($"Member {id} joined the chat room");
            room.join(this);
            send($"Connected as Member {id}");
        }

        @Override
        void onMessage(String message) {
            room.broadcast($"{id}: {message}");
        }

        @Override
        void onDisconnect(UInt16 code, String reason) {
            room.leave(this);
            room.broadcast($"Member {id} left the chat room");
        }
    }
}
