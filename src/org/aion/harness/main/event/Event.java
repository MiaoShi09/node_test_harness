package org.aion.harness.main.event;

import java.util.Collections;
import java.util.List;

/**
 * A "base" or "leaf" event in the sense that this event is represented solely as a single String
 * with no conditional logic. Thus, this event counts as being "satisfied" or "observed" if this
 * String has been witnessed.
 *
 * This class meets the immutability guarantees of the {@link IEvent} interface.
 */
public final class Event implements IEvent {
    private final String eventString;
    private boolean isSatisfied = false;

    /**
     * Constrcuts a new event that is considered to be observed once the provided event string has
     * been witnessed.
     *
     * @param eventString The string to witness.
     * @throws NullPointerException if eventString is null.
     */
    public Event(String eventString) {
        if (eventString == null) {
            throw new NullPointerException("Cannot construct node event with null event string.");
        }
        this.eventString = eventString;
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
        // Once satisfied, this value can never change.
        if (!this.isSatisfied) {
            this.isSatisfied = line.contains(this.eventString);
        }
        return this.isSatisfied;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllObservedEvents() {
        return (this.isSatisfied) ? Collections.singletonList(this.eventString) : Collections.emptyList();
    }

    /**
     * A String representation of this object, based off of the event statement (see the
     * {@code eventStatement()} method).
     *
     * @return this object as a string.
     */
    @Override
    public String toString() {
        return "NodeEvent { " + this.eventStatement() + " }";
    }

}
