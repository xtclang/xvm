import ecstasy.io.FileInputStream;
import ecstasy.io.FileOutputStream;

import wellknown.DescriptorProto;
import wellknown.FileDescriptorProto;

import wellknown.compiler.CodeGeneratorRequest;
import wellknown.compiler.CodeGeneratorResponse;
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

        ProtoCodeGen codeGen     = new ProtoCodeGen();
        SourceFile[] sourceFiles = new Array();

        for (String fileToGenerate : request.fileToGenerate) {
            assert FileDescriptorProto descriptor := fileMap.get(fileToGenerate);

            Map<String, String> sourceMap = codeGen.generate(descriptor);
            for (Map.Entry<String, String> entry : sourceMap.entries) {
                String key = entry.key;
                String value = entry.value;
                SourceFile sourceFile = new SourceFile(name=entry.key, content=entry.value);
                sourceFiles.add(sourceFile);
            }
        }
        return new  CodeGeneratorResponse(file=sourceFiles);

    }
}

