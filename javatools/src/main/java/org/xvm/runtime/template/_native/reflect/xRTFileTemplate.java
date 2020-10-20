package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.mgmt.xRepository;

/**
 * Native FileTemplate implementation.
 */
public class xRTFileTemplate
        extends xRTModuleTemplate
    {
    public static xRTFileTemplate INSTANCE;

    public xRTFileTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        FILE_TEMPLATE_COMPOSITION = ensureClass(getCanonicalType(),
            pool().ensureEcstasyTypeConstant("reflect.FileTemplate"));

        markNativeProperty("mainModule");
        markNativeProperty("resolved");
        markNativeMethod("getModule", STRING, null);
        markNativeMethod("resolve", null, null);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "mainModule":
                {
                return frame.assignValue(iReturn, xRTModuleTemplate.makeHandle(
                    ((FileStructure) hTemplate.getComponent()).getModule()));
                }

            case "resolved":
                {
                return frame.assignValue(iReturn, xBoolean.makeHandle(
                    ((FileStructure) hTemplate.getComponent()).isLinked()));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "getModule":
                {
                FileStructure   file   = (FileStructure) hTemplate.getComponent();
                StringHandle    hName  = (StringHandle) hArg;
                ModuleStructure module = file.getModule(hName.getStringValue());
                return module == null
                    ? frame.raiseException(xException.illegalArgument(frame,
                            "Missing module " + hName.getStringValue()))
                    : frame.assignValue(iReturn, xRTModuleTemplate.makeHandle(module));
                }

            case "resolve":
                {
                FileStructure file = (FileStructure) hTemplate.getComponent();
                if (!file.isLinked())
                    {
                    if (hArg.getTemplate() instanceof xRepository)
                        {
                        file = f_templates.createFileStructure(file.getModule());

                        String sMissing = file.linkModules(f_templates.f_repository, true);
                        if (sMissing != null)
                            {
                            return frame.raiseException("Missing dependent module: " + sMissing);
                            }
                        }
                    else
                        {
                        throw new UnsupportedOperationException("TODO custom repository support");
                        }
                    }
                return frame.assignValue(iReturn, makeHandle(file));
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified file {@link FileStructure}.
     *
     * @param fileStruct  the {@link FileStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(FileStructure fileStruct)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(FILE_TEMPLATE_COMPOSITION, fileStruct);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition FILE_TEMPLATE_COMPOSITION;
    }
