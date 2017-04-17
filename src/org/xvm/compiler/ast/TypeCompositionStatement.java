package org.xvm.compiler.ast;


import org.xvm.asm.ErrorList;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.StructureContainer;

import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A type declaration.
 *
 * @author cp 2017.03.28
 */
public class TypeCompositionStatement
        extends StructureContainerStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypeCompositionStatement(List<Token> modifiers,
                                    List<Annotation> annotations,
                                    Token category,
                                    Token name,
                                    List<Token> qualified,
                                    List<Parameter> typeParams,
                                    List<Parameter> constructorParams,
                                    List<Composition> composition,
                                    StatementBlock body,
                                    Token doc)
        {
        this.modifiers         = modifiers;
        this.annotations       = annotations;
        this.category          = category;
        this.name              = name;
        this.qualified         = qualified;
        this.typeParams        = typeParams;               
        this.constructorParams = constructorParams;        
        this.composition       = composition;              
        this.body              = body;
        this.doc               = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public Token getCategory()
        {
        return category;
        }

    public String getName()
        {
        if (category.getId() == Token.Id.MODULE)
            {
            StringBuilder sb = new StringBuilder();
            for (Token suffix : qualified)
                {
                sb.append('.')
                  .append(suffix.getValue());
                }
            return sb.substring(1).toString();
            }
        else
            {
            return (String) name.getValue();
            }
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * Add an enclosed type composition to this type composition. Because the parser may have to
     * wrap the parsed type composition into a statement block, this method takes a Statement
     * instead of a TypeCompositionStatement, but the idea is the same: the argument to this method
     * should be an object that was returned from {@link org.xvm.compiler.Parser#parseSource()}.
     *
     * @param stmt  a statement returned from {@link org.xvm.compiler.Parser#parseSource()}
     */
    public void addEnclosed(Statement stmt)
        {
        if (enclosed == null)
            {
            if (body == null)
                {
                body = new StatementBlock(new ArrayList<>());
                }

            enclosed = new StatementBlock(new ArrayList<>());
            body.addStatement(enclosed);
            }

        enclosed.addStatement(stmt);
        }

    /**
     * Provide the file structure that will contain the module.
     *
     * @param struct the ModuleStructure for this module
     */
    public void setModuleStructure(ModuleStructure struct)
        {
        setStructure(struct);
        }

    @Override
    protected AstNode registerNames(AstNode parent, ErrorList errs)
        {
        setParent(parent);

        // create the structure for this package or class (etc.)
        if (getStructure() == null)
            {
            // create a structure for this type
            StructureContainer container = parent.getStructure();
            switch (category.getId())
                {
                case PACKAGE:
                    if (container instanceof StructureContainer.PackageContainer)
                        {
                        setStructure(((StructureContainer.PackageContainer) container).ensurePackage((String) name.getValue()));
                        }
                    else
                        {
                        // TODO log error
                        throw new UnsupportedOperationException("not a package container: " + container);
                        }
                    break;

                case CLASS:
                case INTERFACE:
                case SERVICE:
                case CONST:
                case ENUM:
                case TRAIT:
                case MIXIN:
                    if (container instanceof StructureContainer.ClassContainer)
                        {
                        setStructure(((StructureContainer.ClassContainer) container).ensureClass((String) name.getValue()));
                        }
                    else
                        {
                        // TODO log error
                        throw new UnsupportedOperationException("not a package container: " + container);
                        }
                    break;

                default:
                    // TODO log error
                    throw new UnsupportedOperationException("not sure how to make a structure for: " + category.getId().TEXT);
                }
            }

        // recurse to children
        // TODO what if one of them changes?
        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                annotation.registerNames(this, errs);
                }
            }
        if (typeParams != null)
            {
            for (Parameter parameter : typeParams)
                {
                parameter.registerNames(this, errs);
                }
            }
        if (constructorParams != null)
            {
            for (Parameter parameter : constructorParams)
                {
                parameter.registerNames(this, errs);
                }
            }
        if (composition != null)
            {
            for (Composition each : composition)
                {
                each.registerNames(this, errs);
                }
            }
        if (body != null)
            {
            body.registerNames(this, errs);
            }

        return this;
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

        sb.append(category.getId().TEXT)
          .append(' ');

        if (qualified == null)
            {
            sb.append(name.getValue());
            }
        else
            {
            boolean first = true;
            for (Token token : qualified)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append('.');
                    }
                sb.append(token.getValue());
                }
            }

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (Parameter param : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.toTypeParamString());
                }
            sb.append('>');
            }

        if (constructorParams != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : constructorParams)
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

        for (Composition composition : this.composition)
            {
            sb.append("\n        ")
              .append(composition);
            }

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
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("typeParams", typeParams);
        map.put("constructorParams", constructorParams);
        map.put("composition", composition);
        map.put("body", body);
        return map;
        }
        

    // ----- fields --------------------------------------------------------------------------------

    protected List<Token>        modifiers;
    protected List<Annotation>   annotations;
    protected Token              category;
    protected Token              name;
    protected List<Token>        qualified;
    protected List<Parameter>    typeParams;
    protected List<Parameter>    constructorParams;
    protected List<Composition>  composition;
    protected StatementBlock     body;
    protected Token              doc;
    protected StatementBlock     enclosed;
    }
