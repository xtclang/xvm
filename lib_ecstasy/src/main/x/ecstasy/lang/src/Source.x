import io.ByteArrayInputStream;
import io.CharArrayReader;
import io.IllegalUTF;


/**
 * Represents a unit of source code.
 */
const Source {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `Source` instance from a `String` value.
     *
     * @param contents  the source code or other textual contents
     * @param root      the directory that acts as the "root" of the source and resource hierarchy,
     *                  used to obtain file and directory literals
     * @param path      the path corresponding to this source code (which may be a directory), used
     *                  to evaluate file and directory literals
     */
    construct(String contents, Directory? root = Null, Path? path=Null) {
        this.contents = contents;
        this.root     = root;
        this.path     = path;
    }

    /**
     * Construct a `Source` instance from a `File` value.
     *
     * @param file  the file containing the source code or other contents
     * @param root  the directory that acts as the "root" of the source and resource hierarchy, used
     *              to obtain file and directory literals
     */
    construct(File file, Directory? root = Null) {
        this.file = file;
        construct Source(loadText(file), root, file.path);
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The textual contents of the `Source` (typically, source code).
     */
    String contents;

    /**
     * The `File` that the source is read from, or `Null` if the source is provided as a `String`
     * instead.
     */
    File? file;

    /**
     * The `Directory` that acts as the "root" of the source and resource hierarchy, used to obtain
     * file and directory literals.
     */
    Directory? root;

    /**
     * The `Path` of this `Source`, which is either the path of the file that the source was read
     * from, or a `Path` corresponding to the `String` that was used to create this object. This
     * information is used when resolving file and directory literals.
     */
    Path? path;


    // ----- API -----------------------------------------------------------------------------------

    /**
     * @return a `Reader` for the [contents] of the `Source`
     */
    Reader createReader() {
        return new CharArrayReader(contents);
    }

    /**
     * Resolve a path relative to this `Source`, or absolute based on the provided [root] Directory.
     *
     * @return True iff a root directory was provided and the path can be resolved to an existing
     *         file or directory
     * @return (conditional) the `File` or `Directory` that the path resolved to
     */
    conditional File | Directory resolvePath(Path path) {
        // TODO
        return False;
    }

    /**
     * Load a file (as text) referenced from inside this `Source`.
     *
     * @param path  a path relative to this `Source`
     *
     * @return True iff a root directory was provided and the path can be resolved to a file and
     *         read
     * @return (conditional) the `Source` for the specified file
     */
    conditional Source includeString(Path path) {
        // TODO
        return False;
    }

    /**
     * Load a file (as binary) referenced from inside this `Source`.
     *
     * @param path  a path relative to this `Source`
     *
     * @return True iff a root directory was provided and the path can be resolved to a file and
     *         read
     * @return (conditional) the binary contents of the specified file
     */
    conditional Byte[] includeBinary(Path path) {
        // TODO
        return False;
    }

    /**
     * Load the entire contents of the specified file as a String. This implementation supports the
     * following formats: ASCII, UTF-8, UTF-16 (both LE and BE), and UTF-32 (both LE and BE).
     *
     * @param file  the file to load from
     *
     * @return the contents of the file as a String
     *
     * @throws IOException  for any I/O error
     * @throws IllegalUTF   if there is a flaw in the UTF encoding or in the resulting codepoint
     */
    static String loadText(File file) {
        // REVIEW this code does not belong here, and it should be streaming (instead of "contents")

        enum Encoding(Int charLen=1) {Ascii, Utf8, Utf16LE(2), Utf16BE(2), Utf32LE(4), Utf32BE(4)}

        Byte[]   bytes    = file.contents;
        Int      bytesLen = bytes.size;
        Encoding encoding = Ascii;
        Int      headLen  = 0;
        switch (bytesLen) {
        default:
        case 4:
            if (bytes.startsWith([0x00, 0x00, 0xFE, 0xFF])) {
                encoding = Utf32BE;
                headLen  = 4;
                break;
            }

            if (bytes.startsWith([0xFF, 0xFE, 0x00, 0x00])) {
                encoding = Utf32LE;
                headLen  = 4;
                break;
            }

            continue;

        case 3:
            if (bytes.startsWith([0xEF, 0xBB, 0xBF])) {
                encoding = Utf8;
                headLen  = 3;
                break;
            }

            continue;

        case 2:
            if (bytes.startsWith([0xFE, 0xFF])) {
                encoding = Utf16BE;
                headLen  = 2;
                break;
            }

            if (bytes.startsWith([0xFF, 0xFE])) {
                encoding = Utf16LE;
                headLen  = 2;
                break;
            }

            if (bytes[0] == 0x00 && bytes[1] != 0x00) {
                // this is just a guess, but since 0x00 is illegal in ASCII or UTF-8, it's a
                // good guess
                encoding = Utf16BE;
                break;
            }

            if (bytes[0] != 0x00 && bytes[1] == 0x00) {
                // this is just a guess, but since 0x00 is illegal in ASCII or UTF-8, it's a
                // good guess
                encoding = Utf16LE;
                break;
            }

            continue;

        case 1:
        case 0:
            break;
        }

        if (encoding.charLen > 1 && bytesLen % encoding.charLen != 0) {
            // it's a problem if the file size is not divisible by the fixed-length character size
            if (bytesLen % encoding.charLen == 1
                    && (bytes[bytesLen-1] == '\z'.toByte() || bytes[bytesLen-1] == '\0'.toByte())) {
                // the last byte is an "end of file" indicator; this is not correct, but it is
                // ignorable
                --bytesLen;
            } else {
                throw new IllegalUTF($"invalid binary file length {bytesLen} for format {encoding}");
            }
        }

        decode: switch (encoding) {
        case Ascii:
            // quick scan to verify that the contents are ASCII
            Array<Char> chars = new Char[bytesLen];
            loop: for (Byte byte : bytes) {
                if (byte > 0x7F) {
                    // use Utf8 encoding
                    encoding = Utf8;
                    continue decode;
                }

                chars[loop.count] = byte.toChar();
            }
            return new String(chars.freeze(True));

        case Utf8:
            StringBuffer buf = new StringBuffer(bytesLen);
            InputStream  in  = new ByteArrayInputStream(bytes);
            while (!in.eof) {
                buf.add(in.readUTF8Char());
            }
            return buf.toString();

        case Utf16LE:
            Int          charsLen = bytesLen/2;
            StringBuffer buf      = new StringBuffer(charsLen);
            InputStream  in       = new ByteArrayInputStream(bytes);
            while (!in.eof) {
                buf.add(in.readUTF16LEChar());
            }
            return buf.toString();

        case Utf16BE:
            Int          charsLen = bytesLen/2;
            StringBuffer buf      = new StringBuffer(charsLen);
            InputStream  in       = new ByteArrayInputStream(bytes);
            while (!in.eof) {
                buf.add(in.readUTF16BEChar());
            }
            return buf.toString();

        case Utf32LE:
            Int          charsLen = bytesLen/4;
            StringBuffer buf      = new StringBuffer(charsLen);
            InputStream  in       = new ByteArrayInputStream(bytes);
            while (!in.eof) {
                buf.add(in.readUTF32LEChar());
            }
            return buf.toString();

        case Utf32BE:
            Int          charsLen = bytesLen/4;
            StringBuffer buf      = new StringBuffer(charsLen);
            InputStream  in       = new ByteArrayInputStream(bytes);
            while (!in.eof) {
                buf.add(in.readUTF32BEChar());
            }
            return buf.toString();
        }
    }
}