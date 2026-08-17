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
            BinaryInput stream = new SocketInput();
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
            BinaryOutput stream = new SocketOutput();
            return &stream.maskAs(BinaryOutput);

        case Async:
            throw new IllegalState("The Socket is already in asynchronous I/O mode");

        case Closed:
            throw new IOClosed();
        }
    }

    @Override
    void shutdownInput() {
        shutdownInputImpl();
    }

    @Override
    void shutdownOutput() {
        shutdownOutputImpl();
    }

    @Override
    void close(Exception? cause = Null) {
        mode = Closed;
        closeImpl();
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
            return availableImpl();
        }

        @Override
        Byte readByte() {
            Byte[] bytes = readBytesImpl(1);
            if (bytes.size == 0) {
                reachedEof = True;
                throw new EndOfFile();
            }
            return bytes[0];
        }

        @Override
        immutable Byte[] readBytes(Int count) {
            assert:arg count >= 0;

            if (count == 0) {
                return [];
            }
            Byte[] bytes = readBytesImpl(count);
            if (bytes.size < count) {
                reachedEof = True;
            }
            return bytes.freeze(inPlace=True);
        }
    }


    // ----- SocketOutput class --------------------------------------------------------------------

    /**
     * Blocking [BinaryOutput] over the native TCP socket.
     */
    class SocketOutput
            implements BinaryOutput {
        @Override
        void writeByte(Byte value) {
            writeBytesImpl([value], 0, 1);
        }

        @Override
        void writeBytes(Byte[] bytes, Int offset, Int count) {
            // service calls cannot take a mutable array; bytes is already immutable here
            writeBytesImpl(bytes, offset, count);
        }
    }


    // ----- internal ------------------------------------------------------------------------------

    private Byte[] readBytesImpl(Int count) {TODO("Native");}

    private void writeBytesImpl(Byte[] bytes, Int offset, Int count) {TODO("Native");}

    private Int availableImpl() {TODO("Native");}

    private void shutdownInputImpl() {TODO("Native");}

    private void shutdownOutputImpl() {TODO("Native");}

    private void closeImpl() {TODO("Native");}
}
