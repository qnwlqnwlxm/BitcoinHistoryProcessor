/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbio.custom.processors;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.derby.drda.NetworkServerControl;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BitcoinHistoryTest {
  private static final String TEST_FILE = "test.csv";
  private static final String BAD_TEST_FILE = "bad.csv";
  private TestRunner testRunner;
  private Path input;
  private Path badInput;

  private static NetworkServerControl derby;
  final static String DB_LOCATION = "target/db";

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty("derby.stream.error.file", "target/derby.log");
    derby = new NetworkServerControl();
    derby.start(new PrintWriter(System.out));
  }

  @AfterClass
  public static void stop() throws Exception {
    derby.shutdown();
  }

  @Before
  public void init() throws Exception {

    testRunner = TestRunners.newTestRunner(BitcoinHistory.class);
    input = Paths.get(ClassLoader.getSystemResource(TEST_FILE).toURI());
    badInput = Paths.get(ClassLoader.getSystemResource(BAD_TEST_FILE).toURI());
    testRunner.setClustered(true);

    final DBCPService dbcp = new DBCPServiceSimpleImpl();

    final Map<String, String> dbcpProperties = new HashMap<>();

    testRunner.addControllerService("dbcp", dbcp, dbcpProperties);
    testRunner.enableControllerService(dbcp);
    testRunner.setProperty(ConfigUtil.DS_PROP, "dbcp");

    final Connection con = ((DBCPService) testRunner.getControllerService("dbcp")).getConnection();
    Statement stmt = con.createStatement();
    try {
      stmt.execute("drop table bitcoin_history");
    } catch (final SQLException sqle) {
    }
    stmt.execute(
        "create table bitcoin_history (history_time timestamp not null, open_price decimal(20,10), high decimal(20,10), low decimal(20,10),"
        +"close_price decimal(20,10),btc_volume decimal(20,10),usd_volume decimal(20,10),weighted_price decimal(20,10),rgs_dtm timestamp,constraint my_pk primary key (history_time))");
  }

  @Test
  public void testAllOutput() throws Exception {

    testRunner.enqueue(input);
    testRunner.setProperty(ConfigUtil.OUTPUT, "ALL");

    testRunner.run();

    testRunner.assertQueueEmpty();

    testRunner.assertTransferCount(ConfigUtil.JSON, 1);
    testRunner.assertTransferCount(ConfigUtil.XML, 1);
    testRunner.assertTransferCount(ConfigUtil.FAILURE, 0);

    testRunner.shutdown();
  }

  @Test
  public void testJsonOnlyOutput() throws Exception {

    testRunner.enqueue(input);
    testRunner.setProperty(ConfigUtil.OUTPUT, "JSON");

    testRunner.run();

    testRunner.assertQueueEmpty();

    testRunner.assertTransferCount(ConfigUtil.JSON, 1);
    testRunner.assertTransferCount(ConfigUtil.XML, 0);
    testRunner.assertTransferCount(ConfigUtil.FAILURE, 0);

    testRunner.shutdown();
  }

  @Test
  public void testXmlOnlyOutput() throws Exception {

    testRunner.enqueue(input);
    testRunner.setProperty(ConfigUtil.OUTPUT, "XML");

    testRunner.run();

    testRunner.assertQueueEmpty();

    testRunner.assertTransferCount(ConfigUtil.JSON, 0);
    testRunner.assertTransferCount(ConfigUtil.XML, 1);
    testRunner.assertTransferCount(ConfigUtil.FAILURE, 0);

    testRunner.shutdown();
  }

  @Test
  public void testBadOutput() throws Exception {

    testRunner.enqueue(badInput);
    testRunner.setClustered(true);
    testRunner.setProperty(ConfigUtil.OUTPUT, "XML");

    testRunner.run();

    testRunner.assertQueueEmpty();

    testRunner.assertTransferCount(ConfigUtil.JSON, 0);
    testRunner.assertTransferCount(ConfigUtil.XML, 0);
    testRunner.assertTransferCount(ConfigUtil.FAILURE, 1);

    testRunner.shutdown();
  }

  class DBCPServiceSimpleImpl extends AbstractControllerService implements DBCPService {

    @Override
    public String getIdentifier() {
      return "dbcp";
    }

    @Override
    public Connection getConnection() throws ProcessException {
      try {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        final Connection con = DriverManager.getConnection("jdbc:derby:" + DB_LOCATION + ";create=true");
        return con;
      } catch (final Exception e) {
        throw new ProcessException("getConnection failed: " + e);
      }
    }
  }

}
