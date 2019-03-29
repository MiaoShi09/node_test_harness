package org.aion.harness.integ;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.junit.After;
import org.junit.Test;

/**
 * Tests aspects of the node's lifecycle when the node is used improperly.
 */
public class NodeLifecycleMisuseTest {
    private static final String NONEXISTENT_PATH = System.getProperty("user.dir") + "/i_dont_exist";
    private static final String NON_DIR_PATH = System.getProperty("user.dir") + "/not_a_directory";
    private LocalNode node;

    @After
    public void tearDown() throws IOException, InterruptedException {
        new File(NON_DIR_PATH).delete();

        if (this.node != null) {
            this.node.stop();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testNullConfigurations() {
        NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE).configure(null);
    }

    @Test
    public void testInitializeOnNonExistentBuild() throws IOException, InterruptedException {
        NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(Network.CUSTOM, NONEXISTENT_PATH, DatabaseOption.PRESERVE_DATABASE);
        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        assertFalse(node.initialize().isSuccess());
    }

    @Test
    public void testInitializeOnBuildThatIsNotDirectory() throws IOException, InterruptedException {
        assertTrue(new File(NON_DIR_PATH).createNewFile());
        NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(Network.CUSTOM, NON_DIR_PATH, DatabaseOption.PRESERVE_DATABASE);
        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        assertFalse(node.initialize().isSuccess());
    }

    @Test
    public void testInitializeOnNonExistentSourceDirectory() throws IOException, InterruptedException {
        NodeConfigurations configurations = NodeConfigurations.alwaysBuildFromSource(Network.CUSTOM, NONEXISTENT_PATH);
        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        assertFalse(node.initialize().isSuccess());
    }

    @Test
    public void testInitializeOnSourceThatIsNotDirectory() throws IOException, InterruptedException {
        assertTrue(new File(NON_DIR_PATH).createNewFile());
        NodeConfigurations configurations = NodeConfigurations.alwaysBuildFromSource(Network.CUSTOM, NON_DIR_PATH);
        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        assertFalse(node.initialize().isSuccess());
    }

    @Test
    public void testStopWhenNodeIsNotAlive() throws IOException, InterruptedException {
        LocalNode node = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        assertTrue(node.initialize().isSuccess());
        assertFalse(node.stop().isSuccess());
    }

    @Test(expected = IllegalStateException.class)
    public void testInvokingStartTwice() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        assertTrue(this.node.initialize().isSuccess());
        assertTrue(this.node.start().isSuccess());

        this.node.start();
    }

}
