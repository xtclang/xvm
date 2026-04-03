/**
 * Describes an RPC method within a protobuf service definition.
 *
 * @param name             the method name
 * @param inputType        the fully-qualified request message type name
 * @param outputType       the fully-qualified response message type name
 * @param clientStreaming  True if the client sends a stream of messages
 * @param serverStreaming  True if the server sends a stream of messages
 * @param options          any options declared on this method
 */
const MethodDescriptor(String  name,
                       String  inputType,
                       String  outputType,
                       Boolean clientStreaming  = False,
                       Boolean serverStreaming  = False,
                       Map<String, String> options = Map:[]) {

    @Override
    String toString() = $"rpc {name}({inputType}) returns ({outputType})";
}
