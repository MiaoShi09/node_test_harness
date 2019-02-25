package org.aion.harness.main.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.aion.harness.misc.Assumptions;

/**
 * A class responsible for calling an RPC endpoint using the provided payload.
 */
public final class RpcCaller {

    public InternalRpcResult call(RpcPayload payload, boolean verbose) throws InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder()
            .command("curl", "-X", "POST", "--data", payload.payload, Assumptions.IP + ":" + Assumptions.PORT);

        if (verbose) {
            processBuilder.inheritIO();
        }

        try {
            long timeOfCall = System.currentTimeMillis();
            Process rpcProcess = processBuilder.start();

            int status = rpcProcess.waitFor();
            StringBuilder stringBuilder = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(rpcProcess.getInputStream()))) {
                String line = reader.readLine();

                while (line != null) {
                    stringBuilder.append(line);
                    line = reader.readLine();
                }
            }

            String output = stringBuilder.toString();

            if (output.isEmpty()) {
                return InternalRpcResult.unsuccessful("unknown error");
            }

            RpcOutputParser outputParser = new RpcOutputParser(output);

            if ((status == 0) && (!outputParser.hasAttribute("error"))) {
                return InternalRpcResult.successful(output, timeOfCall);
            } else {
                String error = outputParser.attributeToString("error");

                // We expect the content of 'error' to itself be a Json String. If it has no content
                // then the error is unknown.
                if (error == null) {
                    return InternalRpcResult.unsuccessful("unknown error");
                } else {
                    RpcOutputParser errorParser = new RpcOutputParser(error);

                    // The 'data' attribute should capture the error.
                    error = errorParser.attributeToString("data");

                    // If there was no data value then try to grab the less informative 'message'.
                    error = (error == null) ? errorParser.attributeToString("message") : error;

                    return InternalRpcResult.unsuccessful(error);
                }
            }

        } catch (IOException e) {
            return InternalRpcResult.unsuccessful(e.toString());
        }
    }

}
