package org.aion.harness.integ;

import java.io.File;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodePreserveDatabaseTest {
    private static PrivateKey preminedPrivateKey;
    private static Address destination;

    private LocalNode node;
    private RPC rpc;

    @Before
    public void setup() throws DecoderException, InvalidKeySpecException {
        destination = new Address(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));
        preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));
        this.rpc = new RPC("127.0.0.1", "8545");
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.node = null;
    }

    @BeforeClass
    public static void setupBeforeAllTests() throws IOException {
        destroyDatabase();
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
        destroyDatabase();
    }

    @Test
    public void testPreserveDatabaseCheckTransaction() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeToPreserveDatabase();

        // initialize and run node to generate a database, then shutdown the node
        assertTrue(this.node.initialize().isSuccess());

        // start the node
        assertTrue(this.node.start().isSuccess());

        // transfer and wait
        BigInteger transferValue = BigInteger.valueOf(50);
        doBalanceTransfer(transferValue);

        // check balance of destination address
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        BigInteger balanceAfter = rpcResult.getResult();
        assertEquals(transferValue, balanceAfter);

        // now stop the node
        Result result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());

        // Verify that the database exists. Also wait some time so we no longer have the db lock..
        assertTrue(TestHelper.getDefaultDatabaseLocation().exists());
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        // start the node
        result = this.node.start();
        assertTrue(result.isSuccess());

        // check that the transfer is still present
        rpcResult = this.rpc.getBalance(destination);
        BigInteger balanceAfter2 = rpcResult.getResult();
        assertEquals(transferValue, balanceAfter2);

        // check that the balance has not changed
        assertEquals(balanceAfter, balanceAfter2);

        // now stop the node
        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testPreserveNonExistentDatabase() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeToPreserveDatabase();

        File database = TestHelper.getDefaultDatabaseLocation();
        if (database.exists()) {
            FileUtils.deleteDirectory(database);
        }

        // Initialization should still succeed.
        assertTrue(this.node.initialize().isSuccess());
        assertFalse(database.exists());
    }

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return RawTransaction
            .buildAndSignFvmTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }

    private void doBalanceTransfer(BigInteger transferValue) throws InterruptedException {
        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            transferValue,
            BigInteger.ZERO);

        RawTransaction transaction = transactionResult.getTransaction();

        FutureResult<LogEventResult> futureResult = NodeListener.listenTo(this.node).listenForTransactionToBeProcessed(
            transaction,
            1,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> result = this.rpc.sendTransaction(transaction);
        System.out.println("Rpc result = " + result);
        assertTrue(result.isSuccess());

        LogEventResult eventResult = futureResult.get();
        assertTrue(eventResult.eventWasObserved());
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private static void destroyDatabase() throws IOException {
        if (TestHelper.getDefaultDatabaseLocation().exists()) {
            FileUtils.deleteDirectory(TestHelper.getDefaultDatabaseLocation());
        }
    }

}
