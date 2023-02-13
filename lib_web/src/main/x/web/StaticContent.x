import ecstasy.fs.FileNode;

import responses.SimpleResponse;


/**
 * A mixin that represents a static content.
 *
 * The content can be a single file or a directory of files.
 */
mixin StaticContent(String path, FileNode fileNode, MediaType? mediaType=Null,
                    String defaultPage="index.html")
        extends WebService(path)
    {
    assert()
        {
        assert:arg fileNode.exists && fileNode.readable;
        }

    @Get("{path}")
    conditional ResponseOut getResource(String path)
        {
        ResponseOut createResponse(File file)
            {
            if (MediaType mediaType := webApp.registry_.findMediaType(file.name))
                {
                return new SimpleResponse(OK, mediaType, file.contents).makeImmutable();
                }
            throw new RequestAborted(NoContent, $"Unknown media type for {file.name}");
            }

        if (path == "" || path == "/")
            {
            if (File file := fileNode.is(File))
                {
                return True, createResponse(file);
                }
            path = defaultPage;
            }

        FileNode dir = fileNode;
        if (dir.is(Directory))
            {
            try
                {
                if (File file := dir.findFile(path))
                    {
                    return True, createResponse(file);
                    }
                }
            catch (IllegalArgument e)
                {
                // this must be a problem with the path; simply fall through
                }
            }
        return False;
        }
    }