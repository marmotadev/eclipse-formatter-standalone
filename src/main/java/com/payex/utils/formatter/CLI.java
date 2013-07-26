package com.payex.utils.formatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class CLI {
    private static final Logger log = Logger.getLogger(CLI.class.getName());
    private FormatExecutor      fe  = new FormatExecutor();

    public static void main(String[] args) {
        CLI cli = new CLI();
        String fname = StringUtils.join(args);
        Path path = Paths.get(fname);
        if (fname.trim().isEmpty())
            log.error("No file provided");
        else if (Files.notExists(path)) {
            log.error("Path not exists: " + path.toAbsolutePath());
        }
        else
        	cli.format(path);

    }

    private void format(Path path) {
        if (Files.isDirectory(path)) {
            formatDir(path.toAbsolutePath().toString());
        } else
            formatFile(path.toAbsolutePath().toString());
    }

    private void formatFile(String file) {
        fe.executeOnSingleFile(file);
    }

    private void formatDir(String file) {
        fe.execute(file);
    }

}
