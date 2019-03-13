package org.aion.harness.kernel;

import java.util.Arrays;
import main.SignedTransactionBuilder;
import org.aion.harness.result.TransactionResult;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.apache.commons.codec.binary.Hex;

/**
 * A signed Aion transaction.
 *
 * This transaction is "raw", indicating that it is a byte array representative of a transaction
 * (its encoding), rather than a typical object with fields associated with it.
 *
 * A raw transaction is used to send transactions over the RPC layer to the node.
 *
 * A transaction is immutable.
 */
public final class RawTransaction {
    private final byte[] signedTransaction;
    private byte[] hash;

    private RawTransaction(PrivateKey sender, BigInteger nonce, Address destination, byte[] data,
        long energyLimit, long energyPrice, BigInteger value, boolean isForAvm)
        throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {

        if (sender == null) {
            throw new NullPointerException("sender cannot be null");
        }

        SignedTransactionBuilder transactionBuilder = new SignedTransactionBuilder()
                .privateKey(sender.getPrivateKeyBytes())
                .senderNonce(nonce)
                .destination((destination == null) ? null : destination.getAddressBytes())
                .data(data)
                .energyLimit(energyLimit)
                .energyPrice(energyPrice)
                .value(value);

        if (isForAvm) {
            transactionBuilder.useAvmTransactionType();
        }

        this.signedTransaction = transactionBuilder.buildSignedTransaction();
    }

    /**
     * Constructs a new transaction that is sent from the address corresponding to the provided
     * private key.
     *
     * This transaction can be used to deploy Fvm contracts or call pre-compiled contracts.
     *
     * If deploying an Avm contract, use the {@code buildAndSignAvmTransaction()} method instead.
     *
     * @param senderPrivateKey Private key of the sender.
     * @param nonce Nonce of the sender.
     * @param destination Destination address.
     * @param data Transaction data.
     * @param energyLimit Maximum amount of energy to use.
     * @param energyPrice Price per unit of energy used.
     * @param value Amount of value to transfer to destination from sender.
     * @return The signed transaction.
     */
    public static TransactionResult buildAndSignTransaction(PrivateKey senderPrivateKey, BigInteger nonce,
        Address destination, byte[] data, long energyLimit, long energyPrice, BigInteger value) {

        try {
            return TransactionResult.successful(new RawTransaction(senderPrivateKey, nonce, destination, data, energyLimit, energyPrice, value, false));
        } catch (Exception e) {
            return TransactionResult.unsuccessful((e.getMessage() == null) ? e.toString() : e.getMessage());
        }
    }

    /**
     * Constructs a new transaction that is sent from the address corresponding to the provided
     * private key.
     *
     * This transaction can be used to deploy Avm contracts.
     *
     * If deploying an Fvm contract or calling a pre-compiled contract, use the
     * {@code buildAndSignTransaction()} method instead.
     *
     * @param senderPrivateKey Private key of the sender.
     * @param nonce Nonce of the sender.
     * @param destination Destination address.
     * @param data Transaction data.
     * @param energyLimit Maximum amount of energy to use.
     * @param energyPrice Price per unit of energy used.
     * @param value Amount of value to transfer to destination from sender.
     * @return The signed transaction.
     */
    public static TransactionResult buildAndSignAvmTransaction(PrivateKey senderPrivateKey, BigInteger nonce,
        Address destination, byte[] data, long energyLimit, long energyPrice, BigInteger value) {

        try {
            return TransactionResult.successful(new RawTransaction(senderPrivateKey, nonce, destination, data, energyLimit, energyPrice, value, true));
        } catch (Exception e) {
            return TransactionResult.unsuccessful((e.getMessage() == null) ? e.toString() : e.getMessage());
        }
    }

    /**
     * Returns the bytes of the signed transaction.
     *
     * @return The transaction bytes.
     */
    public byte[] getSignedTransactionBytes() {
        return Arrays.copyOf(this.signedTransaction, this.signedTransaction.length);
    }

    /**
     * Returns the hash of this transaction.
     *
     * @return The transaction hash.
     */
    public byte[] getTransactionHash() {
        if (this.hash == null) {
            this.hash = SignedTransactionBuilder.getTransactionHashOfSignedTransaction(this.signedTransaction);
        }
        return this.hash;
    }

    @Override
    public String toString() {
        return "Transaction { hash = " + Hex.encodeHexString(getTransactionHash()) + " }";
    }

}
