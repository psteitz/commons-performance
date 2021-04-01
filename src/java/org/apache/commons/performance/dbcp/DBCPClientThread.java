/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.performance.dbcp;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.Statistics;

/**
 * Client thread that executes requests in a loop using a configured DataSource, with the number of
 * requests, time between requests and query strings governed by constructor parameters. See
 * {@link ClientThread ClientThread javadoc} for a description of how times between requests are
 * computed.
 * 
 */
public class DBCPClientThread
    extends ClientThread {

    /** Initial segment of query string */
    private String queryString = null;
    /** Whether or not the query is on the text column */
    private boolean textQuery = false;
    /** DataSource used to connect */
    private DataSource dataSource = null;
    /** Type of DataSource */
    private DataSourceType type;
    /** Database connection */
    Connection conn = null;
    /** Current query */
    String currentQuery = null;

    /** Statistics on numActive */
    private SummaryStatistics numActiveStats = new SummaryStatistics();
    /** Statistics on numIdle */
    private SummaryStatistics numIdleStats = new SummaryStatistics();
    /** Sampling rate for numActive, numIdle */
    private double samplingRate;
    private final RandomDataGenerator random = new RandomDataGenerator();

    // Cast targets to query for stats
    org.apache.commons.dbcp.BasicDataSource dbcp1DS = null;
    org.apache.commons.dbcp2.BasicDataSource dbcp2DS = null;
    org.apache.tomcat.jdbc.pool.DataSource tomcatDS = null;

    /**
     * Create a dbcp client thread.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum delay time between client requests
     * @param maxDelay maximum delay time between client requests
     * @param sigma standard deviation of delay times between client requests
     * @param delayType distribution of time between client requests
     * @param queryType type of query
     * @param rampPeriod rampup, rampdown period for cyclic load
     * @param peakPeriod period of sustained peak for cyclic load
     * @param troughPeriod period of sustained minimum load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param dataSource DataSource for connections
     * @param stats Statistics container
     */
    public DBCPClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                            String queryType, long rampPeriod, long peakPeriod, long troughPeriod, String cycleType,
                            String rampType, Logger logger, DataSource dataSource, Statistics stats, double samplingRate) {

        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
              rampType, logger, stats);

        this.dataSource = dataSource;

        if (dataSource instanceof org.apache.commons.dbcp.BasicDataSource) {
            type = DataSourceType.DBCP1;
            dbcp1DS = (org.apache.commons.dbcp.BasicDataSource) dataSource;
        } else if (dataSource instanceof org.apache.commons.dbcp2.BasicDataSource) {
            type = DataSourceType.DBCP2;
            dbcp2DS = (org.apache.commons.dbcp2.BasicDataSource) dataSource;
        } else {
            type = DataSourceType.TOMCAT;
            tomcatDS = (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
        }

        if (queryType.equals("no-op")) {
            return;
        }

        if (queryType.equals("integerIndexed")) {
            queryString = "select * from test_table WHERE indexed=";
        } else if (queryType.equals("integerScan")) {
            queryString = "select * from test_table WHERE not_indexed=";
        } else {
            queryString = "select * from test_table WHERE text='";
            textQuery = true;
        }

        this.samplingRate = samplingRate;
    }

    /** Generate a random query */
    @Override
    public void setUp()
        throws Exception {
        if (queryString == null) {
            return;
        }
        if (textQuery) {
            currentQuery = queryString + randomData.nextHexString(20) + "';";
        } else {
            currentQuery = queryString + randomData.nextInt(0, 100) + ";";
        }
    }

    /** Execute query */
    @Override
    public void execute()
        throws Exception {
        conn = dataSource.getConnection();
        if (queryString == null) {
            return;
        }
        Statement stmt = conn.createStatement();
        stmt.execute(currentQuery);
        ResultSet rs = stmt.getResultSet();
        if (!rs.isAfterLast()) {
            rs.next();
        }
        rs.close();
        stmt.close();
    }

    /** Close connection, capture idle / active stats */
    @Override
    public void cleanUp()
        throws Exception {
        if (conn != null) {
            try {
                conn.close();
            } finally {
                conn = null;
            }
        }
        // Capture pool metrics
        if (randomData.nextUniform(0, 1) < samplingRate) {
            int numIdle;
            int numActive;
            switch (type) {
                case DBCP1:
                    numActive = dbcp1DS.getNumActive();
                    numIdle = dbcp1DS.getNumIdle();
                    break;
                case DBCP2:
                    numActive = dbcp2DS.getNumActive();
                    numIdle = dbcp2DS.getNumIdle();
                    break;
                case TOMCAT:
                    numActive = tomcatDS.getNumActive();
                    numIdle = tomcatDS.getNumIdle();
                    break;
                default:
                    throw new RuntimeException("Invalid datasource type");
            }
            numIdleStats.addValue(numIdle);
            numActiveStats.addValue(numActive);
        }
    }

    @Override
    protected void finish()
        throws Exception {
        // Add metrics to stats
        stats.addStatistics(numIdleStats, Thread.currentThread().getName(), "numIdle");
        stats.addStatistics(numActiveStats, Thread.currentThread().getName(), "numActive");
    }

    private enum DataSourceType {
        DBCP1, DBCP2, TOMCAT
    }

}
