package org.xvm.tool;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.xvm.util.ConstOrdinalList;

/**
 * Using the raw information from {@code ./resources/unicode/*.zip}, build the Unicode data tables
 * used by the Char class.
 */
public class BuildUnicodeTables {
    public static final boolean TEST = false;

    private static final String UCD_ALL_FLAT_ZIP = "ucd.all.flat.zip";
    private static final String UCD_ALL_FLAT_XML = "ucd.all.flat.xml";
    private static final File OUTPUT_DIR = new File("./build/resources/unicode/");
    private static final int BUF_SIZE = 256;
    private static final long MB = 1024L * 1024L;
    private static final long GB = MB * 1024L;

    private final String[] asArgs;

    /**
     * @param asArgs the Launcher's command-line arguments
     */
    public BuildUnicodeTables(final String[] asArgs) {
        this.asArgs = asArgs;
    }

    /**
     * Entry point from the OS.
     *
     * @param asArgs command line arguments
     */
    public static void main(final String[] asArgs) throws IOException, JAXBException {
        new BuildUnicodeTables(asArgs).run();
    }

    /**
     * Execute the Launcher tool.
     */
    public void run() throws IOException, JAXBException {
        out("Locating Unicode raw data ...");
        final List<CharData> listRaw = loadData();

        int nHigh = -1;
        for (final CharData cd : listRaw) {
            final int n = cd.lastIndex();
            if (n > nHigh) {
                nHigh = n;
            }
        }
        final int cAll = nHigh + 1;

        out("Processing Unicode codepoints 0.." + nHigh);

        // various data collections
        final int[] cats = new int[cAll];
        Arrays.fill(cats, new CharData().cat());
        // String[] labels = new String[cAll];
        final int[] decs = new int[cAll];
        //CHECKSTYLE:OFF
        Arrays.fill(decs, 10); // 10 is illegal; use as "null"
        //CHECKSTYLE:ON
        final String[] nums = new String[cAll];
        final int[] cccs = new int[cAll];
        Arrays.fill(cccs, 255); // 255 is illegal; use as "null"
        final int[] lowers = new int[cAll];
        final int[] uppers = new int[cAll];
        final int[] titles = new int[cAll];
        final String[] blocks = new String[cAll];

        for (final CharData cd : listRaw) {
            for (int codepoint = cd.firstIndex(), iLast = cd.lastIndex(); codepoint <= iLast; ++codepoint) {
                cats[codepoint] = cd.cat();
                // labels[codepoint] = cd.label();
                decs[codepoint] = cd.dec();
                nums[codepoint] = cd.num();
                cccs[codepoint] = cd.combo();
                lowers[codepoint] = cd.lower();
                uppers[codepoint] = cd.upper();
                titles[codepoint] = cd.title();
                blocks[codepoint] = cd.block();
            }
        }

        writeResult("Cats", cats);
        // writeResult("Labels", labels);
        writeResult("Decs", decs);
        writeResult("Nums", nums);
        writeResult("CCCs", cccs);
        writeResult("Lowers", lowers);
        writeResult("Uppers", uppers);
        writeResult("Titles", titles);
        writeResult("Blocks", blocks);
    }

    public List<CharData> loadData() throws IOException, JAXBException {
        final String sXML;
        if (TEST) {
            sXML = loadDataTest();
        } else {
            final var zip = getZipFile();
            final ZipEntry entryXML = zip.getEntry(UCD_ALL_FLAT_XML);
            final long lRawLen = entryXML.getSize();
            assert 2 * GB > lRawLen;

            final int cbRaw = (int)lRawLen;
            final byte[] abRaw = new byte[cbRaw];
            try (var in = zip.getInputStream(entryXML)) {
                final int cbActual = in.readNBytes(abRaw, 0, cbRaw);
                assert cbActual == cbRaw;
                sXML = new String(abRaw);
            }
        }

        final JAXBContext jaxbContext = JAXBContext.newInstance(UCDData.class);
        final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        final UCDData data = (UCDData)jaxbUnmarshaller.unmarshal(new StringReader(sXML));
        return data.repertoire;
    }

    private static String loadDataTest() throws IOException {
        final String sXML;
        final ClassLoader loader = requireNonNullElseGet(BuildUnicodeTables.class.getClassLoader(), ClassLoader::getSystemClassLoader);
        final String sFile = requireNonNull(loader.getResource("test.xml")).getFile();
        final File file = new File(sFile);
        assert file.exists();
        assert file.isFile();
        assert file.canRead();

        final long lRawLen = file.length();
        assert 2 * GB > lRawLen;

        final int cbRaw = (int)lRawLen;
        final byte[] abRaw = new byte[cbRaw];
        try (var in = new FileInputStream(file)) {
            final int cbActual = in.readNBytes(abRaw, 0, cbRaw);
            assert cbActual == cbRaw;
            sXML = new String(abRaw);
        }
        return sXML;
    }

