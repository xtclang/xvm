package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;


/**
 * Native base for OSFile and OSDirectory implementations.
 */
public class xOSFileNode
        extends xConst
    {
    public xOSFileNode(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeProperty("pathString");
        markNativeProperty("exists");
        markNativeProperty("readable");
        markNativeProperty("writable");
        markNativeProperty("createdMillis");
        markNativeProperty("accessedMillis");
        markNativeProperty("modifiedMillis");
        markNativeProperty("size");

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "pathString":
                return frame.assignValue(iReturn, xString.makeHandle(hNode.f_path.toString()));

            case "exists":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().exists()));

            case "readable":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().canRead()));

            case "writable":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().canWrite()));

            case "createdMillis":
                {
                try
                    {
                    BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.creationTime().toMillis()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode.f_path);
                    }
                }

            case "accessedMillis":
                {
                try
                    {
                    BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.lastAccessTime().toMillis()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode.f_path);
                    }
                }

            case "modifiedMillis":
                {
                try
                    {
                    BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.lastModifiedTime().toMillis()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode.f_path);
                    }
                }

            case "size":
                {
                try
                    {
                    BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.size()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode.f_path);
                    }
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    /**
     * Construct a new {@link NodeHandle} representing the specified file or directory.
     *
     * @param frame      the current frame
     * @param hOSStore   the "host" OSStore handle
     * @param path       the node's path
     * @param fDir       true iff the path represents a directory; false otherwise
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    static int createHandle(Frame frame, ObjectHandle hOSStore, Path path, boolean fDir, int iReturn)
        {
        return fDir
            ? xOSDirectory.INSTANCE.createHandle(frame, hOSStore, path, iReturn)
            : xOSFile     .INSTANCE.createHandle(frame, hOSStore, path, iReturn);
        }


    // ----- helper methods ------------------------------------------------------------------------

    public static int raisePathException(Frame frame, Throwable e, Path path)
        {
        if (e instanceof ExecutionException ee)
            {
            e = ee.getCause();
            if (e instanceof IOException ioe)
                {
                return raisePathException(frame, ioe, path);
                }
            }

        return frame.raiseException(e.getMessage());
        }

    public static int raisePathException(Frame frame, IOException e, Path path)
        {
        // TODO: how to get the natural Path efficiently from path?
        // TODO: consider translating IOExceptions into corresponding natural exceptions

        // strip the exception name from the exception class and prepend to the message
        Class<? extends IOException> clzException = e.getClass();
        String sException  = clzException == IOException.class
                ? ""
                : clzException.getSimpleName().replace("Exception", "") + ": ";
        return frame.raiseException(
            xException.pathException(frame, sException + e.getMessage(), xNullable.NULL));
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class NodeHandle
            extends GenericHandle
        {
        protected final Path f_path;

        // TODO: lazy file channel, etc.

        protected NodeHandle(TypeComposition clazz, Path path, ObjectHandle hOSStore)
            {
            super(clazz);

            f_path = path;

            setField(null, "store", hOSStore);
            }

        public Path getPath()
            {
            return f_path;
            }

        @Override
        public String toString()
            {
            return super.toString() + " " + f_path;
            }
        }
    }