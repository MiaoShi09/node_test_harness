package org.aion.harness.tests.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.harness.kernel.Address;
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
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.event.PrepackagedLogEvents;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.tests.contracts.avm.SimpleContract;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This test uses an avmtestnet build.
 */
@Ignore
public class BalanceTransferTest {
    private static final String BUILT_KERNEL = System.getProperty("user.dir") + "/aion";
    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

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
        Result result = this.node.initialize();
        System.out.println(result);
        assertTrue(result.isSuccess());
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
        destroyLogs();
    }

    /**
     * Tests making a CREATE transaction in which funds are transferred as well.
     */
    @Test
    public void testTransferBalanceToFvmContractUponCreation() throws InterruptedException, DecoderException {
        BigInteger originalBalance = getPreminedBalance();
        BigInteger amount = BigInteger.TEN.pow(13).add(BigInteger.valueOf(2_938_652));

        // Build and send the transaction off, grab its receipt.
        RawTransaction transaction = buildTransactionToCreateAndTransferToFvmContract(amount);
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(createReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction followed by a CALL transaction to transfer funds to a
     * non-payable function (which does not work) followed by a CALL transaction to transfer funds
     * to a payable function (which does work).
     */
    @Test
    public void testTransferBalanceToFvmContractAfterCreation() throws InterruptedException, DecoderException {
        BigInteger amount = BigInteger.TEN.pow(11).add(BigInteger.valueOf(237_645));

        // Build and send the transaction off, grab its receipt.
        System.out.println("Creating the fvm contract...");
        RawTransaction transaction = buildTransactionToCreateFvmContract();
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        // Get the contract address as well as it's & the pre-mined account's balance.
        Address contract = createReceipt.getAddressOfDeployedContract().get();
        BigInteger originalBalance = getPreminedBalance();

        // Attempt to transfer funds to a non-payable function in the contract.
        System.out.println("Attempting to transfer funds to the contract...");
        transaction = buildTransactionToTransferFundsToNonPayableFunction(contract, amount);
        TransactionReceipt transferReceipt = sendTransaction(transaction);

        // The function is not payable so we expect to not have transferred any balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger expectedBalance = originalBalance.subtract(energyCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(BigInteger.ZERO, getBalance(contract));

        // Now attempt to transfer funds to a payable function in the contract.
        System.out.println("Using correct (payable) method to transfer funds...");
        transaction = buildTransactionToTransferFundsToPayableFunction(contract, amount);
        transferReceipt = sendTransaction(transaction);

        // The function is payable so now we expect to have transferred the balance.
        energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        expectedBalance = expectedBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction in which funds are transferred as well.
     */
    @Test
    public void testTransferBalanceToAvmContractUponCreation() throws InterruptedException {
        BigInteger originalBalance = getPreminedBalance();
        BigInteger amount = BigInteger.TEN.pow(17).add(BigInteger.valueOf(298_365_712));

        System.out.println("Creating the avm contract...");
        RawTransaction transaction = buildTransactionToCreateAndTransferToAvmContract(amount);
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(createReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests making a CREATE transaction followed by a CALL transaction to transfer funds to an
     * avm contract.
     */
    @Test
    public void testTransferBalanceToAvmContractAfterCreation() throws InterruptedException {
        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(2_384_956));

        // Create the contract.
        System.out.println("Creating the avm contract...");
        RawTransaction transaction = buildTransactionToCreateAvmContract();
        TransactionReceipt createReceipt = sendTransaction(transaction);
        assertTrue(createReceipt.getAddressOfDeployedContract().isPresent());

        Address contract = createReceipt.getAddressOfDeployedContract().get();

        // Now transfer funds to the contract.
        System.out.println("Transferring funds to the contract...");
        BigInteger originalBalance = getPreminedBalance();
        transaction = buildTransactionToTransferFundsToAvmContract(contract, amount);
        TransactionReceipt transferReceipt = sendCallToAvmContract(transaction);

        // Compute our expected balance and verify it matches the sender's current balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());
        assertEquals(amount, getBalance(contract));
    }

    /**
     * Tests transferring funds to a newly created random address. This is a regular account that
     * has no code (ie. it is not a contract address).
     */
    @Test
    public void testTransferBalanceToRegularAccount() throws InterruptedException, InvalidKeySpecException {
        BigInteger amount = BigInteger.TEN.pow(16).add(BigInteger.valueOf(12_476));
        BigInteger originalBalance = getPreminedBalance();

        // Create the new account.
        Address regularAccount = PrivateKey.random().getAddress();

        // Transfer the funds.
        System.out.println("Transferring funds to the account...");
        RawTransaction transaction = buildTransactionToTransferFundsToAccount(regularAccount, amount);
        TransactionReceipt transferReceipt = sendTransaction(transaction);

        // Verify that the pre-mined account traferred the balance.
        BigInteger energyCost = getEnergyCost(transferReceipt);
        BigInteger transactionCost = energyCost.add(amount);
        BigInteger expectedBalance = originalBalance.subtract(transactionCost);

        assertEquals(expectedBalance, getPreminedBalance());

        // Verify that the regular account received the balance.
        assertEquals(amount, getBalance(regularAccount));
    }

    private BigInteger getEnergyCost(TransactionReceipt receipt) {
        return BigInteger.valueOf(receipt.getTransactionEnergyConsumed()).multiply(BigInteger.valueOf(ENERGY_PRICE));
    }

    private BigInteger getPreminedBalance() throws InterruptedException {
        RpcResult<BigInteger> balanceResult = this.rpc.getBalance(this.preminedPrivateKey.getAddress());
        assertTrue(balanceResult.isSuccess());
        return balanceResult.getResult();
    }

    private BigInteger getBalance(Address address) throws InterruptedException {
        RpcResult<BigInteger> balanceResult = this.rpc.getBalance(address);
        assertTrue(balanceResult.isSuccess());
        return balanceResult.getResult();
    }

    private TransactionReceipt sendCallToAvmContract(RawTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = PrepackagedLogEvents.getTransactionSealedEvent(transaction);
        IEvent contractPrintln = new Event("I'm a pretty dull contract.");
        IEvent event = Event.and(transactionIsSealed, contractPrintln);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(event, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the avm call transaction...");
        RpcResult<ReceiptHash> sendResult = this.rpc.sendTransaction(transaction);
        assertTrue(sendResult.isSuccess());

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the avm call transaction to process...");
        LogEventResult listenResult = future.get();
        assertTrue(listenResult.eventWasObserved());
        System.out.println("Transaction was sealed into a block & we observed the println statement.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(hash);
        assertTrue(receiptResult.isSuccess());
        return receiptResult.getResult();
    }

    private TransactionReceipt sendTransaction(RawTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = PrepackagedLogEvents.getTransactionSealedEvent(transaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = this.rpc.sendTransaction(transaction);
        assertTrue(sendResult.isSuccess());

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get();
        assertTrue(listenResult.eventWasObserved());
        System.out.println("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(hash);
        assertTrue(receiptResult.isSuccess());
        return receiptResult.getResult();
    }

    private RawTransaction buildTransactionToTransferFundsToAvmContract(Address contract, BigInteger amount) throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignAvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
            contract,
            new byte[]{ 0x1, 0x2, 0x3 }, // we give a non-empty array here so that we actually invoke the main method.
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToTransferFundsToAccount(Address account, BigInteger amount) throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
            account,
            new byte[0],
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToTransferFundsToPayableFunction(Address contract, BigInteger amount) throws DecoderException, InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
            contract,
            getPayableFunctionCallEncoding(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToTransferFundsToNonPayableFunction(Address contract, BigInteger amount) throws DecoderException, InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
            contract,
            getNonPayableFunctionCallEncoding(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToCreateFvmContract() throws DecoderException, InterruptedException {
        return buildTransactionToCreateAndTransferToFvmContract(BigInteger.ZERO);
    }

    private RawTransaction buildTransactionToCreateAndTransferToFvmContract(BigInteger amount) throws DecoderException, InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignFvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
            null,
            getFvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            amount);

        assertTrue(result.isSuccess());
        return result.getTransaction();
    }

    private RawTransaction buildTransactionToCreateAvmContract() throws InterruptedException {
        return buildTransactionToCreateAndTransferToAvmContract(BigInteger.ZERO);
    }

    private RawTransaction buildTransactionToCreateAndTransferToAvmContract(BigInteger amount) throws InterruptedException {
        TransactionResult result = RawTransaction.buildAndSignAvmTransaction(
            this.preminedPrivateKey,
            getNonce(),
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

    private byte[] getNonPayableFunctionCallEncoding() throws DecoderException {
        return Hex.decodeHex("fc9ad433");   // calls the function named 'nonpayableFunction'
    }

    private byte[] getPayableFunctionCallEncoding() throws DecoderException {
        return Hex.decodeHex("4a6a7407");   // calls the function named 'payableFunction'
    }

    private static void destroyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(System.getProperty("user.dir") + "/logs"));
    }

    private BigInteger getNonce() throws InterruptedException {
        RpcResult<BigInteger> nonceResult = this.rpc.getNonce(this.preminedPrivateKey.getAddress());
        assertTrue(nonceResult.isSuccess());
        return nonceResult.getResult();
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
