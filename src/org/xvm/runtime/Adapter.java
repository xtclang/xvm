package org.xvm.runtime;


import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.Handy;


/**
 * A temporary intermediary between the RT, the ConstantPool and ClassStructure;
 * FOR SIMULATION ONLY
 */
public class Adapter
    {
    public final ConstantPool f_pool;
    protected final TemplateRegistry f_templates;
    protected final ModuleStructure f_moduleRoot;

    public Adapter(TemplateRegistry registry, ModuleStructure moduleRoot)
        {
        f_templates = registry;
        f_moduleRoot = moduleRoot;
        f_pool = moduleRoot.getConstantPool();
        }

    // get a class type for the specified name in the context of the
    // specified template
    public TypeConstant getClassType(String sName, ClassTemplate template)
        {
        ConstantPool pool = f_pool;

        if (sName.startsWith("@"))
            {
            int ofEnd = sName.indexOf(" ", 1);
            if (ofEnd < 0)
                {
                throw new IllegalArgumentException("Invalid annotation: " + sName);
                }
            TypeConstant typeAnno = getClassType(sName.substring(1, ofEnd), template);
            TypeConstant typeMain = getClassType(sName.substring(ofEnd + 1), template);
            return pool.ensureAnnotatedTypeConstant(typeAnno.getDefiningConstant(), null, typeMain);
            }

        boolean fNullable = sName.endsWith("?");
        if (fNullable)
            {
            sName = sName.substring(0, sName.length() - 1);
            }

        TypeConstant constType = null;

        int ofTypeParam = sName.indexOf('<');
        if (ofTypeParam >= 0)
            {
            String sParam = sName.substring(ofTypeParam + 1, sName.length() - 1);
            String sSimpleName = sName.substring(0, ofTypeParam);

            // TODO: auto-narrowing (ThisTypeConstant)
            if (sSimpleName.endsWith("!"))
                {
                sSimpleName = sSimpleName.substring(0, sSimpleName.length() - 1);
                }

            IdentityConstant idClass = f_templates.getIdentityConstant(sSimpleName);
            if (idClass != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                TypeConstant[] acType = getTypeConstants(template, asType);

                constType = pool.ensureClassTypeConstant(idClass, null, acType);
                }
            }
        else
            {
            if (sName.equals("this"))
                {
                IdentityConstant constId = template == null ?
                    pool.clzObject() : template.f_struct.getIdentityConstant();
                return pool.ensureThisTypeConstant(constId, null);
                }

            if (template != null && template.f_struct.indexOfGenericParameter(sName) >= 0)
                {
                // generic type property
                PropertyStructure prop = (PropertyStructure) template.f_struct.getChild(sName);
                return pool.ensureTerminalTypeConstant(prop.getIdentityConstant());
                }

            Component component = f_moduleRoot.getChildByPath(sName);
            if (component != null)
                {
                IdentityConstant constId = component.getIdentityConstant();
                switch (constId.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                        constType = constId.getType();
                        break;

                    case Typedef:
                        constType = ((TypedefStructure) component).getType();
                        break;
                    }
                }
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
            }

        return fNullable ? constType.ensureNullable() : constType;
        }

    public TypeConstant[] getTypeConstants(ClassTemplate template, String[] asType)
        {
        if (asType == null)
            {
            return null;
            }

        int cTypes = asType.length;
        TypeConstant[] aType = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            aType[i] = getClassType(asType[i].trim(), template);
            }
        return aType;
        }
    }
