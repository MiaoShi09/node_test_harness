package org.aion.harness.main.types.internal;

import java.math.BigInteger;
import org.aion.harness.kernel.Address;
import org.aion.harness.main.tools.JsonStringParser;
import org.aion.harness.main.types.TransactionReceipt;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * A builder class used to construct instances of {@link TransactionReceipt}.
 *
 * All initial values of the fields that will be used to construct the receipt are set to invalid
 * values so that the receipt will throw an exception if a key field was missing.
 *
 * If subsequent calls to a method are made before a call to {@code build()} then the latest value
 * will take precedence.
 *
 * All methods must be called before {@code build()} except for:
 *   {@code newlyDeployedContractAddress()}
 *   {@code transactionDestination()}
 *
 * The builder can be reused.
 *
 * To reset the builder back to its initial state use {@code clear()}.
 *
 * This class also provides a means of constructing a receipt from a Json String.
 *
 * This class is not thread-safe.
 */
public final class TransactionReceiptBuilder {
    private long energyPrice = -1;
    private long energyLimit = -1;
    private long energyConsumed = -1;
    private long cumulativeEnergyConsumed = -1;
    private int transactionIndex = -1;
    private byte[] blockHash = null;
    private byte[] bloomFilter = null;
    private byte[] transactionHash = null;
    private byte[] stateRootHash = null;
    private BigInteger blockNumber = null;
    private Address deployedContractAddress = null;
    private Address sender = null;
    private Address destination = null;
    private int status = -1;

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the energy price of the transaction.
     *
     * @param energyPrice The energy price.
     * @return this builder.
     */
    public TransactionReceiptBuilder transactionEnergyPrice(long energyPrice) {
        this.energyPrice = energyPrice;
        return this;
    }

