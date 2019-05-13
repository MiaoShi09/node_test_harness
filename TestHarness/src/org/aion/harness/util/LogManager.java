package org.aion.harness.util;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import org.aion.harness.result.Result;
import org.apache.commons.io.FileUtils;

/**
 * A class that is used to set up and manage the log files generated by an active node.
 */
public final class LogManager {
    private File currentOutputLog;
    private File currentErrorLog;

    /**
     * This method creates the necessary output and error log files in the logs directory. If this
     * directory does not exist yet, then this method creates it.
     *
     * This method will also archive any outdated logs. That is, it will collect any log files
     * currently in the logs directly and place them in an archive directory. Once more, if this
     * archive directory does not exist then it will be created.
     *
     * After calling this method successfully, the current output and error log files should exist.
     *
     * @return A result indicating the successfulness of this call.
     */
    public Result setupLogFiles() throws IOException {

        // Set the logs to null so we can trust these variables after multiple calls.
        this.currentOutputLog = null;
        this.currentErrorLog = null;

        if (!createLogsDirectoryIfDoesNotExist()) {
            return Result.unsuccessfulDueTo("failed to create log directory");
        }

        // archive any old log files.
        archiveLogs();

        long currentTimeInMillis = System.currentTimeMillis();

        // create the new log files.
        this.currentOutputLog = createNewStdoutLog(currentTimeInMillis);
        if (this.currentOutputLog == null) {
            return Result.unsuccessfulDueTo("failed to create stdout log");
        }

        this.currentErrorLog = createNewStderrLog(currentTimeInMillis);
        if (this.currentErrorLog == null) {
            return Result.unsuccessfulDueTo("failed to create stderr log");
        }

        return Result.successful();
    }

    /**
     * Returns the current log file used to log a node's output, or null if no such file exists yet.
     *
     * @return The current output log file.
     */
    public File getCurrentOutputLogFile() {
        return this.currentOutputLog;
    }

    /**
     * Returns the current log file used to log a node's errors, or null if no such file exists yet.
     *
     * @return The current error log file.
     */
    public File getCurrentErrorLogFile() {
        return this.currentErrorLog;
    }

    /**
     * creates the logs directory if it does not exist, otherwise does nothing.
     */
    private boolean createLogsDirectoryIfDoesNotExist() {
        return (NodeFileManager.getLogsDirectory().exists()) ? true : NodeFileManager.getLogsDirectory().mkdir();
    }

    /**
     * Moves any outstanding log files into the archived directory if they exist.
     */
    private void archiveLogs() throws IOException {
        File[] logEntries = NodeFileManager.getLogsDirectory().listFiles();

        if (logEntries == null) {
            return;
        }

        for (File entry : logEntries) {
            if (entry.isFile()) {
                File destination = findUniqueArchiveDestinationName(entry.getName());
                FileUtils.moveFile(entry, destination);
            }
        }
    }

    /**
     * Creates the stdout log file in the logs directory and returns this file if successful, otherwise returns null.
     *
     * ASSUMPTION: logs directory exists.
     */
    private File createNewStdoutLog(long currentTimeInMillis) throws IOException {
        File stdoutLogFile = new File(NodeFileManager.getLogsDirectory() + File.separator + createLogFilename("out", currentTimeInMillis));
        return (stdoutLogFile.createNewFile()) ? stdoutLogFile : null;
    }

    /**
     * Creates the stderr log file in the logs directory and returns this file if successful, otherwise returns null.
     *
     * ASSUMPTION: logs directory exists.
     */
    private File createNewStderrLog(long currentTimeInMillis) throws IOException {
        File stdoutLogFile = new File(NodeFileManager.getLogsDirectory() + File.separator + createLogFilename("err", currentTimeInMillis));
        return (stdoutLogFile.createNewFile()) ? stdoutLogFile : null;
    }

    /**
     * Creates a filename following a specific log file naming convention with the option of
     * specifying a postfix to make the name unique.
     */
    private String createLogFilename(String postfix, long currentTimeInMillis) {
        Date currentDate = new Date(currentTimeInMillis);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);

        return calendar.get(Calendar.YEAR) + "-"
            + String.format("%02d", (calendar.get(Calendar.MONTH) +  1)) + "-"
            + String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)) + "-"
            + String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)) + ":"
            + String.format("%02d", calendar.get(Calendar.MINUTE)) + ":"
            + String.format("%02d", calendar.get(Calendar.SECOND)) + "-"
            + postfix + ".txt";
    }

    /**
     * Returns a file such that this file does not exist in the archive directory but its name is
     * derived from the provided filename.
     *
     * If the provided filename already exists in the archive directory then this method simply
     * appends a second postfix to the name to make it unique and continues to do so until it arrives
     * at a unique name for the file.
     */
    private File findUniqueArchiveDestinationName(String filename) throws IOException {
        String filenameExtension = filename.substring(filename.lastIndexOf('.'));
        String filenameWithoutExtension = filename.substring(0, filename.lastIndexOf('.'));
        String canonicalName = NodeFileManager.getLogsArchiveDirectory().getCanonicalPath() + File.separator + filenameWithoutExtension;

        int number = 1;
        File file = new File(canonicalName + filenameExtension);

        while (file.exists()) {
            file = new File(canonicalName + "(" + number + ")" + filenameExtension);
            number++;
        }

        return file;
    }

}
