/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.datanode;

import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_DATANODE_METRICS_LOGGER_PERIOD_SECONDS_KEY;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.logging.LogCapturer;
import org.apache.hadoop.metrics2.util.MBeans;
import org.apache.hadoop.test.GenericTestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Test periodic logging of DataNode metrics.
 */
public class TestDataNodeMetricsLogger {
  static final Logger LOG =
      LoggerFactory.getLogger(TestDataNodeMetricsLogger.class);

  private static final String DATA_DIR = MiniDFSCluster.getBaseDirectory()
      + "data";

  private final static InetSocketAddress NN_ADDR = new InetSocketAddress(
      "localhost", 5020);

  private DataNode dn;

  static final Random random = new Random(System.currentTimeMillis());

  @Rule
  public Timeout timeout = new Timeout(300000);

  /**
   * Starts an instance of DataNode
   *
   * @throws IOException
   */
  public void startDNForTest(boolean enableMetricsLogging) throws IOException {
    Configuration conf = new HdfsConfiguration();
    conf.set(DFSConfigKeys.DFS_DATANODE_DATA_DIR_KEY, DATA_DIR);
    conf.set(DFSConfigKeys.DFS_DATANODE_ADDRESS_KEY, "0.0.0.0:0");
    conf.set(DFSConfigKeys.DFS_DATANODE_HTTP_ADDRESS_KEY, "0.0.0.0:0");
    conf.set(DFSConfigKeys.DFS_DATANODE_IPC_ADDRESS_KEY, "0.0.0.0:0");
    conf.setInt(CommonConfigurationKeys.IPC_CLIENT_CONNECT_MAX_RETRIES_KEY, 0);
    conf.setInt(DFS_DATANODE_METRICS_LOGGER_PERIOD_SECONDS_KEY,
        enableMetricsLogging ? 1 : 0); // If enabled, log early and log often

    dn = InternalDataNodeTestUtils.startDNWithMockNN(conf, NN_ADDR, DATA_DIR);
  }

  /**
   * Cleans the resources and closes the instance of datanode
   *
   * @throws IOException
   *           if an error occurred
   */
  @After
  public void tearDown() throws IOException {
    if (dn != null) {
      try {
        dn.shutdown();
      } catch (Exception e) {
        LOG.error("Cannot close: ", e);
      } finally {
        File dir = new File(DATA_DIR);
        if (dir.exists())
          Assert.assertTrue("Cannot delete data-node dirs",
              FileUtil.fullyDelete(dir));
      }
    }
    dn = null;
  }

  @Test
  public void testMetricsLoggerOnByDefault() throws IOException {
    startDNForTest(true);
    assertNotNull(dn);
    assertNotNull(dn.getMetricsLoggerTimer());
  }

  @Test
  public void testDisableMetricsLogger() throws IOException {
    startDNForTest(false);
    assertNotNull(dn);
    assertNull(dn.getMetricsLoggerTimer());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testMetricsLoggerIsAsync() throws IOException {
    startDNForTest(true);
    assertNotNull(dn);
    assertTrue(Collections.list(
            org.apache.log4j.Logger.getLogger(DataNode.METRICS_LOG_NAME).getAllAppenders())
        .get(0) instanceof org.apache.log4j.AsyncAppender);
  }

  /**
   * Publish a fake metric under the "Hadoop:" domain and ensure it is logged by
   * the metrics logger.
   */
  @Test
  public void testMetricsLogOutput() throws IOException, InterruptedException,
      TimeoutException {
    TestFakeMetric metricsProvider = new TestFakeMetric();
    MBeans.register(this.getClass().getSimpleName(), "DummyMetrics",
        metricsProvider);
    startDNForTest(true);
    assertNotNull(dn);
    LogCapturer logCapturer =
        LogCapturer.captureLogs(LoggerFactory.getLogger(DataNode.METRICS_LOG_NAME));
    // Ensure that the supplied pattern was matched.
    GenericTestUtils.waitFor(() -> logCapturer.getOutput().contains("FakeMetric"),
        1000, 60000);
    logCapturer.stopCapturing();
    dn.shutdown();
  }

  public interface TestFakeMetricMXBean {
    int getFakeMetric();
  }

  /**
   * MBean for testing
   */
  public static class TestFakeMetric implements TestFakeMetricMXBean {
    @Override
    public int getFakeMetric() {
      return 0;
    }
  }

}
