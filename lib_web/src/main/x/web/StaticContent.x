import ecstasy.fs.FileNode;

import responses.SimpleResponse;

/**
 * An annotation that represents a static content.
 *
 * The content can be a single file or a directory of files.
 */
annotation StaticContent(String path, FileNode fileNode, MediaType? mediaType = Null,
                         String defaultPage = "index.html")
        extends WebService(path)
        incorporates Mixin(fileNode, mediaType, defaultPage) {

    /**
     * A mixin that represents a static content.
     *
     * Unlike the [StaticContent] annotation, which can only be used with constant arguments, this
     * mixin allows construction with arguments (other than the "path") that can be computed at
     * runtime and/or supports overriding the [getResource] method. The example below shows both:
     *
     *     @WebService("/data")
     *     service ExtraFiles
     *             incorporates StaticContent.Mixin {
     *         construct(Directory data){
     *             construct StaticContent.Mixin(data);
     *         }
     *
     *         @Override
     *         conditional ResponseOut getResource(String path) {
     *             ... // custom code
     *         }
     *      }
     */
    static mixin Mixin(FileNode fileNode, MediaType? mediaType = Null,
                       String defaultPage = "index.html")
            into WebService {

        construct(String path, FileNode fileNode, MediaType? mediaType = Null,
                       String defaultPage = "index.html") {

            construct WebService(path);
            this.fileNode    = fileNode;
            this.mediaType   = mediaType;
            this.defaultPage = defaultPage;
        }

        assert() {
            assert:arg fileNode.exists && fileNode.readable;
        }

        @Get("{/path}")
        conditional ResponseOut getResource(String path) {
            ResponseOut createResponse(File file) {
                MediaType? mediaType = this.mediaType;
                if (mediaType == Null) {
                    mediaType := webApp.registry_.findMediaType(file.name);
                }
                return mediaType == Null
                    ? new SimpleResponse(UnsupportedMediaType, $"Unknown media type for {file.name}")
                    : new SimpleResponse(OK, mediaType, file.contents);
            }

            if (path == "") {
                return True, createResponse(fileNode.is(File)?);
                path = defaultPage;
            }

            FileNode dir = fileNode;
            if (dir.is(Directory)) {
                try {
                    if (File file := dir.findFile(path)) {
                        return True, createResponse(file);
                    }
                } catch (IllegalArgument e) {
                    // this must be a problem with the path; simply fall through
                }
            }
            return False;
        }
    }
}