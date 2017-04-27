package org.xvm.compiler.ast;


import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.StructureContainer;
import org.xvm.asm.StructureContainer.ClassContainer;

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
        extends StructureContainerStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public PropertyDeclarationStatement(List<Token>      modifiers,
                                        List<Annotation> annotations,
                                        TypeExpression   type,
                                        Token            name,
                                        Expression       value,
                                        StatementBlock   body,
                                        Token            doc)
        {
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
        if (getParent().getStructure() instanceof MethodStructure)
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

    /**
     * @return the access specifier for the property
     */
    public Access getAccess()
        {
        List<Token> list = modifiers;
        if (list != null && list.isEmpty())
            {
            for (Token token : list)
                {
                switch (token.getId())
                    {
                    case PUBLIC:
                        return Access.PUBLIC;

                    case PROTECTED:
                        return Access.PROTECTED;

                    case PRIVATE:
                        return Access.PRIVATE;
                    }
                }
            }

        return Access.PUBLIC;           // TODO get the parent's access
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(AstNode parent, ErrorListener errs)
        {
        setParent(parent);

        // create the structure for this property
        if (getStructure() == null)
            {
            // create a structure for this type
            String sName = (String) name.getValue();
            StructureContainer container = parent.getStructure();
            if (container instanceof ClassContainer)
                {
                ClassContainer propContainer = (ClassContainer) container;
                // another property by the same name should not already exist
                if (propContainer.getProperty(sName) == null)
                    {
                    PropertyStructure prop = propContainer.createProperty(isStatic(), getAccess(),
                            type.toString(), (String) name.getValue());
                    setStructure(prop);
                    }
                else
                    {
                    errs.log(Severity.ERROR, Compiler.PROP_DUPLICATE, new Object[] {sName},
                            getSource(), name.getStartPosition(), name.getEndPosition());
                    }
                }
            else
                {
                errs.log(Severity.ERROR, Compiler.PROP_UNEXPECTED, new Object[] {sName, container},
                        getSource(), name.getStartPosition(), name.getEndPosition());
                }
            }

        super.registerStructures(parent, errs);
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
