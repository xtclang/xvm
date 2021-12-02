import io.TextPosition;

import reflect.InvalidType;

import src.Lexer.Token;


/**
 * Represents a named type, including optional access, non-narrowing designation, and
 * parameters. For example:
 *
 *     ecstasy.collections.HashMap!<String?, IntLiteral>
 */
const NamedTypeExpression(Token[]?          moduleNames,
                          Token[]           names,
                          Token?            access,
                          Token?            noNarrow,
                          TypeExpression[]? params,
                          TextPosition      end)
        extends TypeExpression
    {
    construct(Token[]           names,
              Token?            access,
              Token?            noNarrow,
              TypeExpression[]? params,
              TextPosition      end)
        {
        construct NamedTypeExpression(Null, names, access, noNarrow, params, end);
        }

    @Override
    TextPosition start.get()
        {
        return moduleNames?[0].start : names[0].start;
        }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        Module? mod;
        if (moduleNames == Null)
            {
            mod = typeSystem.primaryModule;
            }
        else
            {
            // build the module name
            String moduleName = toDotDelimString(moduleNames);
            if (mod := typeSystem.moduleByQualifiedName.get(moduleName))
                {
                }
            else if (!moduleName.indexOf('.'), mod := typeSystem.moduleBySimpleName.get(moduleName))
                {
                }
            else
                {
                // no matching module
                return False;
                }
            }

        // process names
        Type type = &mod.actualType;
        Loop: for (Token name : names)
            {
            if (type := type.childTypes.get(name.valueText))
                {
                }
            // if we are resolving the first name, and no type exists within the module for that
            // name, then the name may be an implicit import
            else if (Loop.first && moduleNames == Null,
                    type := typeSystem.typeForImplicitName(name.valueText))
                {
                }
            else
                {
                return False;
                }
            }

        // process access
        if (access != Null)
            {
            if (Class clz := type.fromClass())
                {
                type = switch (access.id)
                        {
                        case Public:    clz.PublicType;
                        case Protected: clz.ProtectedType;
                        case Private:   clz.PrivateType;
                        case Struct:    clz.StructType;
                        default: assert;
                        };
                }
            else
                {
                return False;
                }
            }

        // process noNarrow
        // -> noNarrow has no meaning at runtime

        // process params
        if (params != Null)
            {
            Type[] paramTypes = new Type[];
            for (Int i : [0..params.size))
                {
                if (Type paramType := params[i].resolveType(typeSystem, hideExceptions))
                    {
                    paramTypes[i] = paramType;
                    }
                else
                    {
                    return False;
                    }
                }
            try
                {
                type = type.parameterize(paramTypes);
                }
            catch (InvalidType e)
                {
                if (hideExceptions)
                    {
                    return False;
                    }

                throw e;
                }
            }

        return True, type;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        if (moduleNames != Null)
            {
            Loop: for (Token token : moduleNames)
                {
                if (!Loop.first)
                    {
                    buf.add('.');
                    }
                token.appendTo(buf);
                }

            buf.add(':');
            }

        Loop: for (Token token : names)
            {
            if (!Loop.first)
                {
                buf.add('.');
                }
            token.appendTo(buf);
            }

        if (access != Null)
            {
            buf.add(':');
            access.id.text.appendTo(buf);
            }

        if (noNarrow != Null)
            {
            noNarrow.id.text.appendTo(buf);
            }

        if (params != Null)
            {
            buf.add('<');
            for (TypeExpression param : params)
                {
                param.appendTo(buf);
                buf.add(',').add(' ');
                }
            buf.truncate(-2).add('>');
            }

        return buf.toString();
        }
    }
