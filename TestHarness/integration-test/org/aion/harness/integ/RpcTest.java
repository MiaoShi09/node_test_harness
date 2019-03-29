package org.aion.harness.integ;

import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.Network;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.aion.harness.result.TransactionResult;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class RpcTest {
    private static Address destination;
    private static PrivateKey preminedPrivateKey;
    private static long energyLimit = 2_000_000;
    private static long energyPrice = 10_000_000_000L;

    private LocalNode node;
    private RPC rpc;

    @Before
    public void setup() throws IOException, InterruptedException, DecoderException, InvalidKeySpecException {
        destination = new Address(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));
        preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));

        this.node = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        this.rpc = new RPC("127.0.0.1", "8545");

        assertTrue(this.node.initialize().isSuccess());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.node = null;
        this.rpc = null;
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
    }

    @Test
    public void testSendValueViaRPC() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBalanceViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendValueWithIncorrectNonceViaRPC() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.valueOf(100));

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendNegativeValueViaRPC() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.valueOf(-1),
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendZeroValueViaRPC() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ZERO,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendMultipleValueTransfersViaRPC() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        assertTrue(rpcResult.isSuccess());

        transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ONE);

        assertTrue(transactionResult.isSuccess());
        rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendTransactionWhenNoNodeIsAlive() throws InterruptedException {
        assertFalse(this.node.isAlive());
        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ZERO,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertFalse(rpcResult.isSuccess());
    }

    @Test
    public void testSendValueToInvalidAddress() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendValueWithInsufficientBalance() throws Exception {
        byte[] badKey = Hex.decodeHex("223f19370d95582055bd8072cf3ffd635d2712a7171e4888091a060b9f4f63d5");
        PrivateKey badPrivateKey = PrivateKey.fromBytes(badKey);

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            badPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transactionResult.getTransaction());
        System.out.println("Rpc result = " + rpcResult);
        assertFalse(rpcResult.isSuccess());
        assertTrue(rpcResult.getError().contains("Transaction dropped"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testBalanceTransferAndCheck() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger transferValue = BigInteger.valueOf(50);

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(transferValue);

        // check balance after
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(transferValue, rpcResult.getResult());
    }

    @Test
    public void testBalanceTransferZeroBalanceAndCheck() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger transferValue = BigInteger.ZERO;

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(transferValue);

        // check balance after
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());
    }

    @Test
    public void testBalanceTransferNegativeBalanceAndCheck() throws Exception {
        // When someone tries to transfer a negative value(BigInteger), the positive representation of
        // that BigInteger will be the transfer value. This should be something handled by the server.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger negativeTransferValue = BigInteger.valueOf(-10);
        BigInteger positiveRepresentation = new BigInteger(1, negativeTransferValue.toByteArray());

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(negativeTransferValue);

        // check balance after - for positive representation
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(positiveRepresentation, rpcResult.getResult());
    }

    @Test
    public void testGetNonce() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        Address preminedAddress = preminedPrivateKey.getAddress();

        // check nonce before
        RpcResult<BigInteger> rpcResult = this.rpc.getNonce(preminedAddress);
        System.out.println("Rpc result = " + rpcResult);

        BigInteger nonceBefore = rpcResult.getResult();
        System.out.println("nonce is: " + nonceBefore);

        // do a transfer and wait
        doBalanceTransfer(BigInteger.ONE);

        // check nonce after
        rpcResult = this.rpc.getNonce(preminedAddress);
        System.out.println("Rpc result = " + rpcResult);

        BigInteger nonceAfter = rpcResult.getResult();
        System.out.println("nonce is: " + nonceAfter);

        assertEquals(nonceBefore.add(BigInteger.ONE), nonceAfter);
    }

    @Test
    public void testGetTransactionReceipt() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        NodeListener listener = NodeListener.listenTo(this.node);
        RawTransaction transaction = transactionResult.getTransaction();

        FutureResult<LogEventResult> futureResult = listener.listenForTransactionToBeProcessed(transaction, 2, TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + waitResult);
        assertTrue(waitResult.eventWasObserved());

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(rpcResult.getResult());
        System.out.println("Receipt result = " + receiptResult);
        assertTrue(receiptResult.isSuccess());

        // Assert the fields in the receipt are as expected.
        TransactionReceipt receipt = receiptResult.getResult();

        assertEquals(energyLimit, receipt.getTransactionEnergyLimit());
        assertEquals(energyPrice, receipt.getTransactionEnergyPrice());
        assertEquals(21_000L, receipt.getTransactionEnergyConsumed());
        assertEquals(21_000L, receipt.getCumulativeEnergyConsumed());
        assertEquals(0, receipt.getTransactionIndex());
        assertArrayEquals(transaction.getTransactionHash(), receipt.getTransactionHash());
        assertEquals(BigInteger.ONE, receipt.getBlockNumber());
        assertFalse(receipt.getAddressOfDeployedContract().isPresent());
        assertEquals(preminedPrivateKey.getAddress(), receipt.getTransactionSender());

        Optional<Address> transactionDestination = receipt.getTransactionDestination();
        assertTrue(transactionDestination.isPresent());
        assertEquals(destination, transactionDestination.get());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetTransactionReceiptFromContractDeploy() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            null,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        NodeListener listener = NodeListener.listenTo(this.node);
        RawTransaction transaction = transactionResult.getTransaction();

        FutureResult<LogEventResult> futureResult = listener.listenForTransactionToBeProcessed(transaction, 2, TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + waitResult);
        assertTrue(waitResult.eventWasObserved());

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(rpcResult.getResult());
        System.out.println("Receipt result = " + receiptResult);
        assertTrue(receiptResult.isSuccess());

        // Assert the fields in the receipt are as expected.
        TransactionReceipt receipt = receiptResult.getResult();

        assertEquals(energyLimit, receipt.getTransactionEnergyLimit());
        assertEquals(energyPrice, receipt.getTransactionEnergyPrice());
        assertEquals(0, receipt.getTransactionIndex());
        assertArrayEquals(transaction.getTransactionHash(), receipt.getTransactionHash());
        assertEquals(BigInteger.ONE, receipt.getBlockNumber());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        assertEquals(preminedPrivateKey.getAddress(), receipt.getTransactionSender());
        assertFalse(receipt.getTransactionDestination().isPresent());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetTransactionReceiptFromNonExistentReceiptHash() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        TransactionResult transactionResult = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        assertTrue(transactionResult.isSuccess());

        ReceiptHash receiptHash = new ReceiptHash(new byte[32]);

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(receiptHash);
        System.out.println("Receipt result = " + receiptResult);
        assertFalse(receiptResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    /**
     * This test does not assert anything about the syncing process because there's not really
     * anything meaningful to latch on to. It is here so that we can visually monitor this output.
     */
    @Test
    public void testSyncingToNetwork() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeForNetwork(Network.MAINNET);
        assertTrue(this.node.initialize().isSuccess());

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        Result syncResult = this.rpc.waitForSyncToComplete(40, TimeUnit.SECONDS);
        System.out.println("Sync result: " + syncResult);

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBlockByNumber() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<Block> blockResult = this.rpc.getBlockByNumber(BigInteger.ZERO);
        assertTrue(blockResult.isSuccess());
        assertNotNull(blockResult.getResult());

        assertTrue(this.node.stop().isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBlockByNumberWhenBlockDoesNotExist() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<Block> blockResult = this.rpc.getBlockByNumber(BigInteger.valueOf(2349865));
        assertFalse(blockResult.isSuccess());
        assertTrue(blockResult.getError().contains("No block exists"));

        assertTrue(this.node.stop().isSuccess());
        assertFalse(this.node.isAlive());
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

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return RawTransaction
            .buildAndSignFvmTransaction(senderPrivateKey, nonce, destination, new byte[0], energyLimit, energyPrice, value);
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("shutdownNodeIfRunning Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }
}