    private File resolveArgumentAsFile() {
        if (0 < asArgs.length) {
            final File ucdZip = new File(asArgs[0]);
            out(getClass().getSimpleName() + " UCD zip file: " + ucdZip.getAbsolutePath());
            return ucdZip;
        }
        return null;
    }

    private File resolveArgumentAsDestinationDir() {
        if (1 < asArgs.length) {
            final File destDir = new File(asArgs[1]);
            out(getClass().getSimpleName() + " destination directory: " + destDir.getAbsolutePath());
            return destDir;
        }
        return OUTPUT_DIR;
    }

    private ZipFile getZipFile() throws IOException {
        final var file = requireNonNullElseGet(resolveArgumentAsFile(), () -> new File(UCD_ALL_FLAT_ZIP));
        if (!(file.exists() && file.isFile() && file.canRead())) {
            final ClassLoader loader = requireNonNullElseGet(BuildUnicodeTables.class.getClassLoader(), ClassLoader::getSystemClassLoader);
            final var resource = loader.getResource(UCD_ALL_FLAT_ZIP);
            if (null == resource) {
                throw new IOException("Cannot find resources for unicode file: " + UCD_ALL_FLAT_ZIP);
            }
            return new ZipFile(resource.getFile());
        }
        out("Reverting to zip file: " + file.getAbsolutePath());
        return new ZipFile(file);
    }

    void writeResult(final String name, final String[] array) throws IOException {
        // collect and sort the values
        final var map = new TreeMap<String, Integer>();
        final int c = array.length;
        for (final var s : array) {
            if (null != s) {
                assert !s.isEmpty();
                map.compute(s, (k, v) -> (null == v ? 0 : v) + 1);
            }
        }

        final var sb = new StringBuilder();
        sb.append(name)
            .append(": [index] \"str\" (freq) \n--------------------");
        int index = 0;
        for (final var entry : map.entrySet()) {
            sb.append("\n[")
                .append(index)
                .append("] \"")
                .append(entry.getKey())
                .append("\" (")
                .append(entry.getValue()).append("x)");
            entry.setValue(index++);
        }

        final int indexNull = index;
        sb.append("\n\ndefault=").append(indexNull);

        writeDetails(name, sb.toString());

        // assign indexes to each
        final int[] an = new int[c];
        for (int i = 0; i < c; ++i) {
            final String s = array[i];
            an[i] = null == s ? indexNull : map.get(s);
        }

        writeResult(name, an);
    }

    void writeResult(final String name, final int[] array) throws IOException {
        //        if (name.equals("Cats"))
        //            {
        //            out("cats:");
        //            for (int i = 0; i < 128; ++i)
        //                {
        //                out("[" + i + "]=" + array[i]);
        //                }
        //            }

        writeResult(name, ConstOrdinalList.compress(array, BUF_SIZE));

    }

