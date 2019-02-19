package org.aion.harness.util;

import java.util.List;
import org.aion.harness.main.IEvent;
import org.apache.commons.codec.binary.Hex;

public final class NodeEvent implements IEvent {
    private final String eventString;

    private NodeEvent(String eventString) {
        if (eventString == null) {
            throw new NullPointerException("Cannot construct node event with null event string.");
        }
        this.eventString = eventString;
    }

    public static NodeEvent getStartedMiningEvent() {
        return new NodeEvent("sealer starting");
    }

    public static NodeEvent getTransactionSealedEvent(byte[] transactionHash) {
        if (transactionHash == null) {
            throw new NullPointerException("Cannot get event for null transaction hash.");
        }

        return new NodeEvent("Transaction: " + Hex.encodeHexString(transactionHash) + " was sealed into block");
    }

    public static NodeEvent getHeartbeatEvent() {
        // An event we can consider as consistent, and so a good heartbeat.
        return new NodeEvent("p2p-status");
    }

    public static NodeEvent getCustomStringEvent(String string) {
        return new NodeEvent(string);
    }

    public String getEventString() {
        return this.eventString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String eventStatement() {
        return "(" + this.eventString + ")";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IEvent and(IEvent event) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IEvent or(IEvent event) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSatisfiedBy(String line) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllObservedEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "NodeEvent { " + this.eventString + " }";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NodeEvent)) {
            return false;
        }

        if (this == other) {
            return true;
        }

        NodeEvent event = (NodeEvent) other;
        return event.eventString.equals(this.eventString);
    }

    @Override
    public int hashCode() {
        return this.eventString.hashCode();
    }

}
