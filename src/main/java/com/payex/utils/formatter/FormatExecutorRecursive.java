package com.payex.utils.formatter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class FormatExecutorRecursive extends SimpleFileVisitor<Path> {
    private static final Logger log      = Logger.getLogger(FormatExecutorRecursive.class.getName());
    private FormatExecutor      fe;
    private List<Path>          paths    = new ArrayList<Path>();
    private boolean             showskip = true;

    public FormatExecutorRecursive(FormatExecutor formatExecutor) {
        this.fe = formatExecutor;

    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
        // log.debug("nc" + file.getNameCount() + " " + file);
        Path last = file.getName(file.getNameCount() == 0 ? 0 : file.getNameCount() - 1);
        // log.trace("LAST: " + last);
        String lasts = last.toString();

        if (lasts.endsWith(".java")) {
            if (attr.isSymbolicLink()) {
                log.debug("Symbolic link:  " + file);
            } else if (attr.isRegularFile()) {
                log.debug("Regular file:  " + file);
            } else {
                log.debug("Other:  " + file);
            }
            // log.debug("TRACE::" + file.getFileName().toString());
            Path what = file.toAbsolutePath();
            // log.trace("Adding: " + what);
            paths.add(what);
        } else if (showskip)
            log.debug("Skipping file: " + file);
        // System.out.println("(" + attr.size() + "bytes)");
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
        Path last = file.getName(file.getNameCount() == 0 ? 0 : file.getNameCount() - 1);
        if (last.endsWith("CVS") || last.endsWith("target") || last.endsWith(".settings")) {
            if (showskip)
                log.debug("Skipping : " + file);
            return FileVisitResult.SKIP_SUBTREE;
        } else
            return FileVisitResult.CONTINUE;
    }

    public void finalize() {
        log.info("Finished scanning, will convert " + paths.size() + " files");
        fe.executeOnSet(paths);
    }
}