    private File resolveOutput(final String name, final String extension) throws IOException {
        final var filename = "Char" + name + '.' + extension;
        final var dir = resolveArgumentAsDestinationDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not access or create dir: '" + dir.getAbsolutePath() + '\'');
        }
        assert dir.canWrite();
        return new File(dir, filename);
    }

    void writeResult(final String name, final byte[] data) throws IOException {
        try (var out = new FileOutputStream(resolveOutput(name, "dat"))) {
            out.write(data);
        }
    }

    void writeDetails(final String name, final String details) throws IOException {
        try (var out = new FileWriter(resolveOutput(name, "txt"))) {
            out.write(details);
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Print a blank line to the terminal.
     */
    public static void out() {
        out("");
    }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void out(final Object o) {
        System.out.println(o);
    }

    /**
     * Print a blank line to the terminal.
     */
    @SuppressWarnings("unused")
    public static void err() {
        err("");
    }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void err(final Object o) {
        System.err.println(o);
    }

    // ----- inner classes -------------------------------------------------------------------------

    @XmlRootElement(name = "ucd")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UCDData {
        @XmlElement
        public String description;

        //              @XmlElement(name="group"       ), // note: none present in Unicode 13 data
        @XmlElements({ @XmlElement(name = "char"), @XmlElement(name = "noncharacter"), @XmlElement(name = "surrogate"), @XmlElement(name = "reserved") })
        @XmlElementWrapper
        public List<CharData> repertoire = new ArrayList<>();

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append("UCD description=")
                .append(description)
                .append(", repertoire=\n");

            int c = 0;
            for (final var item : repertoire) {
                if (BUF_SIZE < c) {
                    sb.append(",\n...");
                    break;
                } else if (0 < c++) {
                    sb.append(",\n");
                }

                sb.append(item);
            }
            return sb.toString();
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CharData {
        @XmlAttribute(name = "cp")
        String codepoint;

        @XmlAttribute(name = "first-cp")
        String codepointStart;

        @XmlAttribute(name = "last-cp")
        String codepointEnd;

        @XmlAttribute(name = "na")
        String name;

        @XmlAttribute(name = "gc")
        String gc;

        @XmlAttribute(name = "nt")
        String nt;

        @XmlAttribute(name = "nv")
        String nv;

        @XmlAttribute(name = "ccc")
        String ccc;

        @XmlAttribute(name = "slc")
        String slc;

        @XmlAttribute(name = "suc")
        String suc;

        @XmlAttribute(name = "stc")
        String stc;

        @XmlAttribute(name = "blk")
        String blk;

        @SuppressWarnings("checkstyle:MagicNumber")
        int cat() {
            if (null == gc) {
                return 29;
            }

            return switch (gc) {
            case "Lu" -> 0;
            case "Ll" -> 1;
            case "Lt" -> 2;
            case "Lm" -> 3;
            case "Lo" -> 4;
            case "Mn" -> 5;
            case "Mc" -> 6;
            case "Me" -> 7;
            case "Nd" -> 8;
            case "Nl" -> 9;
            case "No" -> 10;
            case "Pc" -> 11;
            case "Pd" -> 12;
            case "Ps" -> 13;
            case "Pe" -> 14;
            case "Pi" -> 15;
            case "Pf" -> 16;
            case "Po" -> 17;
            case "Sm" -> 18;
            case "Sc" -> 19;
            case "Sk" -> 20;
            case "So" -> 21;
            case "Zs" -> 22;
            case "Zl" -> 23;
            case "Zp" -> 24;
            case "Cc" -> 25;
            case "Cf" -> 26;
            case "Cs" -> 27;
            case "Co" -> 28;
            default -> 29;
            };
        }

        int firstIndex() {
            return null == codepoint || codepoint.isEmpty() ? Integer.parseInt(codepointStart, 16) : Integer.parseInt(codepoint, 16);
        }

        int lastIndex() {
            return null == codepoint || codepoint.isEmpty() ? Integer.parseInt(codepointEnd, 16) : Integer.parseInt(codepoint, 16);
        }

        int dec() {
            if ("De".equals(nt)) {
                assert null != nv;
                assert !nv.isEmpty();
                assert !"NaN".equals(nv);
                return Integer.parseInt(nv);
            }

            return 10; // illegal value
        }

        String num() {
            return null == nt || nt.isEmpty() || "None".equals(nt) || null == nv || nv.isEmpty() || "NaN".equals(nv) ? null : nv;
        }

        int combo() {
            return null == ccc || ccc.isEmpty() ? 255 : Integer.parseInt(ccc);
        }

        int lower() {
            return null == slc || slc.isEmpty() || "#".equals(slc) ? 0 : Integer.parseInt(slc, 16);
        }

        int upper() {
            return null == suc || suc.isEmpty() || "#".equals(suc) ? 0 : Integer.parseInt(suc, 16);
        }

        int title() {
            return null == stc || stc.isEmpty() || "#".equals(stc) ? 0 : Integer.parseInt(stc, 16);
        }

        String block() {
            return null == blk || blk.isEmpty() ? null : blk;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName().toLowerCase()
                + " codepoint="
                + (null == codepoint || codepoint.isEmpty() ? codepointStart + ".." + codepointEnd : codepoint)
                + (null != name && !name.isEmpty()
                ? ", name=\"" + name + "\"" : "") + ", gen-cat=" + gc + (null != blk && !blk.isEmpty()
                ? ", block=\"" + blk + "\"" : "") + (null != nt && !nt.isEmpty() && !"None".equals(nt)
                ? ", num-type=\"" + nt + "\"" : "") + (null != nv && !nv.isEmpty() && !"NaN".equals(nv)
                ? ", num-val=\"" + nv + "\"" : "") + (null == suc || suc.isEmpty() || "#".equals(suc)
                ? "" : ", suc=" + suc) + (null == slc || slc.isEmpty() || "#".equals(slc) ? "" : ", slc=" + slc)
                + (null == stc || stc.isEmpty() || "#".equals(stc) ? "" : ", stc=" + stc);
            //   + (bidiClass != null && bidiClass.length() > 0 ? ", bidiClass=\"" + bidiClass + "\"" : "")
            //   + (bidiMirrored != null && bidiMirrored.equals("Y") ? ", bidiMirrored=\"" + bidiMirrored + "\"" : "")
            //   + (bidiMirrorImage != null && bidiMirrorImage.length() > 0 ? ", bidiMirrorImage=\"" + bidiMirrorImage + "\"" : "")
            //   + (bidiControl != null && bidiControl.equals("Y") ? ", bidiControl=\"" + bidiControl + "\"" : "")
            //   + (bidiPairedBracketType != null && bidiPairedBracketType.length() > 0 ? ", bidiPairedBracketType=\"" + bidiPairedBracketType + "\"" : "")
            //   + (bidiPairedBracket != null && bidiPairedBracket.length() > 0 ? ", bidiPairedBracket=\"" + bidiPairedBracket + "\"" : "")
        }
    }
}
