package org.aion.harness;

import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.Result;
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

        Result result = listener.listenForMiningStarted();

        System.out.println(result);

        node.stop();
    }

    @Test
    public void testTx() throws Exception {
        Node node = NodeFactory.getNewNodeInstance(NodeFactory.NodeType.JAVA_NODE);

        String recipient = "a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30";

        TransactionResult transactionResult = constructTransaction(
                Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY),
                Hex.decodeHex(recipient),
                BigInteger.ONE,
                BigInteger.ZERO);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        ((JavaNode) node).initializeButSkipKernelBuild(false);
        node.start();

        NodeListener listener = new NodeListener();

        RPC rpc = new RPC(node);
        rpc.sendTransaction(transactionResult.getTransaction());

        Result result = listener.listenForTransaction(transactionResult.getTransaction().getTransactionHash());
        System.out.println(result);

        node.stop();

        Thread.sleep(15000);

        // ------------------------------------------------------------------------------

        transactionResult = constructTransaction(
                Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY),
                Hex.decodeHex(recipient),
                BigInteger.ONE,
                BigInteger.ONE);

        if (!transactionResult.getResultOnly().success) {
            System.err.println("CONSTRUCT TRANSACTION FAILED");
            return;
        }

        node.start();

        listener = new NodeListener();

        rpc = new RPC(node);
        rpc.sendTransaction(transactionResult.getTransaction());

        result = listener.listenForTransaction(transactionResult.getTransaction().getTransactionHash());
        System.out.println(result);

        node.stop();
    }

    private TransactionResult constructTransaction(byte[] privateKey, byte[] destination, BigInteger value, BigInteger nonce) throws Exception {
        return Transaction.buildAndSignTransaction(privateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value);
    }

}
