package org.xvm.compiler.ast;


import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.StructureContainer;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

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

    public TypeCompositionStatement(Source            source,
                                    List<Token>       modifiers,
                                    List<Annotation>  annotations,
                                    Token             category,
                                    Token             name,
                                    List<Token>       qualified,
                                    List<Parameter>   typeParams,
                                    List<Parameter>   constructorParams,
                                    List<Composition> composition,
                                    StatementBlock    body,
                                    Token             doc)
        {
        this.source            = source;
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

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
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
     * Instantiate and populate the initial FileStructure for this module.
     *
     * @return  a new FileStructure for this module, with the module, packages, and classes
     *          registered
     */
    public FileStructure createModuleStructure(ErrorListener errorList)
        {
        assert category.getId() == Token.Id.MODULE;
        assert getStructure() == null;

        FileStructure struct = new FileStructure(getName());
        setStructure((ModuleStructure) struct.getMainModule());

        super.registerStructures(null, errorList);

        return struct;
        }

    @Override
    protected void registerStructures(AstNode parent, ErrorListener errs)
        {
        if (parent.getStructure() != null)
            {
            // create the structure for this package or class (etc.)
            assert getStructure() == null;

            StructureContainer container = parent.getStructure();
            switch (category.getId())
                {
                case MODULE:
                    errs.log(Severity.ERROR, Compiler.MODULE_UNEXPECTED, null,
                            getSource(), category.getStartPosition(), category.getEndPosition());
                    break;

                case PACKAGE:
                    if (container instanceof StructureContainer.PackageContainer)
                        {
                        // TODO check for duplicate
                        setStructure(((StructureContainer.PackageContainer) container).ensurePackage((String) name.getValue()));
                        }
                    else
                        {
                        errs.log(Severity.ERROR, Compiler.PACKAGE_UNEXPECTED, new String[] {container.toString()},
                                getSource(), category.getStartPosition(), category.getEndPosition());
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
                        // TODO check for duplicate
                        setStructure(((StructureContainer.ClassContainer) container).ensureClass((String) name.getValue()));
                        }
                    else
                        {
                        errs.log(Severity.ERROR, Compiler.CLASS_UNEXPECTED, new String[] {container.toString()},
                                getSource(), category.getStartPosition(), category.getEndPosition());
                        }
                    break;

                default:
                    throw new UnsupportedOperationException("unable to guess structure for: " + category.getId().TEXT);
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


    // ----- fields --------------------------------------------------------------------------------

    protected Source             source;
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypeCompositionStatement.class,
            "annotations", "typeParams", "constructorParams", "composition", "body");
    }
