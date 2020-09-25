package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;


/**
 * Native base for OSFile and OSDirectory implementations.
 */
public abstract class OSFileNode
        extends xConst
    {
    protected OSFileNode(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        // common native properties between OSFile and OSDirectory
        markNativeProperty("store");
        markNativeProperty("pathString");
        markNativeProperty("exists");
        markNativeProperty("readable");
        markNativeProperty("writable");
        markNativeProperty("createdMillis");
        markNativeProperty("accessedMillis");
        markNativeProperty("modifiedMillis");
        markNativeProperty("size");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "store":
                return frame.assignValue(iReturn, hNode.getField(sPropName));

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
                    return raisePathException(frame, e, hNode);
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
                    return raisePathException(frame, e, hNode);
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
                    return raisePathException(frame, e, hNode);
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
                    return raisePathException(frame, e, hNode);
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

    protected int raisePathException(Frame frame, IOException e, NodeHandle hNode)
        {
        // TODO: how to get the natural Path efficiently from hNode.f_path?
        return frame.raiseException(xException.pathException(frame, e.getMessage(), xNullable.NULL));
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

            setField("store", hOSStore);
            }

        public Path getPath()
            {
            return f_path;
            }
        }
    }
