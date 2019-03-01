package org.aion.harness.main.impl;

import org.aion.harness.main.Node;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.global.SingletonFactory;
import org.aion.harness.main.types.Network;
import org.aion.harness.main.types.NodeConfigurations;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.Result;
import org.aion.harness.result.StatusResult;
import org.aion.harness.util.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.TimeUnit;

/**
 * A node that wraps the Java kernel.
 *
 * A JavaNode is not thread-safe.
 */
public final class JavaNode implements Node {
    private NodeConfigurations configurations = null;

    // The running instance of the kernel.
    private Process runningKernel = null;

    /**
     * Configures the node with the specified settings.
     *
     * This method must be called prior to any other actionable method in this class.
     *
     * @param configurations the configuration settings.
     */
    @Override
    public void configure(NodeConfigurations configurations) {
        if (isAlive()) {
            throw new IllegalStateException("Cannot set a node's configurations while it is running.");
        }

        if (configurations == null) {
            throw new NullPointerException("Cannot configure node with null configurations.");
        }

        this.configurations = configurations;
    }

    /**
     * Builds the kernel from source.
     *
     * Displays the I/O of the build process.
     *
     * @return a result indicating the success of failure of this method.
     */
    @Override
    public Result buildKernelVerbose() {
        return buildJavaKernel(true);
    }

    /**
     * Builds the kernel from source.
     *
     * @return a result indicating the success of failure of this method.
     */
    @Override
    public Result buildKernel() {
        return buildJavaKernel(false);
    }

    @Override
    public Result initializeKernelAndPreserveDatabaseVerbose() {
        return initializePreserveDatabase(true);
    }

    @Override
    public Result initializeKernelAndPreserveDatabase() {
        return initializePreserveDatabase(false);
    }

    @Override
    public Result initializeKernelVerbose() {
        return initialize(true);
    }

    @Override
    public Result initializeKernel() {
        return initialize(false);
    }

    /**
     * Starts a node.
     *
     * @throws IllegalStateException if the node is already started or the kernel does not exist.
     */
    @Override
    public Result start() {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot start kernel.");
        }
        if (isAlive()) {
            throw new IllegalStateException("there is already a kernel running.");
        }

        if (!NodeFileManager.getKernelDirectory().exists()) {
            throw new IllegalStateException("there is no kernel directory!");
        }

        System.out.println(Assumptions.LOGGER_BANNER + "Starting Java kernel node...");

