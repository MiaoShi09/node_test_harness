package org.aion.harness.main.impl;

import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.SimpleLog;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProxyRustNode extends RustNode{
    private final SimpleLog log;

    public ProxyRustNode() {
        super();
        log = new SimpleLog(getClass().getName());
    }

    /**
     * Block until logs indicate that either RPC server started or an error happened
     */
    protected Result waitForKernelReadyOrError(File outputLog) throws InterruptedException {
        if (isAlive()) {
            Result result = this.logReader.startReading(outputLog);
            if (!result.isSuccess()) {
                return result;
            }

            IEvent rpcEv = new Event("= Sync Statics =");
            IEvent havePeerEv = new Event("outbound -> active");
            IEvent nearBestEv = new Event("PendingStateImpl.processBest: closeToNetworkBest[true]");

            try {
                NodeListener listener = NodeListener.listenTo(this);

                FutureResult<LogEventResult> futureNearBestBlock = listener
                        .listenForEvent(nearBestEv, 3, TimeUnit.MINUTES);

                listener.listenForEvent(
                        rpcEv.and(havePeerEv), 60, TimeUnit.SECONDS
                ).get(60, TimeUnit.SECONDS);

                log.log("Rust Kernel event maybe be the same; update listening events later");
                futureNearBestBlock.get(5, TimeUnit.MINUTES);
                log.log("Kernel is near best block.");
            } catch (TimeoutException te) {
                String msg = "Rust Kernel event maybe be the same; update listening events later";
                log.log(msg);

                return Result.successful();
            }

            return Result.successful();
        } else {
            return Result.unsuccessfulDueTo("Node failed to start!");
        }
    }
}
