package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * A typedef statement specifies a type to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class TypedefStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypedefStatement(Expression cond, Token keyword, TypeExpression type, Token alias)
        {
        super(keyword.getStartPosition(), alias.getEndPosition());

        this.cond     = cond;
        this.modifier = keyword.getId() == Id.TYPEDEF ? null : keyword;
        this.type     = type;
        this.alias    = alias;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Access getDefaultAccess()
        {
        if (modifier != null)
            {
            switch (modifier.getId())
                {
                case PUBLIC:
                    return Access.PUBLIC;
                case PROTECTED:
                    return Access.PROTECTED;
                case PRIVATE:
                    return Access.PRIVATE;
                }
            }

        return super.getDefaultAccess();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(ErrorListener errs)
        {
        // create the structure for this method
        if (getComponent() == null)
            {
            // create a structure for this typedef
            Component container = getParent().getComponent();
            String    sName     = (String) alias.getValue();
            if (container.isClassContainer())
                {
                Access           access    = getDefaultAccess();
                TypeConstant     constType = type.ensureTypeConstant(); // TODO or ensureIdentityConstant();
                TypedefStructure typedef   = container.createTypedef(access, constType, sName);
                setComponent(typedef);
                }
            else
                {
                // TODO need a "typedef unexpected" error code
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.PROP_UNEXPECTED, sName, container);
                throw new UnsupportedOperationException("not a typedef container: " + container);
                }
            }

        super.registerStructures(errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (cond != null)
            {
            sb.append("if (")
              .append(cond)
              .append(") { ");
            }

        if (modifier != null)
            {
            sb.append(modifier)
              .append(' ');
            }

        sb.append("typedef ")
          .append(type)
          .append(' ')
          .append(alias.getValue())
          .append(';');

        if (cond != null)
            {
            sb.append(" }");
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     cond;
    protected Token          modifier;
    protected Token          alias;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypedefStatement.class, "cond", "type");
    }
