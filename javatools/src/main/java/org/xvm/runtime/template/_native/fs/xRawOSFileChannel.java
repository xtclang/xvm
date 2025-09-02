package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import java.nio.file.Path;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate;
import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;

import org.xvm.runtime.template._native.io.xRawChannel;


/**
 * Native RawOSFileChannel implementation.
 */
public class xRawOSFileChannel
        extends xRawChannel {
    public static xRawOSFileChannel INSTANCE;

    public xRawOSFileChannel(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        // RawOSFileChannel
        markNativeProperty("size");
        markNativeProperty("position");
        markNativeMethod("flush", VOID, VOID);

        // RawChannel
        markNativeProperty("readable");
        markNativeProperty("writable");
        markNativeProperty("closed");

        markNativeMethod("take", VOID, null);
        markNativeMethod("submit", null, INT);

        markNativeMethod("close", VOID, VOID);

        super.initNative();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (sPropName) {
        case "size":
            try {
                return frame.assignValue(iReturn, xInt64.makeHandle(hChannel.f_channel.size()));
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }

        case "position":
            try {
                return frame.assignValue(iReturn, xInt64.makeHandle(hChannel.f_channel.position()));
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }

        case "readable":
            return frame.assignValue(iReturn,
                xBoolean.makeHandle(hChannel.f_channel instanceof ReadableByteChannel));

        case "writable":
            return frame.assignValue(iReturn,
                xBoolean.makeHandle(hChannel.f_channel instanceof WritableByteChannel));

        case "closed":
            return frame.assignValue(iReturn,
                xBoolean.makeHandle(!hChannel.f_channel.isOpen()));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue) {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (sPropName) {
        case "size":
            try {
                long cSize = ((JavaLong) hValue).getValue();
                hChannel.f_channel.truncate(cSize);
                return Op.R_NEXT;
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }

        case "position":
            try {
                long nPosition = ((JavaLong) hValue).getValue();
                hChannel.f_channel.position(nPosition);
                return Op.R_NEXT;
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }
        }

        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
    }


    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (method.getName()) {
        case "take":
            return invokeTake(frame, hChannel, iReturn);

        case "submit":
            return invokeSubmit(frame, hChannel, ahArg, iReturn);

        case "flush":
            try {
                hChannel.f_channel.force(false); // no metadata
                return Op.R_NEXT;
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }

        case "close":
            try {
                hChannel.f_channel.close();
                return Op.R_NEXT;
            } catch (IOException e) {
                return xOSFileNode.raisePathException(frame, e, hChannel.f_path);
            }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Byte[]|Int take()}.
     */
    protected int invokeTake(Frame frame, ChannelHandle hChannel, int iReturn) {
        // we use the HeapByteBuffer as a thin wrapper around the underlying byte array
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        Callable<Integer> task = () -> {
            try {
                return hChannel.f_channel.read(buffer);
            } catch (NonReadableChannelException e) {
                return -2; // InputShutdown
            } catch (ClosedChannelException e) {
                return -3; // closed
            }
        };

        CompletableFuture<Integer> cfRead = frame.f_context.f_container.scheduleIO(task);

        Frame.Continuation continuation = frameCaller -> {
            try {
                int cbRead = cfRead.get().intValue();
                return frameCaller.assignValue(iReturn, cbRead < 0
                        ? xInt64.makeHandle(-1)
                        : xArray.makeByteArrayHandle(buffer.array(), cbRead, Mutability.Constant));
            } catch (InterruptedException | ExecutionException e) {
                return xOSFileNode.raisePathException(frameCaller, e, hChannel.f_path);
            }
        };

        return frame.waitForIO(cfRead, continuation);
    }

    /**
     * Implementation for: {@code Int submit(Byte[] buffer, Int start, Int end)}.
     */
    protected int invokeSubmit(Frame frame, ChannelHandle hChannel, ObjectHandle[] ahArg, int iReturn) {
        if (!hChannel.f_channel.isOpen()) {
            return frame.assignValue(iReturn, xInt64.makeHandle(-1)); // closed
        }

        ArrayHandle hArray = (ArrayHandle) ahArg[0];
        JavaLong    hStart = (JavaLong)    ahArg[1];
        JavaLong    hEnd   = (JavaLong)    ahArg[2];

        ByteArrayHandle hDelegate = (ByteArrayHandle) hArray.m_hDelegate;
        int             ofStart   = (int) hStart.getValue();
        int             ofEnd     = (int) hEnd.getValue();

        // we use the HeapByteBuffer as a thin wrapper around the underlying byte array
        ByteBuffer buffer = ((ByteBasedDelegate) hDelegate.getTemplate()).
                                wrap(hDelegate, ofStart, ofEnd - ofStart);

        Callable<Integer> task = () -> hChannel.f_channel.write(buffer);

        frame.f_context.f_container.scheduleIO(task); // don't wait

        return frame.assignValue(iReturn, xInt64.makeHandle(0)); // OK
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Construct a new {@link ChannelHandle} representing the specified file.
     *
     * @param frame    the current frame
     * @param channel  the channel
     * @param path     the channel's path
     * @param iReturn  the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, FileChannel channel, Path path, int iReturn) {
        if (iReturn == Op.A_IGNORE) {
            return Op.R_NEXT;
        }

        Container      container = frame.f_context.f_container;
        ServiceContext context   = container.createServiceContext(path.toString());
        ChannelHandle  hChannel  = new ChannelHandle(getCanonicalClass(),
                context, channel, path.toAbsolutePath());

        // this should come from the config
        int cbPreferredSize = 8192;
        try {
            cbPreferredSize = Math.max(1024, Math.min(cbPreferredSize, (int) channel.size()));
        } catch (IOException ignore) {}
        hChannel.setPreferredBufferSize(cbPreferredSize);

        return frame.assignValue(iReturn, hChannel);
    }

    /**
     * The handle class for RawOSFileChannel.
     */
    public static class ChannelHandle
            extends xRawChannel.ChannelHandle {
        public final FileChannel f_channel;
        public final Path        f_path;

        public ChannelHandle(TypeComposition clazz, ServiceContext context,
                             FileChannel channel, Path path) {
            super(clazz, context);

            f_channel = channel;
            f_path    = path;
        }

        @Override
        public String toString() {
            return super.toString() + " " + f_path;
        }
    }
}