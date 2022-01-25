package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
public class BuildUnicodeTables
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg  command line arguments
     */
    public static void main(String[] asArg)
        {
        BuildUnicodeTables tool = new BuildUnicodeTables(asArg);
        tool.run();
        }

    /**
     * @param asArgs  the Launcher's command-line arguments
     */
    public BuildUnicodeTables(String[] asArgs)
        {
        m_asArgs = asArgs;
        }

    /**
     * Execute the Launcher tool.
     */
    public void run()
        {
        out("Locating Unicode raw data ...");
        List<CharData> listRaw = loadData();

        int nHigh = -1;
        for (CharData cd : listRaw)
            {
            int n = cd.lastIndex();
            if (n > nHigh)
                {
                nHigh = n;
                }
            }
        int cAll = nHigh + 1;

        out("Processing Unicode codepoints 0.." + nHigh);

        // various data collections
        int   [] cats   = new int   [cAll]; Arrays.fill(cats, new CharData().cat());
     // String[] labels = new String[cAll];
        int   [] decs   = new int   [cAll]; Arrays.fill(decs, 10); // 10 is illegal; use as "null"
        String[] nums   = new String[cAll];
        int   [] cccs   = new int   [cAll]; Arrays.fill(cccs, 255); // 255 is illegal; use as "null"
        int   [] lowers = new int   [cAll];
        int   [] uppers = new int   [cAll];
        int   [] titles = new int   [cAll];
        String[] blocks = new String[cAll];

        for (CharData cd : listRaw)
            {
            for (int codepoint = cd.firstIndex(), iLast = cd.lastIndex(); codepoint <= iLast; ++codepoint)
                {
                cats  [codepoint] = cd.cat();
             // labels[codepoint] = cd.label();
                decs  [codepoint] = cd.dec();
                nums  [codepoint] = cd.num();
                cccs  [codepoint] = cd.combo();
                lowers[codepoint] = cd.lower();
                uppers[codepoint] = cd.upper();
                titles[codepoint] = cd.title();
                blocks[codepoint] = cd.block();

                }
            }

        writeResult("Cats"  , cats);
     // writeResult("Labels", labels);
        writeResult("Decs"  , decs);
        writeResult("Nums"  , nums);
        writeResult("CCCs"  , cccs);
        writeResult("Lowers", lowers);
        writeResult("Uppers", uppers);
        writeResult("Titles", titles);
        writeResult("Blocks", blocks);
        }

    public List<CharData> loadData()
        {
        try
            {
            String sXML;
            if (TEST)
                {
                ClassLoader loader = BuildUnicodeTables.class.getClassLoader();
                if (loader == null)
                    {
                    loader = ClassLoader.getSystemClassLoader();
                    }
                String sFile = loader.getResource("test.xml").getFile();
                File   file  = new File(sFile);
                assert file.exists();
                assert file.isFile();
                assert file.canRead();

                long     lRawLen  = file.length();
                assert lRawLen < 2 * 1000 * 1000 * 1000;

                int         cbRaw    = (int) lRawLen;
                byte[]      abRaw    = new byte[cbRaw];
                InputStream in       = new FileInputStream(file);
                int         cbActual = in.readNBytes(abRaw, 0, cbRaw);
                assert cbActual == cbRaw;
                sXML = new String(abRaw, 0);
                }
            else
                {
                String sZip = "ucd.all.flat.zip";
                File file = new File(sZip);
                if (!(file.exists() && file.isFile() && file.canRead()))
                    {
                    ClassLoader loader = BuildUnicodeTables.class.getClassLoader();
                    if (loader == null)
                        {
                        loader = ClassLoader.getSystemClassLoader();
                        }
                    sZip = loader.getResource(sZip).getFile();
                    }

                ZipFile  zip      = new ZipFile(sZip);
                ZipEntry entryXML = zip.getEntry("ucd.all.flat.xml");
                long     lRawLen  = entryXML.getSize();
                assert lRawLen < 2 * 1000 * 1000 * 1000;

                int         cbRaw    = (int) lRawLen;
                byte[]      abRaw    = new byte[cbRaw];
                InputStream in       = zip.getInputStream(entryXML);
                int         cbActual = in.readNBytes(abRaw, 0, cbRaw);
                assert cbActual == cbRaw;
                sXML = new String(abRaw, 0);
                }

            JAXBContext jaxbContext = JAXBContext.newInstance(UCDData.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            UCDData data = (UCDData) jaxbUnmarshaller.unmarshal(new StringReader(sXML));
            return data.repertoire;
            }
        catch (IOException | JAXBException e)
            {
            throw new RuntimeException(e);
            }
        }

    void writeResult(String name, String[] array)
        {
        // collect and sort the values
        TreeMap<String, Integer> map = new TreeMap<>();
        int c = array.length;
        for (int i = 0; i < c; ++i)
            {
            String s = array[i];
            if (s != null)
                {
                assert !s.equals("");
                map.compute(s, (k, v) -> (v==null?0:v) + 1);
                }
            }

        StringBuilder sb = new StringBuilder();
        sb.append(name)
          .append(": [index] \"str\" (freq) \n--------------------");
        int index = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet())
            {
            sb.append("\n[")
              .append(index)
              .append("] \"")
              .append(entry.getKey())
              .append("\" (")
              .append(entry.getValue())
              .append("x)");

            entry.setValue(index++);
            }

        int indexNull = index;
        sb.append("\n\ndefault=")
          .append(indexNull);

        writeDetails(name, sb.toString());

        // assign indexes to each
        int[] an = new int[c];
        for (int i = 0; i < c; ++i)
            {
            String s = array[i];
            an[i] = s == null ? indexNull : map.get(s);
            }

        writeResult(name, an);
        }

    void writeResult(String name, int[] array)
        {
//        if (name.equals("Cats"))
//            {
//            out("cats:");
//            for (int i = 0; i < 128; ++i)
//                {
//                out("[" + i + "]=" + array[i]);
//                }
//            }

        writeResult(name, ConstOrdinalList.compress(array, 256));
        }

    void writeResult(String name, byte[] data)
        {
        try
            {
            String filename = "Char" + name + ".dat";
            File   dir      = new File("./build/resources/");
            File   file     = dir.exists() && dir.isDirectory() && dir.canWrite()
                    ? new File(dir, filename)
                    : new File(filename);
            FileOutputStream out = new FileOutputStream(file);
            out.write(data);
            out.close();
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        }

    void writeDetails(String name, String details)
        {
        try
            {
            String filename = "Char" + name + ".txt";
            File   dir      = new File("./build/resources/");
            File   file     = dir.exists() && dir.isDirectory() && dir.canWrite()
                ? new File(dir, filename)
                : new File(filename);
            FileWriter writer = new FileWriter(file);
            writer.write(details);
            writer.close();
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Print a blank line to the terminal.
     */
    public static void out()
        {
        out("");
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void out(Object o)
        {
        System.out.println(o);
        }

    /**
     * Print a blank line to the terminal.
     */
    public static void err()
        {
        err("");
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void err(Object o)
        {
        System.err.println(o);
        }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status
     */
    protected void abort(boolean fError)
        {
        System.exit(fError ? -1 : 0);
        }


    // ----- inner classes -------------------------------------------------------------------------

    @XmlRootElement(name = "ucd")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UCDData
        {
        @XmlElement
        public String description;

        @XmlElements({
                @XmlElement(name="char"        ),
                @XmlElement(name="noncharacter"),
                @XmlElement(name="surrogate"   ),
//              @XmlElement(name="group"       ), // note: none present in Unicode 13 data
                @XmlElement(name="reserved"    )
        })
        @XmlElementWrapper
        public List<CharData> repertoire = new ArrayList<>();

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append("UCD description=")
              .append(description)
              .append(", repertoire=\n");

            int c = 0;
            for (CharData item : repertoire)
                {
                if (c > 200)
                    {
                    sb.append(",\n...");
                    break;
                    }
                else if (c++ > 0)
                    {
                    sb.append(",\n");
                    }

                sb.append(item);
                }
            return sb.toString();
            }
        }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CharData
        {
        int firstIndex()
            {
            return codepoint == null || codepoint.length() == 0
                    ? Integer.parseInt(codepointStart, 16)
                    : Integer.parseInt(codepoint, 16);
            }

        int lastIndex()
            {
            return codepoint == null || codepoint.length() == 0
                    ? Integer.parseInt(codepointEnd, 16)
                    : Integer.parseInt(codepoint, 16);
            }

        @XmlAttribute(name = "cp")
        String codepoint;

        @XmlAttribute(name = "first-cp")
        String codepointStart;

        @XmlAttribute(name = "last-cp")
        String codepointEnd;

// note: names in the XML file don't work the way they do in the Unicode .txt data file format
//        String label()
//            {
//            return name != null && name.length() >= 2 && name.charAt(0) == '<'
//                                                      && name.charAt(name.length()-1) == '>'
//                    ? name.substring(1, name.length()-1)
//                    : null;
//            }

        @XmlAttribute(name = "na")
        String name;

        int cat()
            {
            if (gc == null)
                {
                return 29;
                }

            switch (gc)
                {
                case "Lu":  return 0;
                case "Ll":  return 1;
                case "Lt":  return 2;
                case "Lm":  return 3;
                case "Lo":  return 4;
                case "Mn":  return 5;
                case "Mc":  return 6;
                case "Me":  return 7;
                case "Nd":  return 8;
                case "Nl":  return 9;
                case "No":  return 10;
                case "Pc":  return 11;
                case "Pd":  return 12;
                case "Ps":  return 13;
                case "Pe":  return 14;
                case "Pi":  return 15;
                case "Pf":  return 16;
                case "Po":  return 17;
                case "Sm":  return 18;
                case "Sc":  return 19;
                case "Sk":  return 20;
                case "So":  return 21;
                case "Zs":  return 22;
                case "Zl":  return 23;
                case "Zp":  return 24;
                case "Cc":  return 25;
                case "Cf":  return 26;
                case "Cs":  return 27;
                case "Co":  return 28;

                case "Cn":
                case "":
                default:    return 29;
                }
            }

        @XmlAttribute(name = "gc")
        String gc;

        int dec()
            {
            if (nt != null && nt.equals("De"))
                {
                assert nv != null;
                assert nv.length() > 0;
                assert !nv.equals("NaN");
                return Integer.parseInt(nv);
                }

            return 10; // illegal value
            }

        String num()
            {
            return nt == null || nt.length() == 0 || nt.equals("None") ||
                   nv == null || nv.length() == 0 || nv.equals("NaN")
                    ? null
                    : nv;
            }

        @XmlAttribute(name = "nt")
        String nt;

        @XmlAttribute(name = "nv")
        String nv;

        int combo()
            {
            return ccc == null || ccc.length() == 0
                    ? 255
                    : Integer.parseInt(ccc);
            }

        @XmlAttribute(name = "ccc")
        String ccc;

        int lower()
            {
            return slc == null || slc.length() == 0 || slc.equals("#")
                    ? 0
                    : Integer.parseInt(slc, 16);
            }

        @XmlAttribute(name = "slc")
        String slc;

        int upper()
            {
            return suc == null || suc.length() == 0 || suc.equals("#")
                    ? 0
                    : Integer.parseInt(suc, 16);
            }

        @XmlAttribute(name = "suc")
        String suc;

        int title()
            {
            return stc == null || stc.length() == 0 || stc.equals("#")
                    ? 0
                    : Integer.parseInt(stc, 16);
            }

        @XmlAttribute(name = "stc")
        String stc;

        String block()
            {
            return blk == null || blk.length() == 0
                    ? null
                    : blk;
            }

        @XmlAttribute(name = "blk")
        String blk;

//        @XmlAttribute(name = "bc")
//        String bidiClass;
//
//        @XmlAttribute(name = "Bidi_M")
//        String bidiMirrored;
//
//        @XmlAttribute(name = "bmg")
//        String bidiMirrorImage;
//
//        @XmlAttribute(name = "Bidi_C")
//        String bidiControl;
//
//        @XmlAttribute(name = "bpt")
//        String bidiPairedBracketType;
//
//        @XmlAttribute(name = "bpb")
//        String bidiPairedBracket;

        @Override
        public String toString()
            {
            return getClass().getSimpleName().toLowerCase()
                    + " codepoint=" + (codepoint == null || codepoint.length() == 0
                            ? codepointStart + ".." + codepointEnd
                            : codepoint)
                    + (name != null && name.length() > 0 ? ", name=\"" + name + "\"" : "")
                    + ", gen-cat=" + gc
                    + (blk != null && blk.length() > 0 ? ", block=\"" + blk + "\"" : "")
                    + (nt != null && nt.length() > 0 && !nt.equals("None") ? ", num-type=\"" + nt + "\"" : "")
                    + (
                    nv != null && nv.length() > 0 && !nv.equals("NaN") ? ", num-val=\"" + nv + "\"" : "")
                    + (suc == null || suc.length() == 0
                            || suc.equals("#") ? "" : ", suc=" + suc)
                    + (slc == null || slc.length() == 0
                            || slc.equals("#") ? "" : ", slc=" + slc)
                    + (stc == null || stc.length() == 0
                            || stc.equals("#") ? "" : ", stc=" + stc)
//                    + (bidiClass != null && bidiClass.length() > 0 ? ", bidiClass=\"" + bidiClass + "\"" : "")
//                    + (bidiMirrored != null && bidiMirrored.equals("Y") ? ", bidiMirrored=\"" + bidiMirrored + "\"" : "")
//                    + (bidiMirrorImage != null && bidiMirrorImage.length() > 0 ? ", bidiMirrorImage=\"" + bidiMirrorImage + "\"" : "")
//                    + (bidiControl != null && bidiControl.equals("Y") ? ", bidiControl=\"" + bidiControl + "\"" : "")
//                    + (bidiPairedBracketType != null && bidiPairedBracketType.length() > 0 ? ", bidiPairedBracketType=\"" + bidiPairedBracketType + "\"" : "")
//                    + (bidiPairedBracket != null && bidiPairedBracket.length() > 0 ? ", bidiPairedBracket=\"" + bidiPairedBracket + "\"" : "")
                    ;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final boolean TEST = false;

    /**
     * The command-line arguments.
     */
    private String[] m_asArgs;
    }
