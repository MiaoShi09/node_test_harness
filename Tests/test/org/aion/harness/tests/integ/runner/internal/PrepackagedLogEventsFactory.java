package org.aion.harness.tests.integ.runner.internal;

import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.main.event.PrepackagedLogEvents;
import org.aion.harness.main.event.RustPrepackagedLogEvents;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class PrepackagedLogEventsFactory implements TestRule {

    private PrepackagedLogEvents prepackagedLogEvents = null;

    @Override
    public Statement apply(Statement statement, Description description) {
        throw new UnsupportedOperationException("This TestRule is not intended to be executed.");
    }

    /**
     * Intended to be invoked only by TestHarness custom JUnit Runners.
     * @param nodeType type of Node that the log events are for
     */
    private void setKernel(NodeType nodeType) {
        switch(nodeType) {
            case RUST_NODE:
            case PROXY_RUST_NODE:
                prepackagedLogEvents = new RustPrepackagedLogEvents();
                break;
            case JAVA_NODE: 
            case PROXY_JAVA_NODE:
                prepackagedLogEvents = new JavaPrepackagedLogEvents();
                break;
            default:
                throw new IllegalArgumentException("Unknown node type.");
        }
    }

    /** @return prepackaged log events for the configured type of {@link NodeType} */
    public PrepackagedLogEvents build() {
        if(prepackagedLogEvents == null) {
            throw new IllegalArgumentException("Node type not yet configured.");
        }

        return prepackagedLogEvents;
    }
}
