package org.aion.harness.main.types;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import org.aion.harness.kernel.Address;
import org.aion.harness.main.types.internal.TransactionReceiptBuilder;
import org.apache.commons.codec.binary.Hex;

/**
 * A transaction receipt holds pertinent information related to a transaction that has been sealed
 * into a block in the blockchain.
 *
 * The preferred way of constructing instances of this class is {@link TransactionReceiptBuilder}.
 *
 * A transaction receipt is immutable.
 */
public final class TransactionReceipt {
    private final long energyPrice;
    private final long energyLimit;
    private final long energyConsumed;
    private final long cumulativeEnergyConsumed;
    private final int transactionIndex;
    private final byte[] blockHash;
    private final byte[] bloomFilter;
    private final byte[] transactionHash;
    private final byte[] stateRootHash;
    private final BigInteger blockNumber;
    private final Address deployedContractAddress;
    private final Address sender;
    private final Address destination;
    private final int status;

    /**
     * Constructs a new transaction receipt from the specified parameters.
     */
    public TransactionReceipt(long energyPrice, long energyLimit, long energyConsumed, long cumulativeEnergyConsumed,
        int transactionIndex, byte[] blockHash, byte[] bloomFilter, byte[] transactionHash, byte[] stateRootHash,
        BigInteger blockNumber, Address newContractAddress, Address sender, Address destination, int status) {

        this.energyPrice = energyPrice;
        this.energyLimit = energyLimit;
        this.energyConsumed = energyConsumed;
        this.cumulativeEnergyConsumed = cumulativeEnergyConsumed;
        this.transactionIndex = transactionIndex;
        this.blockHash = Arrays.copyOf(blockHash, blockHash.length);
        this.bloomFilter = Arrays.copyOf(bloomFilter, bloomFilter.length);
        this.transactionHash = Arrays.copyOf(transactionHash, transactionHash.length);
        this.stateRootHash = Arrays.copyOf(stateRootHash, stateRootHash.length);
        this.blockNumber = blockNumber;
        this.deployedContractAddress = newContractAddress;
        this.sender = sender;
        this.destination = destination;
        this.status = status;
    }

    /**
     * Returns the energy price of the transaction. This is the amount per unit of energy that the
     * sender paid per energy consumed.
     *
     * @return the transaction energy price.
     */
    public long getTransactionEnergyPrice() {
        return this.energyPrice;
    }

    /**
     * Returns the energy limit of the transaction. This is the maximum amount of energy the sender
     * was willing to use.
     *
     * @return the transaction energy limit.
     */
    public long getTransactionEnergyLimit() {
        return this.energyLimit;
    }

    /**
     * Returns the amount of energy used by the transaction.
     *
     * @return the transaction energy price.
     */
    public long getTransactionEnergyConsumed() {
        return this.energyConsumed;
    }

    /**
     * Returns the cumulative amount of energy used by all transactions in the block that this
     * transaction belongs to.
     *
     * @return the total amount of energy used.
     */
    public long getCumulativeEnergyConsumed() {
        return this.cumulativeEnergyConsumed;
    }

    /**
     * Returns the index in the block in which this transaction sits. A transaction at index i was
     * the i'th transaction to be executed in the block.
     *
     * @return the transaction block index.
     */
    public int getTransactionIndex() {
        return this.transactionIndex;
    }

    /**
     * Returns the block hash of the block this transaction is sealed in.
     *
     * @return the block hash.
     */
    public byte[] getBlockHash() {
        return Arrays.copyOf(this.blockHash, this.blockHash.length);
    }

    /**
     * Returns the bloom filter corresponding to this transaction.
     *
     * @return the log bloom filter.
     */
    public byte[] getBloomFilter() {
        return Arrays.copyOf(this.bloomFilter, this.bloomFilter.length);
    }

    /**
     * Returns the transaction hash of the transaction that this receipt is describing.
     *
     * @return the transaction hash.
     */
    public byte[] getTransactionHash() {
        return Arrays.copyOf(this.transactionHash, this.transactionHash.length);
    }

    /**
     * Returns the state root hash at the time directly after this transaction was executed.
     *
     * @return the state root hash.
     */
    public byte[] getStateRootHash() {
        return Arrays.copyOf(this.stateRootHash, this.stateRootHash.length);
    }

    /**
     * Returns the block number of the block that this transaction was sealed in.
     *
     * @return the block number.
     */
    public BigInteger getBlockNumber() {
        return this.blockNumber;
    }

    /**
     * Returns an empty optional if the transaction was not a contract creation transaction.
     *
     * Otherwise, if the transaction created a new contract, this method returns the address of that
     * new contract.
     *
     * @return the new contract address, if a new contract was created.
     */
    public Optional<Address> getAddressOfDeployedContract() {
        return (this.deployedContractAddress == null) ? Optional.empty() : Optional.of(this.deployedContractAddress);
    }

    /**
     * Returns the address of the account that sent this transaction.
     *
     * @return the sender address.
     */
    public Address getTransactionSender() {
        return this.sender;
    }

    /**
     * Returns an empty optional if no destination was specified (indicating the transaction is a
     * contract creation transaction (see {@code getAddressOfDeployedContract()}).
     *
     * Otherwise, if the transaction had a specific destination address it was intended for, this
     * method returns it.
     *
     * @return the destination address, if one was specified.
     */
    public Optional<Address> getTransactionDestination() {
        return (this.destination == null) ? Optional.empty() : Optional.of(this.destination);
    }

    @Override
    public String toString() {
        return "TransactionReceipt { energy limit = " + this.energyLimit
            + ", energy price = " + this.energyPrice
            + ", energy consumed = " + this.energyConsumed
            + ", total energy consumed = " + this.cumulativeEnergyConsumed
            + ", transaction index = " + this.transactionIndex
            + ", block hash = 0x" + Hex.encodeHexString(this.blockHash)
            + ", bloom filter = 0x" + Hex.encodeHexString(this.bloomFilter)
            + ", transaction hash = 0x" + Hex.encodeHexString(this.transactionHash)
            + ", state root hash = 0x" + Hex.encodeHexString(this.stateRootHash)
            + ", block number = " + this.blockNumber
            + ", deployed contract address = " + this.deployedContractAddress
            + ", transaction sender = " + this.sender
            + ", transaction destination = " + this.destination
            + ", status = " + this.status
            + " }";
    }

    /**
     * Returns {@code true} if, and only if, other is a transaction receipt and the transaction hash
     * of other is equal to this receipt's transaction hash, and the block hash of other is equal to
     * this receipt's block hash, and both receipts have the same transaction index.
     *
     * These three values are sufficient enough for equality, and should imply that all other fields
     * must be equal as well. If this does not hold it is a kernel error.
     *
     * @param other The other object whose equality is to be tested.
     * @return true if other is a transaction receipt with the same transaction index, hash and block hash.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TransactionReceipt)) {
            return false;
        }

        TransactionReceipt otherReceipt = (TransactionReceipt) other;

        if (!Arrays.equals(this.transactionHash, otherReceipt.transactionHash)) {
            return false;
        }
        if (!Arrays.equals(this.blockHash, otherReceipt.blockHash)) {
            return false;
        }
        return this.transactionIndex == otherReceipt.transactionIndex;
    }

    @Override
    public int hashCode() {
        int hash = 37;
        hash += Arrays.hashCode(this.transactionHash);
        hash += Arrays.hashCode(this.blockHash);
        hash += this.transactionIndex;
        return hash;
    }

}
