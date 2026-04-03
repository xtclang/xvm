/**
 * Describes a protobuf service definition.
 *
 * @param name     the service name
 * @param methods  the RPC methods in declaration order
 * @param options  any options declared on this service
 */
const ServiceDescriptor(String             name,
                        MethodDescriptor[] methods,
                        Map<String, String> options = Map:[]) {

    /**
     * Look up an RPC method by name.
     *
     * @param methodName  the method name
     *
     * @return True and the matching MethodDescriptor if found
     */
    conditional MethodDescriptor methodByName(String methodName) {
        for (MethodDescriptor method : methods) {
            if (method.name == methodName) {
                return True, method;
            }
        }
        return False;
    }

    @Override
    String toString() = $"service {name}";
}
