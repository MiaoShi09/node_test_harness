package org.aion.harness.integ.resources;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogWriter implements Runnable {
    private CountDownLatch waitForStart = new CountDownLatch(1);

    private AtomicBoolean dead = new AtomicBoolean(false);
    private final File outputLogFile;
    private int counter = 0;
    private String listenMessage = null;

    private static final String logMessage = "This is a log message, #";

    public LogWriter(File outputLogFile) {
        this.outputLogFile = outputLogFile;
    }

    public void start() {
        this.waitForStart.countDown();
    }

    @Override
    public void run() {
        BufferedWriter bufferedWriter = null;

        try {
            // Wait until we are told to begin.
            this.waitForStart.await();

            if (this.outputLogFile == null) {
                throw new NullPointerException("Output file cannot be null");
            }

            if (!this.outputLogFile.exists()) {
                throw new IllegalStateException("Output file does not exist");
            }

            // Create the writer.
            bufferedWriter = new BufferedWriter(new FileWriter(this.outputLogFile));

            while (!this.dead.get()) {
                String message = getListenMessage();

                if (message != null) {
                    bufferedWriter.write(message + "\n");
                    bufferedWriter.flush();
                    setListenMessage(null);
                } else {
                    bufferedWriter.write(logMessage + counter + "\n");
                    bufferedWriter.flush();
                    this.counter++;
                }

                Thread.sleep(500);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private synchronized String getListenMessage() {
        return this.listenMessage;
    }

    public synchronized void setListenMessage(String message) {
        this.listenMessage = message;
    }

    public void kill() {
        this.dead.set(true);
    }

    public boolean isAlive() {
        return !this.dead.get();
    }

}
