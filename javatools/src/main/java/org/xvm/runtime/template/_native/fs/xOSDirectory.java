package org.xvm.runtime.template._native.fs;


import java.io.File;
import java.io.IOException;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

import java.nio.file.attribute.BasicFileAttributes;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;


/**
 * Native OSDirectory implementation.
 */
public class xOSDirectory
        extends OSFileNode
    {
    public static xOSDirectory INSTANCE;

    public xOSDirectory(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeMethod("deleteRecursively", null, null);
        markNativeMethod("filesRecursively", null, null);   // TODO as a natural implementation?
        markNativeMethod("watchRecursively", null, null);

        getCanonicalType().invalidateTypeInfo();

        s_constructor = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;

        switch (method.getName())
            {
            case "watchRecursively":  // (FileWatcher)
                // TODO GG
                throw new UnsupportedOperationException("watchRecursively");
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;

        switch (method.getName())
            {
            case "deleteRecursively":
                {
                Path pathDir = hNode.f_path;
                File file    = pathDir.toFile();
                if (!file.isDirectory())
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                try
                    {
                    Files.walkFileTree(pathDir,
                        new SimpleFileVisitor<>()
                            {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                    throws IOException
                               {
                               Files.delete(file);
                               return FileVisitResult.CONTINUE;
                               }

                           @Override
                           public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                                    throws IOException
                               {
                               Files.delete(dir);
                               return FileVisitResult.CONTINUE;
                               }
                        });
                    return frame.assignValue(iReturn, xBoolean.TRUE);
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hNode.f_path);
                    }
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Construct a new {@link NodeHandle} representing the specified directory.
     *
     * @param frame      the current frame
     * @param hOSStore   the "host" OSStore handle
     * @param path       the node's path
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, ObjectHandle hOSStore, Path path, int iReturn)
        {
        TypeComposition clz = ensureClass(frame.f_context.f_container,
                                    getCanonicalType(), frame.poolContext().typeDirectory());

        NodeHandle     hStruct = new NodeHandle(clz.ensureAccess(Constants.Access.STRUCT),
                                        path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());

        return proceedConstruction(frame, s_constructor, true, hStruct, ahVar, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static MethodStructure s_constructor;
    }