package org.aion.harness.util;

import org.aion.harness.result.EventRequestResult;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;

import java.util.*;

public final class LogListener implements TailerListener {
    private static final int CAPACITY = 10;

    private List<EventRequest> requestPool = new ArrayList<>(CAPACITY);

    /**
     * Adds the specified event to the list of events that this listener is currently listening for.
     *
     * If the listener is already listening to the maximum number of events then the caller will
     * wait until space becomes available.
     *
     * This method is blocking and will return only once the event request has been satisfied or
     * if the timeout has occurred or if the thread is interrupted, in which case it will return
     * immediately.
     *
     * @param eventRequest The event to request the listener listen for.
     * @param timeoutInMillis The amount of milliseconds to timeout after.
     * @return the result of this request.
     */
    public EventRequestResult submitEventRequest(EventRequest eventRequest, long timeoutInMillis) {
        if (eventRequest == null) {
            throw new NullPointerException("Cannot submit a null event request.");
        }

        long endTimeInMillis = System.currentTimeMillis() + timeoutInMillis;

        try {

            // Add the request to the pool, if we time out then return.
            if (!addRequest(eventRequest, endTimeInMillis)) {
                return EventRequestResult.createRejectedEvent("Timed out waiting for availability in the request pool.");
            }

            // Request was added and we have not yet necessarily timed out, so wait for response.
            long currentTimeInMillis = System.currentTimeMillis();

            synchronized (this) {
                while ((currentTimeInMillis < endTimeInMillis) && (!eventRequest.hasResult())) {
                    System.err.println("Waiting for response..");
                    wait(endTimeInMillis - currentTimeInMillis);
                    currentTimeInMillis = System.currentTimeMillis();
                }
            }

            System.err.println("Got response or timed out!");
            return (eventRequest.hasResult())
                ? eventRequest.getResult()
                : EventRequestResult.createRejectedEvent("Timed out waiting for event to occur.");

        } catch (InterruptedException e) {
            return EventRequestResult.createRejectedEvent("Interrupted while waiting for event.");
        }

    }

    /**
     * Attempts to add the specified request to the list of requests that this listener is listening
     * for.
     *
     * If the current time (in milliseconds) ever becomes equal to or greater than endTimeInMillis
     * then this action will be considered as having timed out and the request will not be added.
     *
     * @param request The request to add to the list of monitored events.
     * @param endTimeInMillis The latest current time to continue attempting to add the event at.
     * @return true if request was added, false if timed out waiting to add request.
     */
    private synchronized boolean addRequest(EventRequest request, long endTimeInMillis) throws InterruptedException {
        long currentTimeInMillis = System.currentTimeMillis();

        while ((currentTimeInMillis < endTimeInMillis) && (this.requestPool.size() == CAPACITY)) {
            System.err.println("Waiting for capacity to free up...");
            wait(endTimeInMillis - currentTimeInMillis);
            currentTimeInMillis = System.currentTimeMillis();
        }

        if (currentTimeInMillis >= endTimeInMillis) {
            System.err.println("Timed out waiting for capacity...");
            return false;
        }

        this.requestPool.add(request);
        System.err.println("Added request, pool size is now = " + this.requestPool.size());
        return true;
    }

    /**
     * Run through the entire request pool and satisfy every request that is waiting on the specified
     * event.
     *
     * Once these requests have been satisfied, remove them from the pool.
     *
     * Wakes up any threads waiting for either space to free up in the request pool or simply
     * waiting for a request to be satisfied.
     *
     * To 'satisfy' a request means simply that the event was observed.
     *
     * @param event The event to satisfy.
     */
    private synchronized void satisfyEventRequest(NodeEvent event, long timeOfObservation) {
        System.err.println("Satisfying an event. Current pool size = " + this.requestPool.size());
        Iterator<EventRequest> requestIterator = this.requestPool.iterator();

        while (requestIterator.hasNext()) {
            EventRequest request = requestIterator.next();

            if (request.getRequest().equals(event)) {
                request.addResult(EventRequestResult.createObservedEvent(timeOfObservation));
                requestIterator.remove();
            }
        }

        System.err.println("Satisfied events. Current pool size is now = " + this.requestPool.size());

        notifyAll();
    }

    /**
     * Returns the {@link NodeEvent} that has been observed in the specified event string.
     *
     * If no event in the pool corresponds to the provided event string then no event has been
     * observed and null is returned.
     *
     * @param eventString Some string that may or may not satisfy an event.
     * @return An observed event or null if no event is observed.
     */
    private synchronized NodeEvent getObservedEvent(String eventString) {
        if ((eventString == null) || (this.requestPool.isEmpty())) {
            return null;
        }

        for (EventRequest request : this.requestPool) {
            if (eventString.contains(request.getRequest().getEventString())) {
                return request.getRequest();
            }
        }

        return null;
    }

    @Override
    public void init(Tailer tailer) {
        //TODO
    }

    @Override
    public void fileNotFound() {
        System.err.println("FILE NOT FOUND");
    }

    @Override
    public void fileRotated() {
        System.err.println("FILE ROTATED!");
    }

    @Override
    public void handle(String nextLine) {

        // Check the pool and determine if an event has been observed and if so, grab that event.
        NodeEvent observedEvent = getObservedEvent(nextLine);
        long timeOfObservation = System.nanoTime();

        // If no events were observed then we are done.
        if (observedEvent == null) {
            return;
        }

        // Satisfy all applicable events, clear the pool of them and notify the waiting threads.
        satisfyEventRequest(observedEvent, timeOfObservation);
    }

    @Override
    public void handle(Exception e) {
        e.printStackTrace();
    }

}
