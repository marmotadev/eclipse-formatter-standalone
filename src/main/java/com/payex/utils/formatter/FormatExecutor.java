package com.payex.utils.formatter;

/*
 * Copyright 2010. All work is copyrighted to their respective author(s),
 * unless otherwise stated.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResourceCollection;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.xml.sax.SAXException;

import com.relativitas.maven.plugins.formatter.ConfigReadException;
import com.relativitas.maven.plugins.formatter.ConfigReader;

/**
 * A Maven plugin mojo to format Java source code using the Eclipse code
 * formatter.
 * 
 * Mojo parameters allow customizing formatting by specifying the config XML
 * file, line endings, compiler version, and source code locations. Reformatting
 * source files is avoided using an md5 hash of the content, comparing to the
 * original hash to the hash after formatting and a cached hash.
 * 
 * @goal format
 * @phase process-sources
 * 
 * @author jecki
 * @author Matt Blanchette
 */
public class FormatExecutor {
    private static final Logger            log                       = Logger.getLogger(FormatExecutor.class.getName());
    private static final String            CACHE_PROPERTIES_FILENAME = "maven-java-formatter-cache.properties";
    private static final String[]          DEFAULT_INCLUDES          = new String[] { "**/*.java" };

    static final String                    LINE_ENDING_AUTO          = "AUTO";
    static final String                    LINE_ENDING_KEEP          = "KEEP";
    static final String                    LINE_ENDING_LF            = "LF";
    static final String                    LINE_ENDING_CRLF          = "CRLF";
    static final String                    LINE_ENDING_CR            = "CR";

    static final String                    LINE_ENDING_LF_CHAR       = "\n";
    static final String                    LINE_ENDING_CRLF_CHARS    = "\r\n";
    static final String                    LINE_ENDING_CR_CHAR       = "\r";

    /**
     * ResourceManager for retrieving the configFile resource.
     * 
     * @component
     * @required
     * @readonly
     */
    // private ResourceManager resourceManager;

    /**
     * Project's source directory as specified in the POM.
     * 
     * @parameter expression="${project.build.sourceDirectory}"
     * @readonly
     * @required
     */
    private File                           sourceDirectory;

    /**
     * Project's test source directory as specified in the POM.
     * 
     * @parameter expression="${project.build.testSourceDirectory}"
     * @readonly
     * @required
     */
    private File                           testSourceDirectory;

    /**
     * Project's target directory as specified in the POM.
     * 
     * @parameter expression="${project.build.directory}"
     * @readonly
     * @required
     */
    private File                           targetDirectory;

    /**
     * Project's base directory.
     * 
     * @parameter expression="${basedir}"
     * @readonly
     * @required
     */
    private File                           basedir;

    /**
     * Location of the Java source files to format. Defaults to source main and
     * test directories if not set. Deprecated in version 0.3. Reintroduced in
     * 0.4.
     * 
     * @parameter
     * @since 0.4
     */
    private File[]                         directories;

    /**
     * List of fileset patterns for Java source locations to include in
     * formatting. Patterns are relative to the project source and test source
     * directories. When not specified, the default include is <code>**&#47;*.java</code>
     * 
     * @parameter
     * @since 0.3
     */
    private String[]                       includes;

    /**
     * List of fileset patterns for Java source locations to exclude from
     * formatting. Patterns are relative to the project source and test source
     * directories. When not specified, there is no default exclude.
     * 
     * @parameter
     * @since 0.3
     */
    private String[]                       excludes;

    /**
     * Java compiler source version.
     * 
     * @parameter default-value="1.5" expression="${maven.compiler.source}"
     */
    private String                         compilerSource            = "1.7";

    /**
     * Java compiler compliance version.
     * 
     * @parameter default-value="1.5" expression="${maven.compiler.source}"
     */
    private String                         compilerCompliance        = "1.7";

    /**
     * Java compiler target version.
     * 
     * @parameter default-value="1.5" expression="${maven.compiler.target}"
     */
    private String                         compilerTargetPlatform    = "1.7";

    /**
     * The file encoding used to read and write source files. When not specified
     * and sourceEncoding also not set, default is platform file encoding.
     * 
     * @parameter default-value="${project.build.sourceEncoding}"
     * @since 0.3
     */
    private String                         encoding                  = "utf-8";

    /**
     * Sets the line-ending of files after formatting. Valid values are:
     * <ul>
     * <li><b>"AUTO"</b> - Use line endings of current system</li>
     * <li><b>"KEEP"</b> - Preserve line endings of files, default to AUTO if ambiguous</li>
     * <li><b>"LF"</b> - Use Unix and Mac style line endings</li>
     * <li><b>"CRLF"</b> - Use DOS and Windows style line endings</li>
     * <li><b>"CR"</b> - Use early Mac style line endings</li>
     * </ul>
     * 
     * @parameter default-value="AUTO"
     * @since 0.2.0
     */
    private String                         lineEnding                = "AUTO";

