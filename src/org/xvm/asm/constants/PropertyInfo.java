package org.xvm.asm.constants;


import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.PropertyStructure;


/**
 * Represents a single property.
 */
public class PropertyInfo
    {
    public PropertyInfo(Constant constDeclares, PropertyStructure struct)
        {
        // TypeConstant typeProp, String sName
        constDeclLevel = constDeclares;
        type           = struct.getType();
        name           = struct.getName();
        access         = struct.getAccess();
        }

    public String getName()
        {
        return name;
        }

    public TypeConstant getType()
        {
        return type;
        }

    public Access getAccess()
        {
        return access;
        }

    // TODO need a way to widen access from protected to public

    public boolean isReadOnly()
        {
        return fRO;
        }

    void markReadOnly()
        {
        fRO = true;
        }

    public boolean isFieldRequired()
        {
        return fField;
        }

    void markFieldRequired()
        {
        fField = true;
        }

    public boolean isCustomLogic()
        {
        return fLogic;
        }

    void markCustomLogic()
        {
        fLogic = true;
        }

    public boolean isAnnotated()
        {
        return fAnnotated;
        }

    void markAnnotated()
        {
        fAnnotated = true;
        }

    public Constant getDeclarationLevel()
        {
        return constDeclLevel;
        }

    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (fRO)
            {
            sb.append("@RO ");
            }

        sb.append(type.getValueString())
                .append(' ')
                .append(name);

        return sb.toString();
        }


    // -----fields ---------------------------------------------------------------------------------

    private String                             name;
    private TypeConstant                       type;
    private Access                             access;
    private boolean                            fRO;
    private boolean                            fField;
    private boolean                            fLogic;
    private boolean                            fAnnotated;
    private Constant                           constDeclLevel;
    private Map<SignatureConstant, MethodInfo> methods;
    }
