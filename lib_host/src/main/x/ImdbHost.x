import ecstasy.io.Log;

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
    conditional ModuleTemplate generateDBModule(
            ModuleRepository repository, String dbModuleName, Directory buildDir, Log errors)
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
            errors.add($"Schema is not found in {dbModuleName} module");
            return False;
            }

        File sourceFile = moduleDir.fileFor("module.x");

        if (createModule(sourceFile, appName, dbModule, appSchemaTemplate, errors) &&
            compileModule(sourceFile, buildDir, errors))
            {
            return True, repository.getModule(dbModuleName + "_imdb");
            }
        return False;
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
                            return True, classTemplate;
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
    Boolean createModule(File sourceFile, String appName,
                         ModuleTemplate moduleTemplate, ClassTemplate appSchemaTemplate, Log errors)
        {
        String appSchema = appSchemaTemplate.name;

        Tuple<PropertyTemplate, DBCategory>[] dbProps;
        if (dbProps := collectDBProps(appSchemaTemplate, errors)) {}
        else
            {
            return False;
            }

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

        sourceFile.create();
        writeUtf(sourceFile, moduleSource);
        return True;
        }

    @Override
    function oodb.Connection(DBUser)
            ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        CatalogMetadata meta = dbContainer.innerTypeSystem.primaryModule.as(CatalogMetadata);
        return meta.ensureConnectionFactory();
        }
    }