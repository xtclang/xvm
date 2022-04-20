package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import java.nio.file.Path;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native OSFileChannel implementation.
 */
public class xOSFileChannel
        extends xService
    {
    public xOSFileChannel(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeProperty("size");
        markNativeProperty("position");
        markNativeProperty("readable");
        markNativeProperty("writable");

        markNativeMethod("flush", VOID, VOID);
        markNativeMethod("read", null, new String[] {"Boolean", "numbers.Int64"});
        markNativeMethod("read", null, new String[] {"Boolean", "numbers.Int64", "numbers.Int64"});
        markNativeMethod("write", null, INT);
        markNativeMethod("write", null, new String[] {"numbers.Int64", "numbers.Int64"});
        markNativeMethod("close", null, VOID);

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate templateChannel = f_container.getTemplate("fs.FileChannel");

        s_clzOSFileChannel = ensureClass(templateChannel.getCanonicalType());
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (sPropName)
            {
            case "size":
                {
                try
                    {
                    return frame.assignValue(iReturn, xInt64.makeHandle(hChannel.f_channel.size()));
                    }
                catch (IOException e)
                    {
                    return OSFileNode.raisePathException(frame, e, hChannel.f_path);
                    }
                }

            case "position":
                {
                try
                    {
                    return frame.assignValue(iReturn, xInt64.makeHandle(hChannel.f_channel.position()));
                    }
                catch (IOException e)
                    {
                    return OSFileNode.raisePathException(frame, e, hChannel.f_path);
                    }
                }

            case "readable":
                {
                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(hChannel instanceof ReadableByteChannel));
                }

            case "writable":
                {
                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(hChannel instanceof WritableByteChannel));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (method.getName())
            {
            case "write":
                {
                return invokeWrite1(frame, hChannel, ahArg, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int[] aiReturn)
        {
        ChannelHandle hChannel = (ChannelHandle) hTarget;

        switch (method.getName())
            {
            case "write":
                {
                return invokeWriteN(frame, hChannel, ahArg, aiReturn);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Int write(Buffer<Byte> buffer)}.
     */
    protected int invokeWrite1(Frame frame, ChannelHandle hFile, ObjectHandle[] ahArg, int iReturn)
        {
        throw new UnsupportedOperationException("TODO");
        }

    /**
     * Implementation for: {@code (Int, Int) write(Buffer<Byte>[] buffers)}.
     */
    protected int invokeWriteN(Frame frame, ChannelHandle hFile, ObjectHandle[] ahArg, int[] iReturn)
        {
        throw new UnsupportedOperationException("TODO");
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
    public static int createHandle(Frame frame, FileChannel channel, Path path, int iReturn)
        {
        if (iReturn == Op.A_IGNORE)
            {
            return Op.R_NEXT;
            }

        ServiceContext context  = frame.f_context.f_container.createServiceContext(path.toString());
        ChannelHandle  hChannel = new ChannelHandle(s_clzOSFileChannel, context, channel, path.toAbsolutePath());

        return frame.assignValue(iReturn, hChannel);
        }

    /**
     * The handle class.
     */
    static public class ChannelHandle
            extends ServiceHandle
        {
        public final FileChannel f_channel;
        public final Path        f_path;

        public ChannelHandle(TypeComposition clazz, ServiceContext context,
                             FileChannel channel, Path path)
            {
            super(clazz, context);

            f_channel = channel;
            f_path    = path;
            }

        @Override
        public String toString()
            {
            return super.toString() + " " + f_path;
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clzOSFileChannel;
    }