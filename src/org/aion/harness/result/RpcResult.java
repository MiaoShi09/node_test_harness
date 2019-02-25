package org.aion.harness.result;

import org.aion.harness.main.tools.RpcOutputParser;

/**
 * A result from an RPC call.
 *
 * An rpc result is either successful or not.
 *
 * If the rpc result is unsuccessful then {@code output}, {@code getResult()} and
 * {@code timeOfCallInMillis} are all meaningless, but {@code error} will be meaningful.
 *
 * If the rpc result is successful then {@code timeOfCallInMillis} represents the time at which the
 * RPC call was made, in milliseconds, and {@code getResult()} will return the particular result of
 * the call.
 *
 * The preferred way of extracing meaningful information from the {@code output} field is using the
 * {@link RpcOutputParser} class.
 *
 * There is not concept of equality defined for an rpc result.
 *
 * An rpc result is immutable, except for the generic object returned by {@code getResult()}, whose
 * immutability is of course dependent upon whatever type this ends up being.
 */
public final class RpcResult<T> {
    public final boolean success;
    public final String output;
    public final String error;
    public final long timeOfCallInMillis;

    private final T result;

    private RpcResult(boolean success, T result, String output, String error, long time) {
        if (output == null) {
            throw new NullPointerException("Cannot construct rpc result with null output.");
        }
        if (error == null) {
            throw new NullPointerException("Cannot construct rpc result with null error.");
        }

        this.success = success;
        this.output = output;
        this.error = error;
        this.timeOfCallInMillis = time;
        this.result = result;
    }

    /**
     * Constructs a successful rpc result, where output is the output returned by the rpc call and
     * timeOfCallInMillis is the time at which the call was made, in milliseconds.
     *
     * @param output The output of the RPC call.
     * @param result The result of the RPC call.
     * @param timeOfCallInMillis The time of the RPC call.
     * @return a successful rpc result.
     */
    public static <T> RpcResult<T> successful(String output, T result, long timeOfCallInMillis) {
        if (result == null) {
            throw new NullPointerException("Cannot construct rpc result with null result.");
        }
        if (timeOfCallInMillis < 0) {
            throw new IllegalArgumentException("cannot construct successful rpc result with negative-valued timestamp.");
        }
        return new RpcResult<>(true, result, output, "", timeOfCallInMillis);
    }

    /**
     * Constrcuts an unsuccessful rpc result, where error is a string descriptive of the error that
     * occurred.
     *
     * @param error The cause of the unsuccessful call.
     * @return an unsuccessful rpc result.
     */
    public static <T> RpcResult<T> unsuccessful(String error) {
        return new RpcResult<>(false, null, "", error, -1);
    }

    /**
     * Returns the result of the RPC call.
     *
     * @return the result of the call.
     */
    public T getResult() {
        return this.result;
    }

    @Override
    public String toString() {
        if (this.success) {
            return "RpcResult { successful | timestamp: " + this.timeOfCallInMillis + " (millis) | output: " + this.output + " }";
        } else {
            return "RpcResult { unsuccessful due to: " + this.error + " }";
        }
    }
}
