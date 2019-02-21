package org.aion.harness.integ;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.Node;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.impl.JavaNode;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.EventRequestResult;
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

    private RPC rpc;
    private Node node;

    @Before
    public void setup() throws IOException, DecoderException {
        destination = Address.createAddress(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));
        preminedPrivateKey = PrivateKey.createPrivateKey(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));
        deleteInitializationDirectories();
        this.node = NodeFactory.getNewNodeInstance(NodeFactory.NodeType.JAVA_NODE);
        this.rpc = new RPC();
    }

    @After
    public void tearDown() throws IOException {
        shutdownNodeIfRunning();
        deleteInitializationDirectories();
        deleteLogs();
        this.node = null;
        this.rpc = null;
    }

    @Test
    public void testWaitForMinersToStart() {
        initializeNodeWithChecks();

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.success);
        assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();

        EventRequestResult requestResult = listener.waitForMinersToStart(TimeUnit.MINUTES.toMillis(2));

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.success);
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testTransactionProcessed() throws Exception {
        TransactionResult transactionResult = constructTransaction(
                preminedPrivateKey,
                destination,
                BigInteger.ONE,
                BigInteger.ZERO);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        ((JavaNode) this.node).initialize(false);
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.success);
        assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();


        this.rpc.sendTransaction(transactionResult.getTransaction());

        EventRequestResult requestResult = listener.waitForTransactionToBeProcessed(
            transactionResult.getTransaction().getTransactionHash(),
            TimeUnit.MINUTES.toMillis(2));

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.success);
        assertFalse(this.node.isAlive());

        Thread.sleep(15000);

        // ------------------------------------------------------------------------------

        transactionResult = constructTransaction(
                preminedPrivateKey,
                destination,
                BigInteger.ONE,
                BigInteger.ONE);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        listener = new NodeListener();

        this.rpc.sendTransaction(transactionResult.getTransaction());

        requestResult = listener.waitForTransactionToBeProcessed(
            transactionResult.getTransaction().getTransactionHash(),
            TimeUnit.MINUTES.toMillis(2));

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.success);
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testWaitForTransactionToBeRejected() throws DecoderException {
        // create a private key, has zero balance, sending balance from it would cause transaction to fail
        PrivateKey privateKeyWithNoBalance = PrivateKey.createPrivateKey(Hex.decodeHex("00e9f9800d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));

        TransactionResult transactionResult = constructTransaction(
                privateKeyWithNoBalance,
                destination,
                BigInteger.ONE,
                BigInteger.ZERO);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        ((JavaNode) this.node).initialize(false);
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.success);
        assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();

        this.rpc.sendTransaction(transactionResult.getTransaction());

        EventRequestResult requestResult = listener.waitForTransactionToBeProcessed(
            transactionResult.getTransaction().getTransactionHash(),
            TimeUnit.MINUTES.toMillis(2));

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        // check it was a rejected event that was observed
        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("rejected"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.success);
        assertFalse(this.node.isAlive());
    }

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return Transaction
            .buildAndSignTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

    private Result initializeNode() {
        if (doFullInitialization) {
            Result result = this.node.buildKernel();
            if (!result.success) {
                return result;
            }
        }

        return this.node.initializeKernel();
    }

    private void initializeNodeWithChecks() {
        Result result = initializeNode();
        assertTrue(result.success);

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
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.success);
            assertFalse(this.node.isAlive());
        }
    }
}
