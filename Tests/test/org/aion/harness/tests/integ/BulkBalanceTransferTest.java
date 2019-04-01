package org.aion.harness.tests.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.BulkRawTransactionBuilder;
import org.aion.harness.kernel.BulkRawTransactionBuilder.TransactionType;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.event.PrepackagedLogEvents;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.main.util.TestHarnessHelper;
import org.aion.harness.result.BulkResult;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.statistics.DurationStatistics;
import org.aion.harness.tests.contracts.avm.SimpleContract;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

/**
 * This test uses an avmtestnet build.
 */
public class BulkBalanceTransferTest {
    private static final String BUILT_KERNEL = System.getProperty("user.dir") + "/aion";
    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;
    private static final int NUMBER_OF_TRANSACTIONS = 25;

    private LocalNode node;
    private RPC rpc;
    private NodeListener listener;
    private PrivateKey preminedPrivateKey;

    @Before
    public void setup() throws IOException, InterruptedException, DecoderException, InvalidKeySpecException {
        disclaimer();
        this.preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));

        NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(Network.AVMTESTNET, BUILT_KERNEL, DatabaseOption.PRESERVE_DATABASE);

        this.node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        this.node.configure(configurations);
        assertTrue(this.node.initialize().isSuccess());
        assertTrue(this.node.start().isSuccess());
        assertTrue(this.node.isAlive());

        this.rpc = new RPC("127.0.0.1", "8545");
        this.listener = NodeListener.listenTo(this.node);
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        assertTrue(this.node.stop().isSuccess());
        assertFalse(this.node.isAlive());
        this.node = null;
        this.rpc = null;
        this.listener = null;

        // If we close and reopen the DB too quickly we get an error... this sleep tries to avoid
        // this issue so that the DB lock is released in time.
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
//        destroyLogs();
    }

    @Test
    public void testMixOfBalanceTransferTransactionsFromSameSender() throws InterruptedException, InvalidKeySpecException, DecoderException {
        List<Address> recipients = randomAddresses(NUMBER_OF_TRANSACTIONS);
        List<BigInteger> amounts = randomAmounts(NUMBER_OF_TRANSACTIONS);
        BigInteger initialNonce = getPreminedNonce();
        BigInteger originalBalance = getPreminedBalance();

        System.out.println("BulkBalanceTransferTest#testMixOfBalanceTransferTransactionsFromSameSender: originalBalance=" + originalBalance.toString());

        List<RawTransaction> transactions = buildRandomTransactionsFromSameSender(
            NUMBER_OF_TRANSACTIONS, recipients, amounts, initialNonce);
        List<TransactionReceipt> transferReceipts = sendTransactions(transactions);

        // Verify that the pre-mined account's balance is as expected.
        BigInteger totalEnergyCost = getTotalEnergyCost(transferReceipts);
        BigInteger totalAmounts = sum(amounts);
        BigInteger totalCost = totalEnergyCost.add(totalAmounts);
        BigInteger expectedBalance = originalBalance.subtract(totalCost);

        System.out.println(String.format(
            "BulkBalanceTransferTest#testMixOfBalanceTransferTransactionsFromSameSender: totalEnergyCost=%s, totalAmounts=%s, totalCost=%s, expectedBalance=%s",
            totalEnergyCost, totalAmounts, totalCost, expectedBalance));
        assertEquals(expectedBalance, getPreminedBalance());

        // Verify that all the beneficiaries have received the expected amounts.
        List<Address> allBeneficiaries = getAllBeneficiaries(transferReceipts);
        for (int i = 0; i < NUMBER_OF_TRANSACTIONS; i++) {
            assertEquals(amounts.get(i), getBalance(allBeneficiaries.get(i)));
        }
    }

    @Test
    public void testMixOfBalanceTransferTransactionsFromDifferentSenders() throws InterruptedException, InvalidKeySpecException, DecoderException {
        List<PrivateKey> senders = randomKeys(NUMBER_OF_TRANSACTIONS);
        List<Address> senderAddresses = TestHarnessHelper.extractAddresses(senders);
        List<Address> recipients = randomAddresses(NUMBER_OF_TRANSACTIONS);
        List<BigInteger> amounts = randomAmounts(NUMBER_OF_TRANSACTIONS);

        BigInteger originalBalance = getPreminedBalance();

        // Send balance to all of the other sender accounts.
        BigInteger amount = BigInteger.TEN.pow(20);

        List<RawTransaction> transactions = buildBalanceTransferTransactions(senderAddresses, amount);
        List<TransactionReceipt> transferReceipts = sendTransactions(transactions);

        // Verify that the pre-mined account's balance is as expected.
        BigInteger totalEnergyCost = getTotalEnergyCost(transferReceipts);
        BigInteger totalTransactionCost = totalEnergyCost.add(amount.multiply(BigInteger.valueOf(
            NUMBER_OF_TRANSACTIONS)));
        BigInteger expectedBalance = originalBalance.subtract(totalTransactionCost);

        assertEquals(expectedBalance, getPreminedBalance());

        // Verify that each of the sender accounts have the expected balance.
        for (Address sender : senderAddresses) {
            assertEquals(amount, getBalance(sender));
        }

        // Now we can send out our balance transfer transactions from our new sender accounts.
        transactions = buildRandomTransactionsFromMultipleSenders(NUMBER_OF_TRANSACTIONS, senders, recipients, amounts);
        transferReceipts = sendTransactions(transactions);

        // Verify that each of the sender accounts have the expected balances.
        verifySenderBalances(senderAddresses, amount, transferReceipts, amounts);

        // Verify that each of the beneficiaries has the expected balance.
        List<Address> allBeneficiaries = getAllBeneficiaries(transferReceipts);
        for (int i = 0; i < NUMBER_OF_TRANSACTIONS; i++) {
            assertEquals(amounts.get(i), getBalance(allBeneficiaries.get(i)));
        }
    }

    private void verifySenderBalances(List<Address> senders, BigInteger senderInitialBalance, List<TransactionReceipt> receipts, List<BigInteger> amounts) throws InterruptedException {
        assertEquals(senders.size(), receipts.size());
        assertEquals(senders.size(), amounts.size());

        int size = senders.size();
        for (int i = 0; i < size; i++) {
            BigInteger energyCost = BigInteger.valueOf(receipts.get(i).getTransactionEnergyConsumed()).multiply(BigInteger.valueOf(ENERGY_PRICE));
            BigInteger transactionCost = energyCost.add(amounts.get(i));
            BigInteger expectedBalance = senderInitialBalance.subtract(transactionCost);

            assertEquals(expectedBalance, getBalance(senders.get(i)));
        }
    }

    private enum TransactionKind {
        REGULAR_TRANSFER(0),
        CREATE_AND_TRANSFER_AVM(1),
        CREATE_AND_TRANSFER_FVM(2);

        private static Map<Integer, TransactionKind> valueToEnum = new HashMap<>();
        private int value;

        static {
            valueToEnum.put(REGULAR_TRANSFER.value, REGULAR_TRANSFER);
            valueToEnum.put(CREATE_AND_TRANSFER_AVM.value, CREATE_AND_TRANSFER_AVM);
            valueToEnum.put(CREATE_AND_TRANSFER_FVM.value, CREATE_AND_TRANSFER_FVM);
        }

        TransactionKind(int value) {
            this.value = value;
        }

        public int toInt() {
            return this.value;
        }

        public static TransactionKind fromInt(int value) {
            return valueToEnum.get(value);
        }
    }

    private List<TransactionReceipt> sendTransactions(List<RawTransaction> transactions) throws InterruptedException {
        // we want to ensure that the transactions get sealed into blocks.
        List<IEvent> transactionIsSealedEvents = getTransactionSealedEvents(transactions);
        List<FutureResult<LogEventResult>> futures = this.listener.listenForEvents(transactionIsSealedEvents, 30, TimeUnit.MINUTES);

        // Send the transactions off.
        System.out.println("Sending the " + transactions.size() + " transactions...");
        List<RpcResult<ReceiptHash>> sendResults = this.rpc.sendTransactions(transactions);
        BulkResult<ReceiptHash> bulkHashes = TestHarnessHelper.extractRpcResults(sendResults);
        assertTrue(bulkHashes.isSuccess());

        // Wait on the futures to complete and ensure we saw the transactions get sealed.
        System.out.println("Waiting for the transactions to process...");
        TestHarnessHelper.waitOnFutures(futures);
        BulkResult<LogEventResult> bulkEventResults = TestHarnessHelper.extractFutureResults(futures);
        assertTrue(bulkEventResults.isSuccess());
        System.out.println("All transactions were sealed into blocks.");

        List<ReceiptHash> hashes = bulkHashes.getResults();

        System.out.println("Getting the transaction receipts...");
        List<RpcResult<TransactionReceipt>> receiptResults = this.rpc.getTransactionReceipts(hashes);
        System.out.println("Got all transaction receipts.");
        BulkResult<TransactionReceipt> bulkReceipts = TestHarnessHelper.extractRpcResults(receiptResults);
        assertTrue(bulkReceipts.isSuccess());

        System.out.println("Some statistics...");
        DurationStatistics.from(sendResults, bulkEventResults.getResults()).printStatistics(10);
        return bulkReceipts.getResults();
    }

    private List<IEvent> getTransactionSealedEvents(List<RawTransaction> transactions) {
        List<IEvent> events = new ArrayList<>();
        for (RawTransaction transaction : transactions) {
            events.add(PrepackagedLogEvents.getTransactionSealedEvent(transaction));
        }
        return events;
    }

    private BigInteger getPreminedNonce() throws InterruptedException {
        return getNonce(this.preminedPrivateKey.getAddress());
    }

    private BigInteger getPreminedBalance() throws InterruptedException {
        return getBalance(this.preminedPrivateKey.getAddress());
    }

    private BigInteger getNonce(Address address) throws InterruptedException {
        RpcResult<BigInteger> nonceResult = this.rpc.getNonce(address);
        assertTrue(nonceResult.isSuccess());
        return nonceResult.getResult();
    }

    private BigInteger getBalance(Address address) throws InterruptedException {
        RpcResult<BigInteger> balanceResult = this.rpc.getBalance(address);
        assertTrue(balanceResult.isSuccess());
        return balanceResult.getResult();
    }

    private List<BigInteger> randomAmounts(int num) {
        Random random = new Random(21);

        List<BigInteger> amounts = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            int amount = random.nextInt();
            amounts.add(BigInteger.valueOf((amount < 0) ? 76_543 : amount));
        }
        return amounts;
    }

    private List<PrivateKey> randomKeys(int num) throws InvalidKeySpecException {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            keys.add(PrivateKey.random());
        }
        return keys;
    }

    private List<Address> randomAddresses(int num) throws InvalidKeySpecException {
        List<Address> addresses = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            addresses.add(PrivateKey.random().getAddress());
        }
        return addresses;
    }

    private List<Address> getAllBeneficiaries(List<TransactionReceipt> receipts) {
        List<Address> addresses = new ArrayList<>();
        for (TransactionReceipt receipt : receipts) {
            if (receipt.getAddressOfDeployedContract().isPresent()) {
                // This is a CREATE
                assertFalse(receipt.getTransactionDestination().isPresent());
                addresses.add(receipt.getAddressOfDeployedContract().get());
            } else {
                // This is a CALL
                assertTrue(receipt.getTransactionDestination().isPresent());
                addresses.add(receipt.getTransactionDestination().get());
            }
        }
        return addresses;
    }

    private BigInteger sum(List<BigInteger> numbers) {
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger number : numbers) {
            sum = sum.add(number);
        }
        return sum;
    }

    private BigInteger getTotalEnergyCost(List<TransactionReceipt> receipts) {
        BigInteger cost = BigInteger.ZERO;
        for (TransactionReceipt receipt : receipts) {
            BigInteger energyConsumed = BigInteger.valueOf(receipt.getTransactionEnergyConsumed());
            BigInteger energyPrice = BigInteger.valueOf(receipt.getTransactionEnergyPrice());
            BigInteger energyCost = energyConsumed.multiply(energyPrice);
            cost = cost.add(energyCost);
        }
        return cost;
    }

    private List<RawTransaction> buildRandomTransactionsFromMultipleSenders(int numberOfTransactions, List<PrivateKey> senders, List<Address> recipients, List<BigInteger> amounts) throws DecoderException {
        assertEquals(numberOfTransactions, senders.size());
        assertEquals(numberOfTransactions, amounts.size());

        Random random = new Random(41);

        List<RawTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numberOfTransactions; i++) {
            transactions.add(buildTransaction(TransactionKind.fromInt(random.nextInt(3)), senders.get(i), recipients.get(i), amounts.get(i), BigInteger.ZERO));
        }
        return transactions;
    }

    private List<RawTransaction> buildRandomTransactionsFromSameSender(int numberOfTransactions, List<Address> recipients, List<BigInteger> amounts, BigInteger initialNonce) throws DecoderException {
        assertEquals(numberOfTransactions, amounts.size());
        assertEquals(numberOfTransactions, recipients.size());

        Random random = new Random(17);
        BigInteger nonce = initialNonce;

        List<RawTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numberOfTransactions; i++) {
            transactions.add(buildTransaction(TransactionKind.fromInt(random.nextInt(3)), this.preminedPrivateKey, recipients.get(i), amounts.get(i), nonce));
            nonce = nonce.add(BigInteger.ONE);
        }
        return transactions;
    }

    private List<RawTransaction> buildBalanceTransferTransactions(List<Address> recipients, BigInteger amount) throws InterruptedException {
        BulkResult<RawTransaction> buildResults = new BulkRawTransactionBuilder(recipients.size())
            .useSameSender(this.preminedPrivateKey, getPreminedNonce())
            .useMultipleDestinations(recipients)
            .useSameTransactionData(new byte[0])
            .useSameTransferValue(amount)
            .useSameEnergyLimit(ENERGY_LIMIT)
            .useSameEnergyPrice(ENERGY_PRICE)
            .useSameTransactionType(TransactionType.FVM)
            .build();

        assertTrue(buildResults.isSuccess());
        return buildResults.getResults();
    }

    private RawTransaction buildTransaction(TransactionKind kind, PrivateKey sender, Address recipient, BigInteger amount, BigInteger nonce) throws DecoderException {
        System.out.println(
            String.format(
                "BulkBalanceTransferTest#buildTransaction: kind=%s, sender=%s, recipient=%s, amount=%s, nonce=%s",
                kind.toString(),
                sender.toString(),
                recipient.toString(),
                amount.toString(),
                nonce.toString()
        ));
        switch (kind) {
            case REGULAR_TRANSFER: return buildTransactionToTransferToRegularAccount(sender, recipient, amount, nonce);
            case CREATE_AND_TRANSFER_FVM: return buildTransactionToCreateAndTransferToFvmContract(sender, amount, nonce);
            case CREATE_AND_TRANSFER_AVM: return buildTransactionToCreateAndTransferToAvmContract(sender, amount, nonce);
            default: fail("Unknown transaction kind: " + kind);
        }
        return null;
    }

    private RawTransaction buildTransactionToTransferToRegularAccount(PrivateKey sender, Address account, BigInteger amount, BigInteger nonce) {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            sender,
            nonce,
            account,
            new byte[0],
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToCreateAndTransferToFvmContract(PrivateKey sender, BigInteger amount, BigInteger nonce) throws DecoderException {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            sender,
            nonce,
            null,
            getFvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToCreateAndTransferToAvmContract(PrivateKey sender, BigInteger amount, BigInteger nonce) {
        TransactionResult result = RawTransaction.buildAndSignAvmTransaction(
            sender,
            nonce,
            null,
            getAvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    /**
     * Returns the bytes of an FVM contract named 'PayableConstructor'.
     *
     * See src/org/aion/harness/tests/contracts/fvm/PayableConstructor.sol for the contract itself.
     *
     * We use the bytes directly here, just because we don't have any good utilities in place for
     * generating these bytes ourselves.
     */
    private byte[] getFvmContractBytes() throws DecoderException {
        return Hex.decodeHex("60506040525b5b600a565b6088806100186000396000f300605060405260"
            + "00356c01000000000000000000000000900463ffffffff1680634a6a740714603b578063fc9ad4331"
            + "46043576035565b60006000fd5b60416056565b005b3415604e5760006000fd5b60546059565b005b"
            + "5b565b5b5600a165627a7a723058206bd6d88e9834838232f339ec7235f108a21441649a2cf876547"
            + "229e6c18c098c0029");
    }

    private byte[] getAvmContractBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(SimpleContract.class), new byte[0]).encodeToBytes();
    }

    private static void destroyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(System.getProperty("user.dir") + "/logs"));
    }

    private static void disclaimer() {
        System.err.println("------------------------------------------------------------------------");
        System.err.println("This is a disclaimer to exonerate me of all charges ");
        System.err.println();
        System.err.println("Unpack the avmtestnet build so that its root (aion) directory is located at: " + BUILT_KERNEL);
        System.err.println("Run in standalone mode (this way you don't need the eth_syncing fix)");
        System.err.println("Set the TX log level to TRACE (be careful of the double config file nonsense..)");
        System.err.println("------------------------------------------------------------------------");
    }

}
