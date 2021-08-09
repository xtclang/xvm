import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.ClassTemplate.Contribution;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.MultiMethodTemplate;
import ecstasy.reflect.ParameterTemplate;
import ecstasy.reflect.PropertyTemplate;
import ecstasy.reflect.TypeParameter;
import ecstasy.reflect.TypeTemplate;

import oodb.Connection;
import oodb.DBObject;
import oodb.DBObject.DBCategory;
import oodb.DBUser;

import imdb.CatalogMetadata;

/**
 * Host for imdb-based db module.
 */
class ImdbHost
        extends DbHost
    {
    @Inject Console console;

    @Override
    ModuleTemplate generateStubs(ModuleRepository repository, String dbModuleName, Directory buildDir)
        {
        ModuleTemplate dbModule = repository.getResolvedModule(dbModuleName);

        String appName = dbModuleName; // TODO GG: allow fully qualified name

        Directory moduleDir = buildDir.dirFor(appName + "_imdb");
        if (moduleDir.exists)
            {
            moduleDir.deleteRecursively();
            }
        moduleDir.create();

        ClassTemplate appSchemaTemplate;
        if (appSchemaTemplate := findSchema(dbModule)) {}
        else
            {
            throw new IllegalState($"Schema is not found in {dbModuleName} module");
            }

        File moduleFile = moduleDir.fileFor("module.x");

        createModule(moduleFile, appName, dbModule, appSchemaTemplate);

        // temporary; replace with the compilation of generated source
        return repository.getModule(dbModuleName + "_imdb");
        }

    conditional ClassTemplate findSchema(ModuleTemplate dbModule)
        {
        Class         schemaClass    = oodb.RootSchema;
        ClassTemplate schemaTemplate = schemaClass.baseTemplate;

        for (ClassTemplate classTemplate : dbModule.classes)
            {
            if (classTemplate.format == Interface)
                {
                for (Contribution contrib : classTemplate.contribs)
                    {
                    if (contrib.action == Implements)
                        {
                        ClassTemplate template = contrib.ingredient.as(ClassTemplate);
                        if (template == schemaTemplate)
                            {
                            return (True, classTemplate);
                            }
                        }
                    }
                }
            }
        return False;
        }

    /**
     * Create module.x source file.
     */
    void createModule(File moduleFile, String appName,
                      ModuleTemplate moduleTemplate, ClassTemplate appSchemaTemplate)
        {
        String appSchema = appSchemaTemplate.name;

        Tuple<PropertyTemplate, DBCategory>[] dbProps = collectDBProps(appSchemaTemplate);

        String propertyInfos        = "";
        String propertyGetters      = "";
        String customInstantiations = "";
        String customDeclarations   = "";

        for (Tuple<PropertyTemplate, DBCategory> propInfo : dbProps)
            {
            PropertyTemplate property = propInfo[0];
            DBCategory       category = propInfo[1];

            TypeTemplate typeTemplate = property.type;

            // already checked at collectDBProps()
            assert Composition classTemplate := typeTemplate.fromClass(),
                   classTemplate.is(ClassTemplate);

            String propertyName = property.name;
            String propertyType = displayName(classTemplate, appName);

            String propertyInfo = $./templates/imdb/PropertyInfo.txt;

            propertyInfos += propertyInfo
                                .replace("%propertyName%"    , propertyName)
                                .replace("%propertyCategory%", category.name)
                                .replace("%propertyType%"    , propertyType)
                                ;

            String propertyGetter = $./templates/imdb/PropertyGetter.txt;

            propertyGetters += propertyGetter
                                .replace("%appName%"     , appName)
                                .replace("%propertyName%", propertyName)
                                .replace("%propertyType%", propertyType)
                                ;

            if (classTemplate.containingModule != moduleTemplate)
                {
                continue;
                }

            String propertyTypeName = classTemplate.name.replace(".", "_");
            String propertyStoreType;
            String propertyBaseType;

            String customInstantiation = $./templates/imdb/CustomInstantiation.txt;
            String customDeclaration   = $./templates/imdb/CustomDeclaration.txt;

            switch (category)
                {
                case DBMap:
                    assert TypeTemplate keyType   := typeTemplate.resolveFormalType("Key");
                    assert TypeTemplate valueType := typeTemplate.resolveFormalType("Value");

                    String keyTypeName   = displayName(keyType, appName);
                    String valueTypeName = displayName(valueType, appName);

                    propertyStoreType = $"imdb_.storage.DBMapStore<{keyTypeName}, {valueTypeName}>";
                    propertyBaseType  = $"DBMapImpl<{keyTypeName}, {valueTypeName}>";
                    break;

                case DBCounter:
                    propertyStoreType = "imdb_.storage.DBCounterStore";
                    propertyBaseType  = "DBCounterImpl";
                    break;

                default:
                    TODO
                }

            String customMethods     = "";
            String customInvocations = "";

            for (MultiMethodTemplate multimethod : classTemplate.multimethods)
                {
                String methodName = multimethod.name;
                for (MethodTemplate method : multimethod.children())
                    {
                    if (!method.isConstructor && !method.isStatic && method.access == Public)
                        {
                        String customMethod     = $./templates/imdb/CustomMethod.txt;
                        String customInvocation = $./templates/imdb/CustomInvocation.txt;

                        ParameterTemplate[] params  = method.parameters;
                        ParameterTemplate[] returns = method.returns;

                        String retType = switch (returns.size)
                                {
                                case 0 : "void";
                                case 1 : displayName(returns[0].type, appName);
                                default: $"({{for (val r : returns) {$.addAll(displayName(r.type, appName)); $.add(',');} }})";

// TODO CP: the equivalent multi-line doesn't parse
//                                default: $|({{for (val r : returns)
//                                          |    {
//                                          |    $.addAll(displayName(r.type, appName));
//                                          |    $.add(',');
//                                          |    }
//                                          |}})
//                                          ;
                                };

                        String argDecl     = "";
                        String args        = "";
                        String argTypes    = "";
                        String tupleValues = "";
                        switch (params.size)
                            {
                            case 0:
                                break;

                            case 1:
                                args        = params[0].name? : assert;
                                argTypes    = displayName(params[0].type, appName);
                                argDecl     = $"{argTypes} {args}";
                                tupleValues = "args[0]";
                                break;

                            default:
                                Loop:
                                for (ParameterTemplate param : params)
                                    {
                                    String name = param.name? : assert;
                                    String type = displayName(param.type, appName);

                                    if (!Loop.first)
                                        {
                                        argDecl     += ", ";
                                        args        += ", ";
                                        argTypes    += ", ";
                                        tupleValues += ", ";
                                        }
                                    argDecl     += $"{type} {name}";
                                    args        += name;
                                    argTypes    += type;
                                    tupleValues += $"args[{Loop.count}]";
                                    }
                                break;
                            }

                        customMethods += customMethod
                                            .replace("%name%"   , methodName)
                                            .replace("%retType%", retType)
                                            .replace("%argDecl%", argDecl)
                                            .replace("%args%"   , args)
                                            ;

                        customInvocations += customInvocation
                                            .replace("%name%"        , methodName)
                                            .replace("%argTypes%"    , argTypes)
                                            .replace("%arg%"         , args)
                                            .replace("%tupleValues%" , tupleValues)
                                            ;
                        }
                    }
                }

            customInstantiations += customInstantiation
                                    .replace("%appName%"          , appName)
                                    .replace("%propertyName%"     , propertyName)
                                    .replace("%propertyType%"     , propertyType)
                                    .replace("%propertyTypeName%" , propertyTypeName)
                                    .replace("%propertyStoreType%", propertyStoreType)
                                    ;

            customDeclarations += customDeclaration
                                    .replace("%propertyType%"     , propertyType)
                                    .replace("%propertyTypeName%" , propertyTypeName)
                                    .replace("%propertyStoreType%", propertyStoreType)
                                    .replace("%propertyBaseType%" , propertyBaseType)
                                    .replace("%CustomMethods%"    , customMethods)
                                    .replace("%CustomInvocations%", customInvocations)
                                    ;
            }

        String moduleSource = $./templates/imdb/_module.txt
                                .replace("%appName%"             , appName)
                                .replace("%appSchema%"           , appSchema)
                                .replace("%PropertyInfos%"       , propertyInfos)
                                .replace("%PropertyGetters%"     , propertyGetters)
                                .replace("%CustomInstantiations%", customInstantiations)
                                .replace("%CustomDeclarations%"  , customDeclarations)
                                ;

        moduleFile.create();
        writeUtf(moduleFile, moduleSource);
        }

    /**
     * Collect all DB properties.
     */
    Tuple<PropertyTemplate, DBCategory>[] collectDBProps(ClassTemplate appSchemaTemplate)
        {
        Tuple<PropertyTemplate, DBCategory>[] properties = new Array();

        NextProperty:
        for (PropertyTemplate prop : appSchemaTemplate.properties)
            {
            TypeTemplate typeTemplate = prop.type;

            if (Composition classTemplate := typeTemplate.fromClass(),
                            classTemplate.is(ClassTemplate))
                {
                for ((DBCategory category, TypeTemplate dbType) : DB_TEMPLATES)
                    {
                    if (typeTemplate.isA(dbType))
                        {
                        properties += Tuple:(prop, category);
                        continue NextProperty;
                        }
                    }
                }
            throw new UnsupportedOperation($"Unsupported property type: {prop.name} {prop.type}");
            }

        // TODO recurse to super template
        return properties;
        }

    /**
     * The code below should be replaced with
     *      file.contents = contents.utfBytes();
     */
    void writeUtf(File file, String contents)
        {
        import ecstasy.io.ByteArrayOutputStream as Stream;
        import ecstasy.io.UTF8Writer;
        import ecstasy.io.Writer;

        Stream out    = new Stream(contents.size);
        Writer writer = new UTF8Writer(out);
        writer.addAll(contents);

        file.contents = out.bytes.freeze(True);
        }

    /**
     * Obtain a display name for the specified type for the specified application.
     */
    String displayName(TypeTemplate type, String appName)
        {
        assert Composition composition := type.fromClass();
        return displayName(composition, appName);
        }

    /**
     * Obtain a display name for the specified composition for the specified application.
     */
    String displayName(Composition composition, String appName)
        {
        if (composition.is(ClassTemplate))
            {
            return composition.implicitName ?: (appName + "_." + composition.displayName);
            }
        TODO AnnotatingComposition
        }

    @Override
    function oodb.Connection(DBUser)
            ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        CatalogMetadata meta = dbContainer.innerTypeSystem.primaryModule.as(CatalogMetadata);
        return meta.ensureConnectionFactory();
        }


    // ----- constants -----------------------------------------------------------------------------

    static TypeTemplate DBObject_TEMPLATE = DBObject.baseTemplate.type;

    static Map<DBCategory, TypeTemplate> DB_TEMPLATES = Map:
            [
            DBMap       = oodb.DBMap     .baseTemplate.type,
            DBList      = oodb.DBList    .baseTemplate.type,
            DBQueue     = oodb.DBQueue   .baseTemplate.type,
            DBLog       = oodb.DBLog     .baseTemplate.type,
            DBCounter   = oodb.DBCounter .baseTemplate.type,
            DBValue     = oodb.DBValue   .baseTemplate.type,
            DBFunction  = oodb.DBFunction.baseTemplate.type,
            DBSchema    = oodb.DBSchema  .baseTemplate.type
            ];
    }