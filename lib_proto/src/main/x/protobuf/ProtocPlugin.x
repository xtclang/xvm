import ecstasy.io.FileInputStream;
import ecstasy.io.FileOutputStream;

import wellknown.DescriptorProto;
import wellknown.Edition;
import wellknown.FileDescriptorProto;

import wellknown.compiler.CodeGeneratorRequest;
import wellknown.compiler.CodeGeneratorResponse;
import wellknown.compiler.CodeGeneratorResponse.Feature;
import wellknown.compiler.CodeGeneratorResponse.File as SourceFile;

/**
 * This class is the entry point for the protoc plugin.
 */
class ProtocPlugin {
    /**
     * Generates the code for the given request file and writes the result to the response file.
     *
     * The request file must exist and must contain a serialized protobuf stream representing a
     * CodeGeneratorRequest. The response file will contain a serialized protobuf stream
     * representing  a CodeGeneratorResponse.
     *
     * @param requestFile   the request file
     * @param responseFile  the response file
     */
    void generate(File requestFile, File responseFile) {
        assert requestFile.exists;

        CodeGeneratorRequest request = new CodeGeneratorRequest();
        request.mergeFrom(new FileInputStream(requestFile));

        CodeGeneratorResponse response = generate(request);
        if (responseFile.exists) {
            responseFile.delete();
        }
        responseFile.ensure();
        response.writeTo(new FileOutputStream(responseFile));
    }

    /**
     * Generates the code for the given request file and writes the result to the response file.
     *
     * @param request  the `CodeGeneratorRequest` to generate code for
     *
     * @return  the `CodeGeneratorResponse` containing the generated code
     */
    CodeGeneratorResponse generate(CodeGeneratorRequest request) {
        Map<String, FileDescriptorProto> fileMap = new HashMap();
        for (FileDescriptorProto fd : request.protoFile) {
            fileMap.put(fd.name, fd);
        }

        Map<String, String[]> options     = parseOptions(request.parameter);
        ProtoCodeGen          codeGen     = new ProtoCodeGen(options);
        SourceFile[]          sourceFiles = new Array();

        for (String fileToGenerate : request.fileToGenerate) {
            assert FileDescriptorProto descriptor := fileMap.get(fileToGenerate);

            Map<String, String> sourceMap = codeGen.generate(descriptor);
            for (Map.Entry<String, String> entry : sourceMap.entries) {
                SourceFile sourceFile = new SourceFile(name=entry.key, content=entry.value);
                sourceFiles.add(sourceFile);
            }
        }

        UInt64 features = Feature.FeatureProto3Optional.protoValue.toUInt64()
                        | Feature.FeatureSupportsEditions.protoValue.toUInt64();

        return new CodeGeneratorResponse(
            supportedFeatures = features,
            minimumEdition    = Edition.Edition2023.protoValue.toInt32(),
            maximumEdition    = Edition.Edition2024.protoValue.toInt32(),
            file              = sourceFiles
        );
    }

    /**
     * Parses a comma-delimited parameter string of key=value pairs into a Map.
     *
     * The same key may appear multiple times, so each key maps to an array of values.
     * If the parameter string is empty, an empty map is returned.
     *
     * @param parameter  the parameter string to parse
     *
     * @return a Map of option keys to their values
     */
    private Map<String, String[]> parseOptions(String parameter) {
        Map<String, String[]> options = new HashMap();
        if (parameter.size == 0) {
            return options;
        }

        for (String part : parameter.split(',')) {
            if (Int eq := part.indexOf('=')) {
                String key   = part[0 ..< eq];
                String value = part[eq + 1 ..< part.size];
                if (String[] values := options.get(key)) {
                    values.add(value);
                } else {
                    options.put(key, new Array<String>().add(value));
                }
            }
        }
        return options;
    }
}

