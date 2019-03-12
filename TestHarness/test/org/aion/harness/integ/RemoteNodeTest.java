package org.aion.harness.integ;

import org.aion.harness.integ.resources.LogWriter;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.Event;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.types.internal.NodeConfigurationBuilder;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.aion.harness.main.impl.RemoteNode;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import java.io.File;
import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;


public class RemoteNodeTest {
    private static File nodeDirectory = NodeFileManager.getNodeDirectory();
    private static File kernelDirectory = NodeFileManager.getKernelDirectory();

    private LocalNode localNode;
    private RemoteNode remoteNode;

    /**
     * Set to false for testing convenience, tune to true if starting from scratch.
     */
    private static boolean doFullInitialization = false;

    @Before
    public void setup() throws IOException {
        deleteInitializationDirectories();
        this.remoteNode = NodeFactory.getNewRemoteNodeInstance(NodeType.JAVA_NODE);
        this.localNode = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        this.localNode.configure( NodeConfigurationBuilder.defaultConfigurations());
    }

    @After
    public void tearDown() throws IOException {
        shutdownNodeIfRunning();
        deleteInitializationDirectories();
        deleteLogs();
        this.remoteNode = null;
        this.localNode = null;
    }

    @Test (expected = NullPointerException.class)
    public void testConnectNullFile() {
        this.remoteNode.connect(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConnectFileNotExist() {
        File fileNotExist = new File(NodeFileManager.getLogsDirectory().getAbsolutePath() + File.separator + "notExist");
        this.remoteNode.connect(fileNotExist);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConnectLogIsNotAFile() {
        File notAFile = new File(NodeFileManager.getLogsDirectory().getAbsolutePath() + File.separator + "newDirectory");
        notAFile.mkdir();

        this.remoteNode.connect(notAFile);
    }

    @Test
    public void testConnectRemoteNodeToLocalNodeLogFile() throws InterruptedException {
        initializeNodeWithChecks();
        Result result = this.localNode.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.localNode.isAlive());

        // get the log file and connect with remote node
        File localNodeLogFile = null;
        File[] logFiles = NodeFileManager.getLogsDirectory().listFiles();
        for (File file: logFiles) {
            if (file.getName().contains("out")) {
                localNodeLogFile = file;
            }
        }

        result = this.remoteNode.connect(localNodeLogFile);
        System.out.println("Connect result = " + result);
        assertTrue(result.isSuccess());

        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> future = nodeListener.listenForEvent(new Event("DEBUG"), 30, TimeUnit.SECONDS);

        LogEventResult waitResult = future.get();
        System.out.println("Listener result = " + future.toString());
        assertTrue(waitResult.eventWasObserved());

        this.remoteNode.disconnect();
        this.localNode.stop();
    }

    @Test
    public void testRemoteListenerWhenRemoteNodeIsDisconnected() throws IOException, InterruptedException {
        // create a log file
        File localNodeLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(localNodeLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        Result result = this.remoteNode.connect(localNodeLogFile);
        System.out.println("Connect result = " + result);
        assertTrue(result.isSuccess());

        // disconnect the remote node
        this.remoteNode.disconnect();

        // try to listen when listener has stopped listening
        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(new Event("message"), 30, TimeUnit.SECONDS);

        // get the result of the listener.
        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + futureResult.toString());
        assertTrue(waitResult.eventWasRejected());

        // shutdown thread
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testRemoteNode() throws InterruptedException, IOException {
        String message = "listen to this message";

        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // set up remote node and connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Start listening for the message we are going to set.
        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(new Event(message), 1, TimeUnit.MINUTES);

        // put a message to the logs, this is the message we are looking for.
        logWriter.setListenMessage(message);

        // get the result of the listener.
        LogEventResult waitResult = futureResult.get();

        System.out.println("Listener result = " + futureResult.toString());
        assertTrue(waitResult.eventWasObserved());

        // disconnect the node and shut down the thread.
        remoteNode.disconnect();
        logWriter.kill();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testLocalAndRemoteNodeTogether() throws InterruptedException, IOException {
        String message = "my message";

        // start local node
        initializeNodeWithChecks();
        Result result = this.localNode.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.localNode.isAlive());

        // create a log file for the remote node to listen to.
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // set up remote node and connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Listen to the local node.
        NodeListener localNodeListener = NodeListener.listenTo(this.localNode);
        FutureResult<LogEventResult> localFuture = localNodeListener.listenForEvent(new Event("DEBUG"), 1, TimeUnit.MINUTES);

        // Listen to the remote node.
        NodeListener remoteNodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> remoteFuture = remoteNodeListener.listenForEvent(new Event(message), 1, TimeUnit.MINUTES);

        // put a message to the logs, this is the message we are looking for.
        logWriter.setListenMessage(message);

        // get the results of the listeners.
        LogEventResult localResult = localFuture.get();
        LogEventResult remoteResult = remoteFuture.get();

        System.out.println("Local listener result = " + localFuture);
        System.out.println("Remote listener result = " + remoteFuture);

        assertTrue(localResult.eventWasObserved());
        assertTrue(remoteResult.eventWasObserved());

        // stop local node
        result = this.localNode.stop();
        System.out.println("Stop result = " + result);
        assertTrue(result.isSuccess());
        assertFalse(this.localNode.isAlive());

        // disconnect remote node and shut down the thread.
        this.remoteNode.disconnect();
        logWriter.kill();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test (expected = IllegalStateException.class)
    public void testConnectMultipleTimesToRemoteNode() throws IOException {
        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // connect to log file - once
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // connect to log file - again
        connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());
    }

    @Test
    public void testDisconnectMultipleTimesToRemoteNode() throws IOException, InterruptedException {
        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // disconnect - once
        Result disconnectResult = this.remoteNode.disconnect();
        assertTrue(disconnectResult.isSuccess());

        // disconnect - once again
        disconnectResult = this.remoteNode.disconnect();
        assertTrue(disconnectResult.isSuccess());
    }

    private File createRemoteLogFile() throws IOException {
        File logDirectory = new File(NodeFileManager.getLogsDirectory().getAbsolutePath());
        File outputLogFile = new File(NodeFileManager.getLogsDirectory().getAbsolutePath() + File.separator + "testRemoteNode.txt");
        if (!logDirectory.exists()) {
            assertTrue(logDirectory.mkdir());
        }

        if (!outputLogFile.exists()) {
            assertTrue(outputLogFile.createNewFile());
        }

        return outputLogFile;
    }

    private Result initializeNode() {
        if (doFullInitialization) {
            Result result = this.localNode.buildKernel();
            if (!result.isSuccess()) {
                return result;
            }
        }

        return this.localNode.initializeKernel();
    }

    private void initializeNodeWithChecks() {
        Result result = initializeNode();
        assertTrue(result.isSuccess());

        // verify the node directory was created.
        assertTrue(nodeDirectory.exists());
        assertTrue(nodeDirectory.isDirectory());

        // veirfy the node directory contains the aion directory.
        File[] nodeDirectoryEntries = nodeDirectory.listFiles();
        assertNotNull(nodeDirectoryEntries);
        assertEquals(1, nodeDirectoryEntries.length);
        assertEquals(kernelDirectory, nodeDirectoryEntries[0]);
        assertTrue(nodeDirectoryEntries[0].isDirectory());
    }

    private static void deleteInitializationDirectories() throws IOException {
        if (nodeDirectory.exists()) {
            FileUtils.deleteDirectory(nodeDirectory);
        }
        if (kernelDirectory.exists()) {
            FileUtils.deleteDirectory(kernelDirectory);
        }
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private void shutdownNodeIfRunning() {
        if ((this.localNode != null) && (this.localNode.isAlive())) {
            Result result = this.localNode.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.localNode.isAlive());
        }
    }
}
