package org.aion.harness.integ;

import java.io.File;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.*;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.util.NodeConfigurationBuilder;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventListenerTest {
    private static boolean doFullInitialization = false;
    private static File nodeDirectory = NodeFileManager.getNodeDirectory();
    private static File kernelDirectory = NodeFileManager.getKernelDirectory();
    private static Address destination;
    private static PrivateKey preminedPrivateKey;
    private static String ip = "127.0.0.1";
    private static String port = "8545";

    private RPC rpc;
    private LocalNode node;

    @Before
    public void setup() throws IOException, DecoderException, InvalidKeySpecException {
        destination = new Address(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));
        preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));
        deleteInitializationDirectories();
        this.node = NodeFactory.getNewLocalNodeInstance(NodeFactory.NodeType.JAVA_NODE);
        this.node.configure(NodeConfigurationBuilder.defaultConfigurations(false));
        this.rpc = new RPC(ip, port);
    }

    @After
    public void tearDown() throws Exception {
        shutdownNodeIfRunning();
        deleteInitializationDirectories();
        deleteLogs();
        this.node = null;
        this.rpc = null;
    }

    @Test
    public void testWaitForMinersToStart() throws IOException, InterruptedException {
        initializeNodeWithChecks();

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        LogEventResult requestResult = listener.listenForMinersToStart(2, TimeUnit.MINUTES).get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testTransactionProcessed() throws Exception {
        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        if (!transactionResult.isSuccess()) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        this.node.initializeKernel();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        RawTransaction transaction = transactionResult.getTransaction();

        FutureResult<LogEventResult> futureResult = listener.listenForTransactionToBeProcessed(
            transaction,
            2,
            TimeUnit.MINUTES);

        this.rpc.sendTransaction(transaction);

        LogEventResult requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());

        Thread.sleep(15000);

        // ------------------------------------------------------------------------------

        transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ONE);

        if (!transactionResult.isSuccess()) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        listener = NodeListener.listenTo(this.node);

        transaction = transactionResult.getTransaction();

        futureResult = listener.listenForTransactionToBeProcessed(
            transaction,
            2,
            TimeUnit.MINUTES);

        this.rpc.sendTransaction(transaction);

        requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testWaitForTransactionToBeRejected() throws DecoderException, IOException, InterruptedException, InvalidKeySpecException {
        // create a private key, has zero balance, sending balance from it would cause transaction to fail
        PrivateKey privateKeyWithNoBalance = PrivateKey.fromBytes(Hex.decodeHex("00e9f9800d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));

        TransactionResult transactionResult = constructTransaction(
            privateKeyWithNoBalance,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        if (!transactionResult.isSuccess()) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        this.node.initializeKernel();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        RawTransaction transaction = transactionResult.getTransaction();

        FutureResult<LogEventResult> futureResult = listener.listenForTransactionToBeProcessed(
            transaction,
            2,
            TimeUnit.MINUTES);

        this.rpc.sendTransaction(transaction);

        LogEventResult requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        // check it was a rejected event that was observed
        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("rejected"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testFutureResultBlocksOnGet() throws IOException, InterruptedException {
        initializeNodeWithChecks();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);
        IEvent event = new Event("I will never occur");

        long duration = 10;

        long start = System.nanoTime();
        listener.listenForEvent(event, duration, TimeUnit.SECONDS).get();
        long end = System.nanoTime();

        // Basically we expect to wait approximately duration seconds since this is a blocking call.
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(end - start) >= (duration - 1));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testListenForDoesNotBlock() throws IOException, InterruptedException {
        initializeNodeWithChecks();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);
        IEvent event = new Event("I will never occur");

        long duration = 10;

        long start = System.nanoTime();
        FutureResult<LogEventResult> futureResult = listener.listenForEvent(event, duration, TimeUnit.SECONDS);
        long end = System.nanoTime();

        // Basically we expect to return much earlier than the duration. Using less than here just
        // as in opposition to the blocking event test above & to avoid making assumptions.
        // So long as the event never does occur this property is strong enough.
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(end - start) < (duration - 1));

        // Verify that we did in fact expire
        assertTrue(futureResult.get().eventExpired());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return RawTransaction
            .buildAndSignFvmTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

    private Result initializeNode() throws IOException, InterruptedException {
        if (doFullInitialization) {
            Result result = this.node.buildKernel();
            if (!result.isSuccess()) {
                return result;
            }
        }

        return this.node.initializeKernel();
    }

    private void initializeNodeWithChecks() throws IOException, InterruptedException {
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

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }
}