    /**
     * File or classpath location of an Eclipse code formatter configuration xml
     * file to use in formatting.
     * 
     * @parameter
     */
    private String                         configFile                = "formatter.xml";

    /**
     * Sets whether compilerSource, compilerCompliance, and
     * compilerTargetPlatform values are used instead of those defined in the
     * configFile.
     * 
     * @parameter default-value="false"
     * @since 0.2.0
     */
    private Boolean                        overrideConfigCompilerVersion;

    private CodeFormatter                  formatter;

    private PlexusIoFileResourceCollection collection;

    public FormatExecutor() {
        basedir = new File(FormatExecutor.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        createCodeFormatter();
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    public void execute() throws RuntimeException {
        long startClock = System.currentTimeMillis();

        if (StringUtils.isEmpty(encoding)) {
            encoding = ReaderFactory.FILE_ENCODING;
            getLog().warn("File encoding has not been set, using platform encoding (" + encoding
                          + ") to format source files, i.e. build is platform dependent!");
        } else {
            try {
                "Test Encoding".getBytes(encoding);
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Encoding '" + encoding + "' is not supported");
            }
            getLog().info("Using '" + encoding + "' encoding to format source files.");
        }

        if (!LINE_ENDING_AUTO.equals(lineEnding) && !LINE_ENDING_KEEP.equals(lineEnding)
            && !LINE_ENDING_LF.equals(lineEnding) && !LINE_ENDING_CRLF.equals(lineEnding)
            && !LINE_ENDING_CR.equals(lineEnding)) {
            throw new RuntimeException("Unknown value for lineEnding parameter");
        }

        createResourceCollection();

        List files = new ArrayList();
        try {
            if (directories != null) {
                for (File directory : directories) {
                    if (directory.exists() && directory.isDirectory()) {
                        collection.setBaseDir(directory);
                        addCollectionFiles(files);
                    }
                }
            } else { // Using defaults of source main and test dirs
                if (sourceDirectory != null && sourceDirectory.exists() && sourceDirectory.isDirectory()) {
                    collection.setBaseDir(sourceDirectory);
                    addCollectionFiles(files);
                }
                if (testSourceDirectory != null && testSourceDirectory.exists() && testSourceDirectory.isDirectory()) {
                    collection.setBaseDir(testSourceDirectory);
                    addCollectionFiles(files);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to find files using includes/excludes", e);
        }

        int numberOfFiles = files.size();
        // Log log = getLog();
        log.info("Number of files to be formatted: " + numberOfFiles);

        if (numberOfFiles > 0) {
            createCodeFormatter();
            ResultCollector rc = new ResultCollector();
            Properties hashCache = readFileHashCacheFile();

            String basedirPath = getBasedirPath();
            for (int i = 0, n = files.size(); i < n; i++) {
                File file = (File) files.get(i);
                formatFile(file, rc, hashCache, basedirPath);
            }

            storeFileHashCache(hashCache);

            long endClock = System.currentTimeMillis();

            log.info("Successfully formatted: " + rc.successCount + " file(s)");
            log.info("Fail to format        : " + rc.failCount + " file(s)");
            log.info("Skipped               : " + rc.skippedCount + " file(s)");
            log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
        }
    }

    private Logger getLog() {
        return log;
    }

    /**
     * Create a {@link PlexusIoFileResourceCollection} instance to be used by
     * this mojo. This collection uses the includes and excludes to find the
     * source files.
     */
    void createResourceCollection() {
        collection = new PlexusIoFileResourceCollection();
        if (includes != null && includes.length > 0) {
            collection.setIncludes(includes);
        } else {
            collection.setIncludes(DEFAULT_INCLUDES);
        }
        collection.setExcludes(excludes);
        collection.setIncludingEmptyDirectories(false);

        IncludeExcludeFileSelector fileSelector = new IncludeExcludeFileSelector();
        fileSelector.setIncludes(DEFAULT_INCLUDES);
        collection.setFileSelectors(new FileSelector[] { fileSelector });
    }

    /**
     * Add source files from the {@link PlexusIoFileResourceCollection} to the
     * files list.
     * 
     * @param files
     * @throws IOException
     */
    void addCollectionFiles(List files) throws IOException {
        Iterator resources = collection.getResources();
        while (resources.hasNext()) {
            PlexusIoFileResource resource = (PlexusIoFileResource) resources.next();
            files.add(resource.getFile());
        }
    }

    private String getBasedirPath() {
        try {
            return basedir.getCanonicalPath();
        }
        catch (Exception e) {
            return "";
        }
    }

    private void storeFileHashCache(Properties props) {
        File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile));
            props.store(out, null);
        }
        catch (FileNotFoundException e) {
            getLog().warn("Cannot store file hash cache properties file", e);
        }
        catch (IOException e) {
            getLog().warn("Cannot store file hash cache properties file", e);
        }
    }

    private Properties readFileHashCacheFile() {
        Properties props = new Properties();
        // Log log = getLog();
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        } else if (!targetDirectory.isDirectory()) {
            log.warn("Something strange here as the " + "supposedly target directory is not a directory.");
            return props;
        }

        File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
        if (!cacheFile.exists()) {
            return props;
        }

        try {
            props.load(new BufferedInputStream(new FileInputStream(cacheFile)));
        }
        catch (FileNotFoundException e) {
            log.warn("Cannot load file hash cache properties file", e);
        }
        catch (IOException e) {
            log.warn("Cannot load file hash cache properties file", e);
        }
        return props;
    }

