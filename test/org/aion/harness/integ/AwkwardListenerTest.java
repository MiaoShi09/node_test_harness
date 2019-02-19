package org.aion.harness.integ;

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
import org.aion.harness.result.TransactionResult;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

public class AwkwardListenerTest {

    @Test
    public void test() throws IOException, InterruptedException {
        Node node = NodeFactory.getNewNodeInstance(NodeFactory.NodeType.JAVA_NODE);

        ((JavaNode) node).initializeButSkipKernelBuild(false);
        node.start();

        NodeListener listener = new NodeListener();

        EventRequestResult result = listener.waitForMinersToStart(TimeUnit.MINUTES.toMillis(2));

        System.out.println(result);

        node.stop();
    }

    @Test
    public void testTx() throws Exception {
        Node node = NodeFactory.getNewNodeInstance(NodeFactory.NodeType.JAVA_NODE);

        String recipient = "a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30";
        Address preminedAddress = Address.createAddress(Hex.decodeHex(Assumptions.PREMINED_ADDRESS));
        PrivateKey preminedPrivateKey = PrivateKey.createPrivateKey(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));
        Address destination = Address.createAddress(Hex.decodeHex(recipient));

        TransactionResult transactionResult = constructTransaction(
                preminedPrivateKey,
                destination,
                BigInteger.ONE,
                BigInteger.ZERO);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        ((JavaNode) node).initializeButSkipKernelBuild(false);
        node.start();

        NodeListener listener = new NodeListener();

        RPC rpc = new RPC();
        rpc.sendTransaction(transactionResult.getTransaction());

        EventRequestResult result = listener.waitForTransactionToBeSealed(transactionResult.getTransaction().getTransactionHash(), TimeUnit.MINUTES.toMillis(2));
        System.out.println(result);

        node.stop();

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

        node.start();

        listener = new NodeListener();

        rpc = new RPC();
        rpc.sendTransaction(transactionResult.getTransaction());

        result = listener.waitForTransactionToBeSealed(transactionResult.getTransaction().getTransactionHash(), TimeUnit.MINUTES.toMillis(2));
        System.out.println(result);

        node.stop();
    }

    private TransactionResult constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) {
        return Transaction
            .buildAndSignTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

}
