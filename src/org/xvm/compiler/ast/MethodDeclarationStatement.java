package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A method declaration.
 *
 * @author cp 2017.04.03
 */
public class MethodDeclarationStatement
        extends StructureContainerStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MethodDeclarationStatement(List<Token> modifiers,
                                      List<Annotation> annotations,
                                      List<Token> typeVars,
                                      List<TypeExpression> returns,
                                      Token name,
                                      List<TypeExpression> redundant,
                                      List<Parameter> params,
                                      StatementBlock body,
                                      StatementBlock continuation,
                                      Token doc)
        {
        this.modifiers    = modifiers;
        this.annotations  = annotations;
        this.typeVars     = typeVars;
        this.returns      = returns;
        this.name         = name;
        this.redundant    = redundant;
        this.params       = params;
        this.body         = body;
        this.continuation = continuation;
        this.doc          = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

//    @Override
//    protected void registerGlobalNames(AstNode parent, ErrorList errs)
//        {
//        setParent(parent);
//
//        // create the structure for this method
//        if (getStructure() == null)
//            {
//            // create a structure for this type
//            StructureContainer container = parent.getStructure();
//            if (container instanceof StructureContainer.MethodContainer)
//                {
//                ConstantPool.MethodConstant.Builder builder = ((StructureContainer.MethodContainer)
//                        container).methodBuilder((String) name.getValue());
//                if (params != null)
//                    {
//                    for (Parameter parameter : params)
//                        {
//                        // TODO chicken and egg problem: need a type constant
//                        // TODO look at TypeName
//                        // builder.addParameter(, parameter.getName());
//                        }
//                    }
//                if (returns != null)
//                    {
//                    for (TypeExpression type : returns)
//                        {
//                        // TODO chicken and egg problem: need a type constant
//                        // builder.addReturnValue() // also TODO get rid of name on return value
//                        }
//                    }
//                setStructure(builder.ensureMethod());
//                }
//            else
//                {
//                // TODO log error
//                throw new UnsupportedOperationException("not a method container: " + container);
//                }
//            }
//
//        // recurse to children
//        // TODO what if one of them changes?
//        if (annotations != null)
//            {
//            for (Annotation annotation : annotations)
//                {
//                annotation.registerGlobalNames(this, errs);
//                }
//            }
//        if (returns != null)
//            {
//            for (TypeExpression type : returns)
//                {
//                type.registerGlobalNames(this, errs);
//                }
//            }
//        if (params != null)
//            {
//            for (Parameter parameter : params)
//                {
//                parameter.registerGlobalNames(this, errs);
//                }
//            }
//        if (body != null)
//            {
//            body.registerGlobalNames(this, errs);
//            }
//        if (continuation != null)
//            {
//            continuation.registerGlobalNames(this, errs);
//            }
//
//        return this;
//        }


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

        if (typeVars != null)
            {
            sb.append('<');
            boolean first = true;
            for (Token var : typeVars)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(var.getValue());
                }
            sb.append("> ");
            }

        if (returns == null || returns.isEmpty())
            {
            sb.append("Void ");
            }
        else if (returns.size() == 1)
            {
            sb.append(returns.get(0))
                    .append(' ');
            }
        else
            {
            sb.append(" (");
            boolean first = true;
            for (TypeExpression type : returns)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append(") ");
            }

        sb.append(name.getValue());

        if (redundant != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : redundant)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

        if (params != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append(')');
            }

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

        if (body == null)
            {
            sb.append(';');
            }
        else
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
            
            if (continuation != null)
                {
                String sFinally = continuation.toString();
                sb.append("\nfinally");
                if (sFinally.indexOf('\n') >= 0)
                    {
                    sb.append('\n')
                      .append(indentLines(sFinally, "    "));
                    }
                else
                    {
                    sb.append(' ')
                      .append(sFinally);
                    }
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Token>          modifiers;
    protected List<Annotation>     annotations;
    protected List<Token>          typeVars;
    protected List<TypeExpression> returns;
    protected Token                name;
    protected List<TypeExpression> redundant;
    protected List<Parameter>      params;
    protected StatementBlock       body;
    protected StatementBlock       continuation;
    protected Token                doc;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "annotations", "returns", "redundant", "params", "body", "continuation");
    }
