/**
 * Describes a complete `.proto` file.
 *
 * This is the root of the descriptor tree produced by parsing a `.proto` file. It contains all
 * top-level message, enum, and service definitions along with file-level metadata such as the
 * syntax version, package name, and imports.
 */
const FileDescriptor {

    construct(String               name,
              String               syntax             = "proto3",
              String               packageName        = "",
              String[]             dependencies        = [],
              String[]             publicDependencies  = [],
              MessageDescriptor[]  messages            = [],
              EnumDescriptor[]     enums               = [],
              ServiceDescriptor[]  services            = [],
              Map<String, String>  options             = Map:[]) {
        this.name               = name;
        this.syntax             = syntax;
        this.packageName        = packageName;
        this.dependencies       = dependencies;
        this.publicDependencies = publicDependencies;
        this.messages           = messages;
        this.enums              = enums;
        this.services           = services;
        this.options            = options;
    }

    /**
     * The file name (e.g. `"my_service.proto"`).
     */
    String name;

    /**
     * The syntax version: `"proto2"` or `"proto3"`.
     */
    String syntax;

    /**
     * The package name, or empty if none.
     */
    String packageName;

    /**
     * The import paths.
     */
    String[] dependencies;

    /**
     * The `import public` paths.
     */
    String[] publicDependencies;

    /**
     * Top-level message definitions.
     */
    MessageDescriptor[] messages;

    /**
     * Top-level enum definitions.
     */
    EnumDescriptor[] enums;

    /**
     * Service definitions.
     */
    ServiceDescriptor[] services;

    /**
     * File-level options.
     */
    Map<String, String> options;

    /**
     * @return True if this file uses proto3 syntax
     */
    Boolean isProto3.get() = syntax == "proto3";

    /**
     * Look up a top-level message by name.
     *
     * @param messageName  the message type name
     *
     * @return True and the matching MessageDescriptor if found
     */
    conditional MessageDescriptor messageByName(String messageName) {
        for (MessageDescriptor message : messages) {
            if (message.name == messageName) {
                return True, message;
            }
        }
        return False;
    }

    /**
     * Look up a top-level enum by name.
     *
     * @param enumName  the enum type name
     *
     * @return True and the matching EnumDescriptor if found
     */
    conditional EnumDescriptor enumByName(String enumName) {
        for (EnumDescriptor e : enums) {
            if (e.name == enumName) {
                return True, e;
            }
        }
        return False;
    }

    /**
     * Look up a service by name.
     *
     * @param serviceName  the service name
     *
     * @return True and the matching ServiceDescriptor if found
     */
    conditional ServiceDescriptor serviceByName(String serviceName) {
        for (ServiceDescriptor svc : services) {
            if (svc.name == serviceName) {
                return True, svc;
            }
        }
        return False;
    }

    @Override
    String toString() = $"file {name}";
}
