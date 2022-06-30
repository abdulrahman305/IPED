package iped.parsers.external;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.ExternalParserConfigGenerator;
import iped.parsers.util.RepoToolDownloader;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class ExternalParserTest implements ExternalParsersConfigReaderMetKeys {
    private static String tmpPath = "/src/test/tmp/";
    private static String userDir = System.getProperty("user.dir");
    private static String absoluteTmpPath = userDir + tmpPath;
    private static String XMLFilePath = absoluteTmpPath + "/ExternalParsers.xml";
    private static String osName = System.getProperty("os.name").toLowerCase();
    private static File XMLFile;
    private static List<ExternalParser> parsers;
    private static ExternalParser superfetchParser, prefetchParser, recyclebinParser, recyclebinInfo2Parser, EvtxLogParser;

    private static ExternalParserConfigGenerator createExternalParserConfig(String name, String toolPath, String checkCommand,
        String command, String mimeType, int firstLinesToIgnore, String charset) throws TikaException, TransformerException {

        ExternalParserConfigGenerator parserConfigGenerator = new ExternalParserConfigGenerator();
        parserConfigGenerator.setParserName(name);
        parserConfigGenerator.setWinToolPath(toolPath);
        parserConfigGenerator.setCheckCommand(checkCommand);
        parserConfigGenerator.setErrorCodes(1);
        parserConfigGenerator.setCommand(command);
        HashSet<MediaType> mimeTypes = new HashSet<>();
        mimeTypes.add(MediaType.application(mimeType));
        parserConfigGenerator.addMimeTypes(mimeTypes);
        parserConfigGenerator.setFirstLinesToIgnore(firstLinesToIgnore);
        parserConfigGenerator.setOutputCharset(charset);

        return parserConfigGenerator;
    }

    @BeforeClass
    public static void setUp() throws IOException, TikaException, TransformerException, ParserConfigurationException, SAXException {
        XMLFile = new File(XMLFilePath);
        new File(absoluteTmpPath).mkdir();

        // download all external parsers tools
        if (osName.startsWith("windows")) {
            String repoPath = "libyal/libagdb/20181111.1/libagdb-20181111.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
            repoPath = "libyal/sccainfo/20170205.1/sccainfo-20170205.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
            repoPath = "abelcheung/rifiuti2/0.6.1/rifiuti2-0.6.1.zip";  // recyclebin and recyclebinINFO2 parsers
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
            repoPath = "libyal/evtxexport/20170122.1/evtxexport-20170122.1.zip";
            RepoToolDownloader.unzipFromUrl(repoPath, absoluteTmpPath);
        }

        // add SuperFetch parser configuration to xml file
        ExternalParserConfigGenerator superfetchConfigGenerator = createExternalParserConfig("SuperFetchParser", tmpPath + "libagdb/", 
            "agdbinfo -V", "agdbinfo ${INPUT}", "x-superfetch", 0, "ISO-8859-1");
        superfetchConfigGenerator.writeDocumentToFile(XMLFile);

        // append Prefetch parser configuration to the same file
        ExternalParserConfigGenerator prefetchConfigGenerator = createExternalParserConfig("PrefetchParser", tmpPath + "sccainfo/",
            "sccainfo -V", "sccainfo ${INPUT}", "x-prefetch", 0, "ISO-8859-1");
        prefetchConfigGenerator.writeDocumentToFile(XMLFile);

        // append RecycleBin parser
        ExternalParserConfigGenerator recyclebinConfigGenerator = createExternalParserConfig("RecycleBinParser", tmpPath + "rifiuti/",
            "rifiuti-vista -v", "rifiuti-vista -8 ${INPUT}", "x-recyclebin", 3, "UTF-8");
        recyclebinConfigGenerator.writeDocumentToFile(XMLFile);

        // append RecycleBinInfo2 parser
        ExternalParserConfigGenerator recyclebinInfo2ConfigGenerator = createExternalParserConfig("RecycleInfo2Parser", tmpPath + "rifiuti/",
            "rifiuti -v", "rifiuti -8 ${INPUT}", "x-info2", 3, "UTF-8");
        recyclebinInfo2ConfigGenerator.writeDocumentToFile(XMLFile);

        // append EvtxLogParser parser
        ExternalParserConfigGenerator evtxLogConfigGenerator = createExternalParserConfig("EvtxLogParser", tmpPath + "evtxexport/",
            "evtxexport -V", "evtxexport ${INPUT}", "x-elf-file", 0, "ISO-8859-1");
        evtxLogConfigGenerator.writeDocumentToFile(XMLFile);

        parsers = ExternalParsersConfigReader.read(new FileInputStream(XMLFile));
        superfetchParser = parsers.size() > 0 ? parsers.get(0) : null;
        prefetchParser = parsers.size() > 1 ? parsers.get(1) : null;
        recyclebinParser = parsers.size() > 2 ? parsers.get(2) : null;
        recyclebinInfo2Parser = parsers.size() > 3 ? parsers.get(3) : null;
        EvtxLogParser = parsers.size() > 4 ? parsers.get(4) : null;
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (osName.startsWith("windows")) {
            FileUtils.deleteDirectory(new File(absoluteTmpPath));
        }
        XMLFile.delete();
    }
    
    @Test
    public void testSuperFetch() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_superfetchAgGlFgAppHistory.db";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {
            assumeNotNull(superfetchParser);
            superfetchParser.parse(stream, handler, metadata, context);
            String mts = metadata.toString();
            String hts = handler.toString();

            assertTrue(mts.contains(fileName));
            assertTrue(hts.contains("\\DEVICE\\HARDDISKVOLUME2"));
            assertTrue(hts.contains("Mar 25, 2015 11:08:36"));
            assertTrue(hts.contains("0xca0c7a4"));
            assertTrue(hts.contains("3573"));
            assertTrue(hts.contains("EXCEL.EXE"));
        }
    }

    @Test
    public void testPreFetch() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler(1 << 20);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_prefetch.pf";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {
            assumeNotNull(prefetchParser);
            prefetchParser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("TEAMS.EXE"));
            assertTrue(hts.contains("30"));
            assertTrue(hts.contains("0x92d8a64c"));
            assertTrue(hts.contains("Jun 08, 2022 12:09:38.412736500 UTC"));
            assertTrue(hts.toLowerCase().contains("users\\felipe costa\\appdata\\local\\microsoft\\teams\\current\\teams.exe"));
        }
    }

    @Test
    public void testRecycleBin() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_recyclebin.png";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {
            assumeNotNull(recyclebinParser);
            recyclebinParser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("2022-06-29 12:56:50"));
            assertTrue(hts.contains("473831"));
            assertTrue(hts.contains("C:\\Users\\Felipe Costa\\OneDrive\\Área de Trabalho\\test_lenaPng.png"));
        }
    }

    @Test
    public void testRecycleBinInfo2() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_recyclebinINFO2";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {
            assumeNotNull(recyclebinInfo2Parser);
            recyclebinInfo2Parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();
            System.out.println(hts);

            assertTrue(hts.contains("2008-10-28 15:53:42"));
            assertTrue(hts.contains("No"));
            assertTrue(hts.contains("4096"));
            assertTrue(hts.contains("C:\\Documents and Settings\\All Users\\Desktop\\有道桌面词典.lnk"));
            assertTrue(hts.contains("2008-11-19 05:07:35"));
            assertTrue(hts.contains("Yes"));
            assertTrue(hts.contains("2727936"));
            assertTrue(hts.contains("C:\\Documents and Settings\\Administrator\\Desktop\\GetDataBackforFAT-v3.63_PConline"));
        }
    }


    @Test
    public void testEvtxLogParser() throws IOException, TikaException, SAXException, TransformerException {

        ContentHandler handler = new BodyContentHandler(1 << 25);
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String fileName = "test_evtxLog.evtx";
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try (InputStream stream = this.getClass().getResourceAsStream("/test-files/" + fileName)) {
            assumeNotNull(EvtxLogParser);
            EvtxLogParser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Jun 29, 2022 15:11:27.519191500 UTC"));
            assertTrue(hts.contains("Microsoft-Windows-Servicing"));
            assertTrue(hts.contains("Microsoft-Hyper-V-ClientEdition"));
            assertTrue(hts.contains("0x0"));
            assertTrue(hts.contains("DISM Package Manager Provider"));
            assertTrue(hts.contains("Jun 02, 2022"));
            assertTrue(hts.contains("Microsoft-Windows-Subsystem-Linux"));
            assertTrue(hts.contains("Microsoft-Windows-Lxss-Optional"));
            assertTrue(hts.contains("0d (13)"));
        }
    }

}
