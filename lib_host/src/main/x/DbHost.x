import ecstasy.io.Log;

import ecstasy.mgmt.Container;
import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.ClassTemplate.Composition;
import ecstasy.reflect.TypeTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.PropertyTemplate;

import oodb.DBObject;
import oodb.DBObject.DBCategory;

/**
 * An abstract host for a db module.
 */
@Abstract
class DbHost
    {
    /**
     * Generate all the necessary classes to use a DB modules.
     *
     * @param repository    the repository to load necessary modules from
     * @param dbModuleName  the name of the hosted DB module
     * @param buildDir      the directory to place all generated artifacts to
     * @param errors        the error log
     *
     * @return True iff the module template was successfully created
     * @return the generated module (optional)
     */
    conditional ModuleTemplate
        generateDBModule(ModuleRepository repository, String dbModuleName, Directory buildDir, Log errors);

    /**
     * Check an existence of the DB (e.g. on disk); create or recover if necessary.
     *
     * @return a connection factory
     */
    function oodb.Connection(oodb.DBUser)
        ensureDatabase(Map<String, String>? configOverrides = Null);

    /**
     * The Container that hosts the DB module.
     */
    @Unassigned
    Container dbContainer;


    // ----- common helper methods -----------------------------------------------------------------

    /**
     * Collect all DB properties.
     */
    conditional Tuple<PropertyTemplate, DBCategory>[]
            collectDBProps(ClassTemplate appSchemaTemplate, Log errors)
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
            errors.add($"Unsupported property type: {prop.name} {prop.type}");
            return False;
            }

        // TODO recurse to super template
        return True, properties;
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

    /**
     * Compile the specified source file.
     */
    Boolean compileModule(File sourceFile, Directory buildDir, Log errors)
        {
        @Inject ecstasy.lang.src.Compiler compiler;

        compiler.setResultLocation(buildDir);

        (Boolean success, String error) = compiler.compile([sourceFile]);

        if (!success)
            {
            errors.add(error);
            }
        return success;
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