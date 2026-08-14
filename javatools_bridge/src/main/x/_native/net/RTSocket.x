import ecstasy.io.EndOfFile;
import ecstasy.io.IOClosed;
import ecstasy.io.Channel;

import libnet.IPAddress;
import libnet.Socket;
import libnet.SocketAddress;
import libnet.ServerSocket;

/**
 * Implements a native [Socket].
 */
service RTSocket(SocketAddress localAddress, SocketAddress remoteAddress)
        implements Socket {
    /**
     * Constructor from native land.
     *
     * @param name            the name of this network interface
     * @param addressesBytes  the byte array for each address of this network interface
     */
    construct(Byte[] localAddressBytes, UInt16 localPort, Byte[] remoteAddressBytes, UInt16 remotePort) {
        construct RTSocket((new IPAddress(localAddressBytes), localPort),
                           (new IPAddress(remoteAddressBytes), remotePort));
    }

    /**
     * State of the socket IO.
     *
     * * None - no IO yet
     * * Sync - access to `in` and/or `out` has occurred
     * * Async - access to `channel` has occurred
     * * Closed - the socket has been closed
     */
    private enum IO {None, Sync, Async, Closed}

    /**
     * The "IO mode" of the socket. Once the socket goes into sync or async mode, it's not supposed
     * switch to the other.
     */
    private IO mode = None;

    /**
     * TODO
     */
    protected/private Channel? rawChannel = Null;


    // ----- Socket methods ------------------------------------------------------------------------

    @Override
    public/private SocketAddress localAddress;

    @Override
    public/private SocketAddress remoteAddress;

    @Override
    @Lazy public/private Channel channel.calc() {
        switch (mode) {
        case None:
        case Async:
            mode = Async;
            val channel = new SocketChannel(rawChannel ?: TODO("Native"));
            return &channel.maskAs(Socket.Channel);

        case Sync:
            throw new IllegalState("The Socket is already in synchronous I/O mode");

        case Closed:
            throw new IOClosed();
        }
    }

    @Override
    @Lazy BinaryInput in.calc() {
        switch (mode) {
        case None:
        case Sync:
            mode = Sync;
            val stream = new SocketInput();
            return &stream.maskAs(BinaryInput);

        case Async:
            throw new IllegalState("The Socket is already in asynchronous I/O mode");

        case Closed:
            throw new IOClosed();
        }
    }

    @Override
    @Lazy BinaryOutput out.calc() {
        switch (mode) {
        case None:
        case Sync:
            mode = Sync;
            val stream = new SocketOutput();
            return &stream.maskAs(BinaryOutput);

        case Async:
            throw new IllegalState("The Socket is already in asynchronous I/O mode");

        case Closed:
            throw new IOClosed();
        }
    }

    @Override
    void shutdownInput() {
        nativeShutdownInput();
    }

    @Override
    void shutdownOutput() {
        nativeShutdownOutput();
    }

    @Override
    void close(Exception? cause = Null) {
        mode = Closed;
        nativeClose();
    }

    @Override
    String toString() {
        return "Socket";
    }


    // ----- SocketChannel class -------------------------------------------------------------------

    /**
     * TODO
     */
    class SocketChannel(Channel rawChannel)
            delegates Channel(rawChannel) {
        // TODO
    }

    // ----- SocketInput class ---------------------------------------------------------------------

    /**
     * Blocking [BinaryInput] over the native TCP socket.
     */
    class SocketInput
            implements BinaryInput {
        private Boolean reachedEof = False;

        @Override
        @RO Boolean eof.get() {
            return reachedEof;
        }

        @Override
        @RO Int available.get() {
            return nativeAvailable();
        }

        @Override
        Byte readByte() {
            Byte[] got = nativeReadBytes(1);
            if (got.size == 0) {
                reachedEof = True;
                throw new EndOfFile();
            }
            return got[0];
        }

        @Override
        immutable Byte[] readBytes(Int count) {
            if (count <= 0) {
                return [];
            }
            Byte[] got = nativeReadBytes(count);
            if (got.size < count) {
                reachedEof = True;
            }
            return got.freeze(inPlace=True);
        }
    }


    // ----- SocketInput class ---------------------------------------------------------------------

    /**
     * Blocking [BinaryOutput] over the native TCP socket.
     */
    class SocketOutput
            implements BinaryOutput {
        @Override
        void writeByte(Byte value) {
            nativeWriteBytes([value].freeze(inPlace=True), 0, 1);
        }

        @Override
        void writeBytes(Byte[] bytes, Int offset, Int count) {
            // Service calls cannot take a mutable array; copy if needed.
            nativeWriteBytes(bytes.freeze(inPlace=False), offset, count);
        }
    }


    // ----- internal ------------------------------------------------------------------------------

    Byte[] nativeReadBytes(Int count) {TODO("Native");}

    void nativeWriteBytes(Byte[] bytes, Int offset, Int count) {TODO("Native");}

    Int nativeAvailable() {TODO("Native");}

    void nativeShutdownInput() {TODO("Native");}

    void nativeShutdownOutput() {TODO("Native");}

    void nativeClose() {TODO("Native");}
}
