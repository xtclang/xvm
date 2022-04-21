import ecstasy.fs.FileNode;

/**
 * A mixin that represents a static content.
 *
 * The content can be a single file or a directory of files.
 */
mixin StaticContent(FileNode fileNode, MediaType mediaType, String path = "/")
        extends WebService(path)
    {
    assert()
        {
        assert:arg fileNode.exists && fileNode.readable;
        }

    @Get
    conditional HttpResponse get()
        {
        FileNode f = fileNode;
        if (f.is(File))
            {
            HttpResponse response = new HttpResponse();
            response.body = f.contents;
            response.headers.setContentType(mediaType);
            response.makeImmutable();
            return True, response;
            }
        return getResource("index.html");
        }

    @Get("/{name: .+}")
    conditional HttpResponse getResource(@PathParam String name)
        {
        FileNode dir = fileNode;
        if (dir.is(Directory))
            {
            if (File file := dir.findFile(name))
                {
                HttpResponse response = new HttpResponse();
                response.body = file.contents;
                response.headers.setContentType(mediaType);
                response.makeImmutable();
                return True, response;
                }
            }
        return False;
        }
    }