package org.aion.harness.integ;

import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.main.LocalNode;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests the various aspects of a node's lifecycle.
 */
public class NodeLifecycleTest {
    private LocalNode node;

    @Before
    public void setup() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        assertTrue(this.node.initialize().isSuccess());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.node = null;
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
    }

    @Test
    public void testStart() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);
        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());
    }

    @Test
    public void testStop() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testStopWhenKernelIsNotStarted() throws IOException, InterruptedException {
        assertFalse(this.node.isAlive());
        Result result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertFalse(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testInvokingStopTwice() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertFalse(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testReset() throws IOException {
        if (!TestHelper.getDefaultDatabaseLocation().exists()) {
            assertTrue(TestHelper.getDefaultDatabaseLocation().mkdir());
        }

        Result result = this.node.resetState();
        System.out.println("Reset result = " + result);
        assertTrue(result.isSuccess());

        assertFalse(TestHelper.getDefaultDatabaseLocation().exists());
    }

    @Test(expected = IllegalStateException.class)
    public void testResetWhileNodeIsRunning() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        this.node.resetState();
    }

    /**
     * This tests verifies that multiple calls to resetState do not cause any exceptions to be thrown.
     */
    @Test
    public void testInvokingResetTwice() throws IOException {
        if (!TestHelper.getDefaultDatabaseLocation().exists()) {
            assertTrue(TestHelper.getDefaultDatabaseLocation().mkdir());
        }

        Result result = this.node.resetState();
        System.out.println("Reset result = " + result);
        assertTrue(result.isSuccess());

        result = this.node.resetState();
        System.out.println("Reset result = " + result);
        assertTrue(result.isSuccess());
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }

}