    public TransactionReceiptBuilder status(int status) {
        this.status = status;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the energy limit of the transaction.
     *
     * @param energyLimit The energy limit.
     * @return this builder.
     */
    public TransactionReceiptBuilder transactionEnergyLimit(long energyLimit) {
        this.energyLimit = energyLimit;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the amount of energy consumed of the transaction.
     *
     * @param energyConsumed The energy consumed.
     * @return this builder.
     */
    public TransactionReceiptBuilder energyConsumedByTransaction(long energyConsumed) {
        this.energyConsumed = energyConsumed;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the amount of energy consumed by the block this transaction belongs to.
     *
     * @param cumulativeEnergyConsumed The total amount of energy consumed.
     * @return this builder.
     */
    public TransactionReceiptBuilder totalEnergyConsumedByBlock(long cumulativeEnergyConsumed) {
        this.cumulativeEnergyConsumed = cumulativeEnergyConsumed;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the transaction index.
     *
     * @param transactionIndex The transaction index.
     * @return this builder.
     */
    public TransactionReceiptBuilder indexOfTransactionInBlock(int transactionIndex) {
        this.transactionIndex = transactionIndex;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the block hash.
     *
     * @param blockHash The block hash.
     * @return this builder.
     */
    public TransactionReceiptBuilder blockHash(byte[] blockHash) {
        this.blockHash = blockHash;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the bloom filter of the transaction.
     *
     * @param bloomFilter The bloom filter.
     * @return this builder.
     */
    public TransactionReceiptBuilder bloomFilter(byte[] bloomFilter) {
        this.bloomFilter = bloomFilter;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the transaction hash.
     *
     * @param transactionHash The transaction hash.
     * @return this builder.
     */
    public TransactionReceiptBuilder transactionHash(byte[] transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the hash of the storage state root.
     *
     * @param stateRootHash The state root hash.
     * @return this builder.
     */
    public TransactionReceiptBuilder stateRootHash(byte[] stateRootHash) {
        this.stateRootHash = stateRootHash;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the block number of the block the transaction belongs to.
     *
     * @param blockNumber The block number.
     * @return this builder.
     */
    public TransactionReceiptBuilder blockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
        return this;
    }

    /**
     * <b>Mandatory. This field must be set.</b>
     *
     * Sets the sender of the transaction.
     *
     * @param sender The transaction sender.
     * @return this builder.
     */
    public TransactionReceiptBuilder transactionSender(Address sender) {
        this.sender = sender;
        return this;
    }

    /**
     * <b>Semi-optional. This field may or may not be set: but either it or destination must be set,
     * and both cannot be set.</b>
     *
     * Sets the address of the contract that the transaction deployed.
     *
     * @param deployedContractAddress The address of the newly deployed contract.
     * @return this builder.
     */
    public TransactionReceiptBuilder newlyDeployedContractAddress(Address deployedContractAddress) {
        this.deployedContractAddress = deployedContractAddress;
        return this;
    }

    /**
     * <b>Semi-optional. This field may or may not be set: but either it or the newly deployed
     * contract address must be set, and both cannot be set.</b>
     *
     * Sets the energy price of the transaction.
     *
     * @param destination The destination address of the transaction.
     * @return this builder.
     */
    public TransactionReceiptBuilder transactionDestination(Address destination) {
        this.destination = destination;
        return this;
    }

    /**
     * Builds the receipt from the parameters that were set prior to calling this method.
     *
     * @return the newly built receipt.
     */
    public TransactionReceipt build() {
        if (this.energyPrice < 1) {
            throw new IllegalStateException("Cannot construct transaction receipt - no energy price set.");
        }
        if (this.energyLimit < 0) {
            throw new IllegalStateException("Cannot construct transaction receipt - no energy limit set.");
        }
        if (this.energyConsumed < 0) {
            throw new IllegalStateException("Cannot construct transaction receipt - no energy consumed set.");
        }
        if (this.cumulativeEnergyConsumed < 0) {
            throw new IllegalStateException("Cannot construct transaction receipt - no cumulative energy consumed set.");
        }
        if (this.transactionIndex < 0) {
            throw new IllegalStateException("Cannot construct transaction receipt - no transaction index set.");
        }
        if (this.blockHash == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no block hash set.");
        }
        if (this.bloomFilter == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no bloom filter set.");
        }
        if (this.transactionHash == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no transaction hash set.");
        }
        if (this.stateRootHash == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no state root hash set.");
        }
        if (this.blockNumber == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no block number set.");
        }
        if (this.sender == null) {
            throw new IllegalStateException("Cannot construct transaction receipt - no sender set.");
        }
        if ((this.deployedContractAddress == null) && (this.destination == null)) {
            throw new IllegalStateException("Cannot construct transaction receipt - no destination AND no contract address set.");
        }
        if ((this.deployedContractAddress != null) && (this.destination != null)) {
            throw new IllegalStateException("Cannot construct transaction receipt - both destination AND contract address set.");
        }

        return new TransactionReceipt(
            this.energyPrice,
            this.energyLimit,
            this.energyConsumed,
            this.cumulativeEnergyConsumed,
            this.transactionIndex,
            this.blockHash,
            this.bloomFilter,
            this.transactionHash,
            this.stateRootHash,
            this.blockNumber,
            this.deployedContractAddress,
            this.sender,
            this.destination,
            this.status
        );
    }

    /**
     * Builds a {@link TransactionReceipt} from a provided Json String, where it is assumed that the
     * provided string has all of the attributes necessary to construct this object.
     *
     * In general, this method should only ever be called with the output result of an RPC method
     * that returns a transaction receipt in Json format.
     *
     * Internally, this method calls all of the build methods in this class and builds the receipt,
     * but handles all of the parsing on its own.
     *
     * @param jsonString The json string to build the receipt from.
     * @return the transaction receipt.
     */
    public TransactionReceipt buildFromJsonString(String jsonString) throws DecoderException {
        JsonStringParser jsonParser = new JsonStringParser(jsonString);

        String energyPrice = jsonParser.attributeToString("nrgPrice");
        String energyLimit = jsonParser.attributeToString("gasLimit");
        String energyUsed = jsonParser.attributeToString("nrgUsed");
        String totalEnergyUsed = jsonParser.attributeToString("cumulativeGasUsed");
        String index = jsonParser.attributeToString("transactionIndex");
        String blockHash = jsonParser.attributeToString("blockHash");
        String bloomFilter = jsonParser.attributeToString("logsBloom");
        String transactionHash = jsonParser.attributeToString("transactionHash");
        String rootHash = jsonParser.attributeToString("root");
        String blockNumber = jsonParser.attributeToString("blockNumber");
        String sender = jsonParser.attributeToString("from");
        String contract = jsonParser.attributeToString("contractAddress");
        String destination = jsonParser.attributeToString("to");
        String status = jsonParser.attributeToString("status");

        return new TransactionReceiptBuilder()
            .transactionEnergyPrice((energyPrice == null) ? -1 : Long.parseLong(energyPrice, 16))
            .transactionEnergyLimit((energyLimit == null) ? -1 : Long.parseLong(energyLimit, 16))
            .energyConsumedByTransaction((energyUsed == null) ? -1 : Long.parseLong(energyUsed, 16))
            .totalEnergyConsumedByBlock((totalEnergyUsed == null) ? -1 : Long.parseLong(totalEnergyUsed, 16))
            .indexOfTransactionInBlock((index == null) ? -1 : Integer.parseInt(index, 16))
            .blockHash((blockHash == null) ? null : Hex.decodeHex(blockHash))
            .bloomFilter((bloomFilter == null) ? null : Hex.decodeHex(bloomFilter))
            .transactionHash((transactionHash == null) ? null : Hex.decodeHex(transactionHash))
            .stateRootHash((rootHash == null) ? null : Hex.decodeHex(rootHash))
            .blockNumber((blockNumber == null) ? null : new BigInteger(blockNumber, 16))
            .transactionSender((sender == null) ? null : new Address(Hex.decodeHex(sender)))
            .newlyDeployedContractAddress((contract == null) ? null : new Address(Hex.decodeHex(contract)))
            .transactionDestination((destination == null) ? null : new Address(Hex.decodeHex(destination)))
            .status(status == null? -1: Integer.parseInt(status, 16))
            .build();
    }

    /**
     * Restores this builder to its initial empty state.
     */
    public void clear() {
        this.energyPrice = -1;
        this.energyLimit = -1;
        this.energyConsumed = -1;
        this.cumulativeEnergyConsumed = -1;
        this.transactionIndex = -1;
        this.blockHash = null;
        this.bloomFilter = null;
        this.transactionHash = null;
        this.stateRootHash = null;
        this.blockNumber = null;
        this.deployedContractAddress = null;
        this.sender = null;
        this.destination = null;
    }

}
