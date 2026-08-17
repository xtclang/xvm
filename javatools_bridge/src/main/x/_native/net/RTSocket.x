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
    typedef immutable Byte[] as Binary;

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
    void shutdownInput() = shutdownInputImpl();

    @Override
    void shutdownOutput() = shutdownOutputImpl();

    @Override
    void close(Exception? cause = Null) {
        mode = Closed;
        closeImpl();
    }

    @Override
    String toString() = "Socket";

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
     * Non-blocking [BinaryInput] over the native TCP socket. Concurrent reads are processed in
     * invocation order.
     */
    @Concurrent
    class SocketInput
            implements BinaryInput {
        /**
         * The tail of the ordered native read operations.
         */
        private Future<Binary>? pendingRead = Null;

        @Override
        public/private Boolean eof = False;

        @Override
        @RO Int available.get() = availableImpl();

        @Override
        Byte readByte() {
            return readAsync(1).transform(bytes -> {
                if (bytes.size == 0) {
                    eof = True;
                    throw new EndOfFile();
                }
                return bytes[0];
            });
        }

        @Override
        Binary readBytes(Int count) {
            assert:arg count >= 0;

            if (count == 0) {
                return [];
            }
            return readAsync(count).transform(bytes -> {
                if (bytes.size < count) {
                    eof = True;
                }
                return bytes;
            });
        }

        /**
         * Schedule a native read after any pending read.
         */
        private Future<Binary> readAsync(Int count) {
            if (Future<Binary> previous ?= pendingRead, !previous.assigned) {
                @Future Binary result;
                Future<Binary> future = &result;
                previous.whenComplete((_, _) -> {
                    try {
                        result = readBytesImpl^(count);
                    } catch (Exception e) {
                        &result.completeExceptionally(e);
                    }
                });
                return pendingRead <- future;
            }

            @Future Binary result = readBytesImpl^(count);
            Future<Binary> future = &result;
            return pendingRead <- future;
        }
    }

    // ----- SocketOutput class --------------------------------------------------------------------

    /**
     * Non-blocking [BinaryOutput] over the native TCP socket. Concurrent writes are processed in
     * invocation order.
     */
    @Concurrent
    class SocketOutput
            implements BinaryOutput {
        /**
         * The tail of the ordered native write operations.
         */
        private Future<Tuple>? pendingWrite = Null;

        @Override
        void writeByte(Byte value) = writeAsync([value], 0, 1);

        @Override
        void writeBytes(Byte[] bytes, Int offset, Int count) = writeAsync(bytes, offset, count);

        /**
         * Schedule a native write after any pending write.
         */
        private Future<Tuple> writeAsync(Byte[] bytes, Int offset, Int count) {
            if (Future<Tuple> previous ?= pendingWrite, !previous.assigned) {
                @Future Tuple result;
                Future<Tuple> future = &result;
                previous.whenComplete((_, _) -> {
                    try {
                        result = writeBytesImpl^(bytes, offset, count);
                    } catch (Exception e) {
                        &result.completeExceptionally(e);
                    }
                });
                return pendingWrite <- future;
            }

            @Future Tuple result = writeBytesImpl^(bytes, offset, count);
            Future<Tuple> future = &result;
            return pendingWrite <- future;
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    private Binary readBytesImpl(Int count) {TODO("Native");}

    private void writeBytesImpl(Byte[] bytes, Int offset, Int count) {TODO("Native");}

    private Int availableImpl() {TODO("Native");}

    private void shutdownInputImpl() {TODO("Native");}

    private void shutdownOutputImpl() {TODO("Native");}

    private void closeImpl() {TODO("Native");}
}