    /**
     * @param file
     * @param rc
     * @param hashCache
     * @param basedirPath
     */
    private void formatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath) {
        try {
            doFormatFile(file, rc, hashCache, basedirPath);
        }
        catch (IOException e) {
            rc.failCount++;
            getLog().warn(e);
        }
        catch (MalformedTreeException e) {
            rc.failCount++;
            getLog().warn(e);
        }
        catch (BadLocationException e) {
            rc.failCount++;
            getLog().warn(e);
        }
    }

    /**
     * Format individual file.
     * 
     * @param file
     * @param rc
     * @param hashCache
     * @param basedirPath
     * @throws IOException
     * @throws BadLocationException
     */
    private void
            doFormatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath) throws IOException,
                                                                                                 BadLocationException {
        log.debug("Processing file: " + file);

        String code = new String(Files.readAllBytes(file.toPath()));// readFileAsString(file);

        String lineSeparator = getLineEnding(code);

        TextEdit te = formatter.format(CodeFormatter.K_COMPILATION_UNIT + CodeFormatter.F_INCLUDE_COMMENTS,
                                       code,
                                       0,
                                       code.length(),
                                       0,
                                       lineSeparator);
        if (te == null) {
            rc.skippedCount++;
            log.debug("Code cannot be formatted. Possible cause " + "is unmatched source/target/compliance version.");
            return;
        }

        IDocument doc = new Document(code);
        te.apply(doc);
        String formattedCode = doc.get();

        writeStringToFile(formattedCode, file);
        rc.successCount++;
    }

    /**
     * @param str
     * @return
     * @throws UnsupportedEncodingException
     */
    private String md5hash(String str) throws UnsupportedEncodingException {
        return DigestUtils.md5Hex(str.getBytes(encoding));
    }

    /**
     * Read the given file and return the content as a string.
     * 
     * @param file
     * @return
     * @throws java.io.IOException
     */
    private String readFileAsString(File file) throws java.io.IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(ReaderFactory.newReader(file, encoding));
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        }
        finally {
            IOUtil.close(reader);
        }
        return fileData.toString();
    }

    /**
     * Write the given string to a file.
     * 
     * @param str
     * @param file
     * @throws IOException
     */
    private void writeStringToFile(String str, File file) throws IOException {
        if (!file.exists() && file.isDirectory()) {
            return;
        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(WriterFactory.newWriter(file, encoding));
            bw.write(str);
        }
        finally {
            IOUtil.close(bw);
        }
    }

    /**
     * Create a {@link CodeFormatter} instance to be used by this mojo.
     * 
     * @throws RuntimeException
     */
    private void createCodeFormatter() {
        Map options = getFormattingOptions();
        formatter = ToolFactory.createCodeFormatter(options);
    }

    /**
     * Return the options to be passed when creating {@link CodeFormatter} instance.
     * 
     * @return
     * @throws RuntimeException
     */
    private Map getFormattingOptions() {
        Map options = new HashMap();
        options.put(JavaCore.COMPILER_SOURCE, compilerSource);
        options.put(JavaCore.COMPILER_COMPLIANCE, compilerCompliance);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, compilerTargetPlatform);

        if (configFile != null) {
            Map config = getOptionsFromConfigFile();
            if (Boolean.TRUE.equals(overrideConfigCompilerVersion)) {
                config.remove(JavaCore.COMPILER_SOURCE);
                config.remove(JavaCore.COMPILER_COMPLIANCE);
                config.remove(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM);
            }
            options.putAll(config);
        }

        return options;
    }

    /**
     * Read config file and return the config as {@link Map}.
     * 
     * @return
     * @throws RuntimeException
     */
    private Map getOptionsFromConfigFile() {

        InputStream configInput = null;
        try {
            configInput = Files.newInputStream(Paths.get(basedir.getAbsolutePath(), configFile));
        }
        catch (IOException e) {
            configInput = getClass().getClassLoader().getResourceAsStream(configFile);
        }

        if (configInput == null) {
            throw new RuntimeException("Config file [" + configFile + "] does not exist");
        } else {
            try {
                ConfigReader configReader = new ConfigReader();
                return configReader.read(configInput);
            }
            catch (IOException e) {
                throw new RuntimeException("Cannot read config file [" + configFile + "]", e);
            }
            catch (SAXException e) {
                throw new RuntimeException("Cannot parse config file [" + configFile + "]", e);
            }
            catch (ConfigReadException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            finally {
                if (configInput != null) {
                    try {
                        configInput.close();
                    }
                    catch (IOException e) {
                    }
                }
            }
        }
    }

    /**
     * Returns the lineEnding parameter as characters when the value is known
     * (LF, CRLF, CR) or can be determined from the file text (KEEP). Otherwise
     * null is returned.
     * 
     * @return
     */
    String getLineEnding(String fileDataString) {
        String lineEnd = null;
        if (LINE_ENDING_KEEP.equals(lineEnding)) {
            lineEnd = determineLineEnding(fileDataString);
        } else if (LINE_ENDING_LF.equals(lineEnding)) {
            lineEnd = LINE_ENDING_LF_CHAR;
        } else if (LINE_ENDING_CRLF.equals(lineEnding)) {
            lineEnd = LINE_ENDING_CRLF_CHARS;
        } else if (LINE_ENDING_CR.equals(lineEnding)) {
            lineEnd = LINE_ENDING_CR_CHAR;
        }
        return lineEnd;
    }

    /**
     * Returns the most occurring line-ending characters in the file text or
     * null if no line-ending occurs the most.
     * 
     * @return
     */
    String determineLineEnding(String fileDataString) {
        int lfCount = 0;
        int crCount = 0;
        int crlfCount = 0;

        for (int i = 0; i < fileDataString.length(); i++) {
            char c = fileDataString.charAt(i);
            if (c == '\r') {
                if ((i + 1) < fileDataString.length() && fileDataString.charAt(i + 1) == '\n') {
                    crlfCount++;
                    i++;
                } else {
                    crCount++;
                }
            } else if (c == '\n') {
                lfCount++;
            }
        }
        if (lfCount > crCount && lfCount > crlfCount) {
            return LINE_ENDING_LF_CHAR;
        } else if (crlfCount > lfCount && crlfCount > crCount) {
            return LINE_ENDING_CRLF_CHARS;
        } else if (crCount > lfCount && crCount > crlfCount) {
            return LINE_ENDING_CR_CHAR;
        }
        return null;
    }

    private class ResultCollector {
        private int successCount;
        private int failCount;
        private int skippedCount;
    }

    public void execute(String path) {
        Path startingDir = Paths.get(path);
        FormatExecutorRecursive pf = new FormatExecutorRecursive(this);
        try {
            log.debug("Will recurse into " + path);
            Files.walkFileTree(startingDir, pf);
            pf.finalize();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void executeOnSingleFile(String filename) {
        long startClock = System.currentTimeMillis();

        createResourceCollection();

        List<File> files = new ArrayList<File>();

        files.add(new File(filename));

        int numberOfFiles = files.size();
        // Log log = getLog();
        // log.info("Number of files to be formatted: " + numberOfFiles);

        if (numberOfFiles > 0) {

            ResultCollector rc = new ResultCollector();
            // Properties hashCache = readFileHashCacheFile();

            String basedirPath = getBasedirPath();
            for (int i = 0, n = files.size(); i < n; i++) {
                File file = (File) files.get(i);
                formatFile(file, rc, null, basedirPath);
            }

            long endClock = System.currentTimeMillis();

            log.info("Successfully formatted: " + rc.successCount + " file(s)");
            log.info("Fail to format        : " + rc.failCount + " file(s)");
            log.info("Skipped               : " + rc.skippedCount + " file(s)");
            log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
        }
    }

    public void executeOnSet(List<Path> filenames) {
        long startClock = System.currentTimeMillis();

        createResourceCollection();

        List<File> files = new ArrayList<File>();

        for (Path fn : filenames) {
            files.add(new File(fn.toAbsolutePath().toString()));
        }

        int numberOfFiles = files.size();
        // Log log = getLog();
        // log.info("Number of files to be formatted: " + numberOfFiles);

        if (numberOfFiles > 0) {

            ResultCollector rc = new ResultCollector();
            // Properties hashCache = readFileHashCacheFile();

            String basedirPath = getBasedirPath();
            for (int i = 0, n = files.size(); i < n; i++) {
                File file = (File) files.get(i);
                formatFile(file, rc, null, basedirPath);
            }

            long endClock = System.currentTimeMillis();

            log.info("Successfully formatted: " + rc.successCount + " file(s)");
            log.info("Fail to format        : " + rc.failCount + " file(s)");
            log.info("Skipped               : " + rc.skippedCount + " file(s)");
            log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
        }
    }
}