        try {
            ProcessBuilder builder = new ProcessBuilder("./aion.sh", "-n", this.configurations.getNetwork().string())
                .directory(NodeFileManager.getKernelDirectory());

            LogManager logManager = SingletonFactory.singleton().logManager();
            File outputLog = logManager.getCurrentOutputLogFile();

            if (outputLog == null) {
                logManager.setupLogFiles();
                outputLog = logManager.getCurrentOutputLogFile();
            }

            builder.redirectOutput(outputLog);
            builder.redirectError(logManager.getCurrentErrorLogFile());

            this.runningKernel = builder.start();

            return waitForRpcServerToStart(outputLog);

        } catch (Exception e) {
            return Result.unsuccessfulDueToException(e);
        }
    }

    /**
     * Stops the node if it is currently running.
     */
    @Override
    public Result stop() {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot stop kernel.");
        }

        Result result;

        if (isAlive()) {
            System.out.println(Assumptions.LOGGER_BANNER + "Stopping Java kernel node...");

            try {

                this.runningKernel.destroy();
                boolean shutdown = this.runningKernel.waitFor(1, TimeUnit.MINUTES);
                this.runningKernel = null;
                SingletonFactory.singleton().logReader().stopReading();

                result = (shutdown) ? Result.successful() : Result.unsuccessfulDueTo("Timed out waiting for node to shut down!");

            } catch (Exception e) {
                result = Result.unsuccessfulDueToException(e);
            } finally {
                System.out.println(Assumptions.LOGGER_BANNER + "Java kernel node stopped.");
            }

        } else {
            result = Result.unsuccessfulDueTo("Node is not currently alive!");
        }

        // Finds the kernel and kills it (above we are killing the aion.sh script,
        // which is not guaranteed to kill the kernel). We find these processes because we know the
        // directory of the executable, so we can hunt it down precisely.
        String executableDir = NodeFileManager.getDirectoryOfExecutableKernel().getAbsolutePath();
        ProcessHandle.allProcesses()
            .filter(process -> process.info().command().toString().contains(executableDir))
            .forEach(kernel -> kernel.destroy());

        return result;
    }

    /**
     * Returns true if the node is currently running.
     */
    @Override
    public boolean isAlive() {
        return ((this.runningKernel != null) && (this.runningKernel.isAlive()));
    }

    /**
     * Resets the state of the node.
     *
     * @throws IllegalStateException if the node is currently running or there is no kernel.
     */
    @Override
    public Result resetState() {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot reset kernel state.");
        }
        if (isAlive()){
            throw new IllegalStateException("Cannot reset state while the node is running.");
        }

        if (!NodeFileManager.getKernelDirectory().exists()) {
            throw new IllegalStateException("there is no kernel directory!");
        }

        System.out.println(Assumptions.LOGGER_BANNER + "Resetting the state of the Java kernel node...");

        try {

            File database = this.configurations.getDatabase();
            if (database.exists()) {
                FileUtils.deleteDirectory(database);
            }
            return Result.successful();

        } catch (Exception e) {
            return Result.unsuccessfulDueToException(e);
        }
    }

    /**
     * Returns the network that this node will attempt to connect to when {@code start()} is invoked,
     * or, if the node is running, then the network it has connected to.
     *
     * Returns null if no network has been configured yet.
     *
     * @return the network the node is on.
     */
    @Override
    public Network getNetwork() {
        return (this.configurations == null) ? null : this.configurations.getNetwork();
    }

    private Result initializePreserveDatabase(boolean verbose) {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot initialize kernel.");
        }

        File nodeDirectory = NodeFileManager.getNodeDirectory();
        File kernelDatabaseDirectory = this.configurations.getDatabase();
        File temporaryDatabaseDirectory = NodeFileManager.getTemporaryDatabase();

        try {
            if (nodeDirectory.exists()) {
                if (kernelDatabaseDirectory.exists()) {

                    // if preserved database directory already exist, delete it
                    if (temporaryDatabaseDirectory.exists()) {
                        throw new IllegalStateException("there is already a database at " + temporaryDatabaseDirectory);
                    }

                    // move the kernel database
                    FileUtils.moveDirectory(kernelDatabaseDirectory, temporaryDatabaseDirectory);

                    // delete node directory
                    FileUtils.deleteDirectory(nodeDirectory);

                    if (!nodeDirectory.mkdir()) {
                        throw new IllegalStateException("Failed to make directory: " + nodeDirectory);
                    }

                    // move the preserved database back to the node directory
                    FileUtils.moveDirectory(temporaryDatabaseDirectory, kernelDatabaseDirectory);
                }

            } else {
                if (!nodeDirectory.mkdir()) {
                    throw new IllegalStateException("Failed to make directory: " + nodeDirectory);
                }
            }
        } catch (Exception e) {
            return Result.unsuccessfulDueToException(e);
        }

        return untarAndSetupKernel(verbose);
    }

    private Result initialize(boolean verbose) {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot initialize kernel.");
        }

        File nodeDestination = NodeFileManager.getNodeDirectory();
        if (nodeDestination.exists()) {
            throw new IllegalStateException("node directory already exists.");
        }

        if (!nodeDestination.mkdir()) {
            throw new IllegalStateException("Failed to make directory: " + nodeDestination);
        }

        return untarAndSetupKernel(verbose);
    }

    private Result untarAndSetupKernel(boolean verbose) {
        System.out.println(Assumptions.LOGGER_BANNER + "Fetching the built kernel...");

        if (verbose) {
            System.out.println(Assumptions.LOGGER_BANNER + "Fetching Java Kernel tar file at location: "
                    + this.configurations.getKernelBuildDirectory());
        }

        File kernelBuildDirectory = this.configurations.getKernelBuildDirectory();
        if (!kernelBuildDirectory.isDirectory()) {
            throw new IllegalStateException(
                "Unable to find kernel build directory at: " + kernelBuildDirectory);
        }

        try {
            File kernelTarFile = null;
            File[] entries = kernelBuildDirectory.listFiles();
            if (entries == null) {
                throw new NoSuchFileException("Could not find kernel tar file.");
            }

            if (verbose) {
                System.out.println(Assumptions.LOGGER_BANNER + "Looking for file with the following format: aion-v***.tar.bz2");
            }

            for (File file : entries) {
                if (Assumptions.KERNEL_TAR_PATTERN.matcher(file.getName()).matches()) {
                    kernelTarFile = file;
                }
            }

            if (kernelTarFile == null) {
                throw new NoSuchFileException("Could not find kernel tar file.");
            }


            File tarDestination = new File(
                    NodeFileManager.getNodeDirectory().getPath() + File.separator + Assumptions.NEW_KERNEL_TAR_NAME);
            Files.copy(kernelTarFile.toPath(), tarDestination.toPath());

            if (verbose) {
                System.out.println(Assumptions.LOGGER_BANNER + "Unzipping Java Kernel tar file using command: tar xvjf");
            }

            ProcessBuilder builder = new ProcessBuilder("tar", "xvjf", tarDestination.getName())
                .directory(tarDestination.getParentFile());

            if (verbose) {
                builder.inheritIO();
            }

            int untarStatus = builder.start().waitFor();
            tarDestination.delete();

            if (!SingletonFactory.singleton().logManager().setupLogFiles().success) {
                return Result.unsuccessfulDueTo("Failed to set up log files!");
            }

            return (untarStatus == 0)
                ? Result.successful()
                : Result.unsuccessfulDueTo("Failed to prepare the kernel");

        } catch (Exception e) {
            return Result.unsuccessfulDueToException(e);
        }
    }

    private Result buildJavaKernel(boolean verbose) {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot build kernel.");
        }

        System.out.println(Assumptions.LOGGER_BANNER + "Building the Java kernel from source...");

        try {

            if (verbose) {
                System.out.println(Assumptions.LOGGER_BANNER + "Building Java Kernel from command: ./gradlew clean pack");
                System.out.println(Assumptions.LOGGER_BANNER + "Building Java Kernel at location: "
                    + this.configurations.getKernelSourceDirectory());
            }

            ProcessBuilder builder = new ProcessBuilder("./gradlew", "clean", "pack")
                .directory(this.configurations.getKernelSourceDirectory());

            if (verbose) {
                builder.inheritIO();
            }

            return (builder.start().waitFor() == 0)
                ? Result.successful()
                : Result.unsuccessfulDueTo("An error occurred building the kernel!");

        } catch (Exception e) {
            return Result.unsuccessfulDueToException(e);
        }
    }

    /**
     * Waits for the RPC server to start before returning.
     */
    private Result waitForRpcServerToStart(File outputLog) throws InterruptedException {
        // We wait for the rpc event to know we are ok to return. There is a chance that we will miss
        // this event and start listening too late. That is why we timeout after 20 seconds, which
        // should be more than sufficient for the server to activate, and then we check if the node
        // is still live.
        // See issue #1 relating to this decision, which will be refactored in the future.

        if (isAlive()) {
            // We wait for the Rpc event or else 20 seconds, in case we come too late and never see it.
            IEvent rpcEvent = new Event("rpc-server - (UNDERTOW) started");

            StatusResult result = SingletonFactory.singleton().logReader().startReading(outputLog);
            if (!result.success) {
                return Result.unsuccessfulDueTo(result.error);
            }

            new NodeListener().listenForEvent(rpcEvent, 20, TimeUnit.SECONDS).get();
        }

        return (isAlive())
            ? Result.successful()
            : Result.unsuccessfulDueTo("Node failed to start!");
    }
}
