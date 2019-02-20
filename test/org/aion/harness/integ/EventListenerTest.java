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
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        deleteInitializationDirectories();
        deleteLogs();
        this.node = null;
        this.rpc = null;
    }

    @Test
    public void testWaitForMinersToStart() throws IOException, InterruptedException {
        initializeNodeWithChecks();

        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();

        EventRequestResult result = listener.waitForMinersToStart(TimeUnit.MINUTES.toMillis(2));

        System.out.println(result);
        Assert.assertTrue(result.eventWasObserved());

        this.node.stop();
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

        ((JavaNode) this.node).initializeButSkipKernelBuild(false);
        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();


        this.rpc.sendTransaction(transactionResult.getTransaction());

        EventRequestResult result = listener.waitForTransactionToBeProcessed(transactionResult.getTransaction().getTransactionHash(), TimeUnit.MINUTES.toMillis(2));
        System.out.println(result);
        Assert.assertTrue(result.eventWasObserved());

        List<String> observed = result.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        this.node.stop();

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

        result = listener.waitForTransactionToBeProcessed(transactionResult.getTransaction().getTransactionHash(), TimeUnit.MINUTES.toMillis(2));
        System.out.println(result);
        Assert.assertTrue(result.eventWasObserved());

        observed = result.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        this.node.stop();
    }

    @Test
    public void testWaitForTransactionToBeRejected() throws IOException, InterruptedException, DecoderException {
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

        ((JavaNode) this.node).initializeButSkipKernelBuild(false);
        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        NodeListener listener = new NodeListener();

        this.rpc.sendTransaction(transactionResult.getTransaction());

        EventRequestResult result = listener.waitForTransactionToBeProcessed(transactionResult.getTransaction().getTransactionHash(), TimeUnit.MINUTES.toMillis(2));
        System.out.println(result);
        Assert.assertTrue(result.eventWasObserved());

        // check it was a rejected event that was observed
        List<String> observed = result.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("rejected"));

        this.node.stop();
    }

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return Transaction
            .buildAndSignTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

    private Result initializeNode() throws IOException, InterruptedException {
        if (doFullInitialization) {
            return this.node.initialize();
        } else {
            boolean status = ((JavaNode) this.node).initializeButSkipKernelBuild(false);
            return (status) ? Result.successful() : Result.unsuccessful(Assumptions.TESTING_ERROR_STATUS, "Failed partial initialization in test");
        }
    }

    private void initializeNodeWithChecks() throws IOException, InterruptedException {
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

    private void shutdownNodeIfRunning() throws InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            this.node.stop();
        }
    }
}
