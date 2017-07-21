package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Component;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A property declaration.
 *
 * @author cp 2017.04.04
 */
public class PropertyDeclarationStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public PropertyDeclarationStatement(long             lStartPos,
                                        long             lEndPos,
                                        Expression       condition,
                                        List<Token>      modifiers,
                                        List<Annotation> annotations,
                                        TypeExpression   type,
                                        Token            name,
                                        Expression       value,
                                        StatementBlock   body,
                                        Token            doc)
        {
        super(lStartPos, lEndPos);

        this.condition   = condition;
        this.modifiers   = modifiers;
        this.annotations = annotations;
        this.type        = type;
        this.name        = name;
        this.value       = value;
        this.body        = body;
        this.doc         = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the property is declared as static
     */
    public boolean isStatic()
        {
        // properties inside a method are ALWAYS specified as static, but NEVER actually static (in
        // the "constant property" sense)
        if (getParent().getComponent() instanceof MethodStructure)
            {
            return false;
            }

        List<Token> list = modifiers;
        if (list != null && list.isEmpty())
            {
            for (Token token : list)
                {
                if (token.getId() == Token.Id.STATIC)
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    @Override
    public Access getDefaultAccess()
        {
        Access access = getAccess(modifiers);
        return access == null
                ? super.getDefaultAccess()
                : access;
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
        // create the structure for this property
        if (getComponent() == null)
            {
            // create a structure for this type
            String sName = (String) name.getValue();
            Component container = getParent().getComponent();
            if (container.isClassContainer())
                {
                // another property by the same name should not already exist, but  the check for
                // duplicates is deferred, since it is possible (thanks to the complexity of
                // conditionals) to have multiple components occupying the same location within the
                // namespace at this point in the compilation
                // if (container.getProperty(sName) != null) ...

                ConstantPool      pool      = container.getConstantPool();
                TypeConstant      constType = pool.createUnresolvedTypeConstant(type);
                PropertyStructure prop      = container.createProperty(isStatic(), getDefaultAccess(),
                                                                       constType, sName);
                setComponent(prop);
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.PROP_UNEXPECTED, sName, container);
                }
            }

        super.registerStructures(errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (modifiers != null)
            {
            for (Token token : modifiers)
                {
                sb.append(token.getId().TEXT)
                  .append(' ');
                }
            }

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                  .append(' ');
                }
            }

        sb.append(type)
          .append(' ')
          .append(name.getValue());

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        if (value != null)
            {
            sb.append(" = ")
              .append(value)
              .append(";");
            }
        else if (body != null)
            {
            String sBody = body.toString();
            if (sBody.indexOf('\n') >= 0)
                {
                sb.append('\n')
                  .append(indentLines(sBody, "    "));
                }
            else
                {
                sb.append(' ')
                  .append(sBody);
                }
            }
        else
            {
            sb.append(';');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression         condition;
    protected List<Token>        modifiers;
    protected List<Annotation>   annotations;
    protected TypeExpression     type;
    protected Token              name;
    protected Expression         value;
    protected StatementBlock     body;
    protected Token              doc;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PropertyDeclarationStatement.class,
            "annotations", "type", "value", "body");
    }
