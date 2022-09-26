import ecstasy.fs.FileNode;

import responses.SimpleResponse;


/**
 * A mixin that represents a static content.
 *
 * The content can be a single file or a directory of files.
 */
mixin StaticContent(FileNode fileNode, MediaType mediaType, String path = "/",
                    String defaultPage="index.html")
        extends WebService(path)
    {
    assert()
        {
        assert:arg fileNode.exists && fileNode.readable;
        }

    @Get
    conditional Response get()
        {
        if (File file := fileNode.is(File))
            {
            return True, new SimpleResponse(OK, mediaType, file.contents).makeImmutable();
            }
        return getResource(defaultPage);
        }

    @Get("/{path}")
    conditional Response getResource(String path)
        {
        FileNode dir = fileNode;
        if (dir.is(Directory))
            {
            if (File file := dir.findFile(path))
                {
                return True, new SimpleResponse(OK, mediaType, file.contents).makeImmutable();
                }
            }
        return False;
        }
    }