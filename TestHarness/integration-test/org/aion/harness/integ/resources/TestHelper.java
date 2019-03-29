package org.aion.harness.integ.resources;

import java.io.File;
import java.io.IOException;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;

public class TestHelper {
    public static final String EXPECTED_BUILD_LOCATION = System.getProperty("user.dir") + File.separator + "aion";
    public static final Network DEFAULT_NETWORK = Network.MASTERY;

    public static File getDefaultDatabaseLocation() {
        return new File(EXPECTED_BUILD_LOCATION + File.separator + DEFAULT_NETWORK.string() + File.separator + "database");
    }

    public static LocalNode configureDefaultLocalNodeAndDoNotPreserveDatabase() throws IOException {
        return getDefaultLocalNode(DEFAULT_NETWORK, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
    }

    public static LocalNode configureDefaultLocalNodeToPreserveDatabase() throws IOException {
        return getDefaultLocalNode(DEFAULT_NETWORK, DatabaseOption.PRESERVE_DATABASE);
    }

    public static LocalNode configureDefaultLocalNodeForNetwork(Network network) throws IOException {
        return getDefaultLocalNode(network, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
    }

    private static LocalNode getDefaultLocalNode(Network network, DatabaseOption databaseOption) throws IOException {
        File expectedBuildLocation = new File(EXPECTED_BUILD_LOCATION);

        if (!expectedBuildLocation.exists()) {
            System.err.println("-------------------------------------------------------------------------------------------");
            System.err.println("ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println("The built kernel is expected to be found at the following location: " + EXPECTED_BUILD_LOCATION);
            System.err.println("-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        if (!expectedBuildLocation.isDirectory()) {
            System.err.println("-------------------------------------------------------------------------------------------");
            System.err.println("ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println("A file was found at the expected location but it is not a directory: " + EXPECTED_BUILD_LOCATION);
            System.err.println("This must be the root directory of the built kernel.");
            System.err.println("-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeFactory.NodeType.JAVA_NODE);
        node.configure(NodeConfigurations.alwaysUseBuiltKernel(network, EXPECTED_BUILD_LOCATION, databaseOption));
        return node;
    }

}
