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
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.dbcp.AbandonedConfig;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.ConfigurationException;
import org.apache.commons.performance.LoadGenerator;
import org.apache.commons.performance.Statistics;
import org.apache.commons.pool.KeyedObjectPoolFactory;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericKeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * Configurable load / performance tester for commons dbcp. Uses Commons Digester to parse and load
 * configuration and spawns DBCPClientThread instances to generate load and gather statistics.
 * 
 */
public class DBCPSoak
    extends LoadGenerator {

    // Connection properties
    private String driverClass;
    private String connectUrl;
    private String connectUser;
    private String connectPassword;
    private String queryType;

    // Connection pool properties
    private String poolType;
    private String driverType;
    private String factoryType;
    private boolean autocommit;
    private boolean readOnly;
    private byte exhaustedAction;
    private boolean testOnBorrow;
    private boolean testOnReturn;
    private long timeBetweenEvictions;
    private int testsPerEviction;
    private long idleTimeout;
    private boolean testWhileIdle;
    private String validationQuery;
    private AbandonedConfig abandonedConfig = new AbandonedConfig();
    private boolean poolPreparedStatements;
    private int maxOpenStatements;
    private int maxActive;
    private int maxIdle;
    private int minIdle;
    private long maxWait;
    private double samplingRate;

    // DataSource type
    private String dataSourceType;

    // Instance variables
    private GenericObjectPool connectionPool;
    private DataSource dataSource;

    /**
     * Create connection pool and, if necessary, test table.
     */
    @Override
    protected void init()
        throws Exception {

        if (dataSourceType.equals("BasicDataSource")) {
            org.apache.commons.dbcp.BasicDataSource bds = new org.apache.commons.dbcp.BasicDataSource();
            bds.setDefaultAutoCommit(autocommit);
            bds.setPassword(connectPassword);
            bds.setUrl(connectUrl);
            bds.setUsername(connectUser);
            bds.setDriverClassName(driverClass);
            bds.setMinEvictableIdleTimeMillis(idleTimeout);
            bds.setMaxActive(maxActive);
            bds.setMaxIdle(maxIdle);
            bds.setMaxWait(maxWait);
            bds.setMinIdle(minIdle);
            bds.setPoolPreparedStatements(poolPreparedStatements);
            bds.setDefaultReadOnly(readOnly);
            bds.setTestOnBorrow(testOnBorrow);
            bds.setTestOnReturn(testOnReturn);
            bds.setTestWhileIdle(testWhileIdle);
            bds.setNumTestsPerEvictionRun(testsPerEviction);
            bds.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            bds.setValidationQuery(validationQuery);
            if (poolType.equals("AbandonedObjectPool")) {
                bds.setRemoveAbandoned(true);
            }
            dataSource = bds;
            checkDatabase();
            return;
        }

        if (dataSourceType.equals("BasicDataSource2")) {
            org.apache.commons.dbcp2.BasicDataSource bds = new org.apache.commons.dbcp2.BasicDataSource();
            bds.setDefaultAutoCommit(autocommit);
            bds.setPassword(connectPassword);
            bds.setUrl(connectUrl);
            bds.setUsername(connectUser);
            bds.setDriverClassName(driverClass);
            bds.setMinEvictableIdleTimeMillis(idleTimeout);
            bds.setMaxTotal(maxActive);
            bds.setMaxIdle(maxIdle);
            bds.setMaxWaitMillis(maxWait);
            bds.setMinIdle(minIdle);
            bds.setPoolPreparedStatements(poolPreparedStatements);
            bds.setDefaultReadOnly(readOnly);
            bds.setTestOnBorrow(testOnBorrow);
            bds.setTestOnReturn(testOnReturn);
            bds.setTestWhileIdle(testWhileIdle);
            bds.setNumTestsPerEvictionRun(testsPerEviction);
            bds.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            bds.setValidationQuery(validationQuery);
            if (poolType.equals("AbandonedObjectPool")) {
                bds.setRemoveAbandonedOnBorrow(true);
            }
            dataSource = bds;
            checkDatabase();
            return;
        }

        if (dataSourceType.equals("tomcat-jdbc-pool")) {
            PoolProperties config = new PoolProperties();
            config.setDefaultAutoCommit(autocommit);
            config.setPassword(connectPassword);
            config.setUrl(connectUrl);
            config.setUsername(connectUser);
            config.setDriverClassName(driverClass);
            config.setMinEvictableIdleTimeMillis((int) idleTimeout);
            config.setMaxActive(maxActive);
            config.setMaxIdle(maxIdle);
            config.setMaxWait((int) maxWait);
            config.setMinIdle(minIdle);
            config.setDefaultReadOnly(readOnly);
            config.setTestOnBorrow(testOnBorrow);
            config.setTestOnReturn(testOnReturn);
            config.setTestWhileIdle(testWhileIdle);
            config.setNumTestsPerEvictionRun(testsPerEviction);
            config.setTimeBetweenEvictionRunsMillis((int) timeBetweenEvictions);
            dataSource = new org.apache.tomcat.jdbc.pool.DataSource(config);
            checkDatabase();
            return;
        }

        Class.forName(driverClass);

        // Create object pool
        if (poolType.equals("GenericObjectPool")) {
            connectionPool = new org.apache.commons.pool.impl.GenericObjectPool(null, maxActive, exhaustedAction,
                                                                                maxWait, maxIdle, minIdle,
                                                                                testOnBorrow, testOnReturn,
                                                                                timeBetweenEvictions, testsPerEviction,
                                                                                idleTimeout, testWhileIdle);
        } else if (poolType.equals("AbandonedObjectPool")) {
            connectionPool = new org.apache.commons.dbcp.AbandonedObjectPool(null, abandonedConfig);
        } else {
            throw new ConfigurationException("invalid pool type configuration: " + poolType);
        }

        // Create raw connection factory
        ConnectionFactory connectionFactory = null;
        if (driverType.equals("DriverManager")) {
            connectionFactory = new DriverManagerConnectionFactory(connectUrl, connectUser, connectPassword);
        } else if (driverType.equals("Driver")) {
            Properties props = new Properties();
            props.put("user", connectUser);
            props.put("password", connectPassword);
            connectionFactory = new DriverConnectionFactory((Driver) Class.forName(driverClass).newInstance(),
                                                            connectUrl, props);
        } else {
            throw new ConfigurationException("Bad config setting for driver type");
        }

        // Create object factory
        PoolableObjectFactory poolableConnectionFactory = null;
        KeyedObjectPoolFactory statementPoolFactory = null;
        if (poolPreparedStatements) { // Use same defaults as BasicDataSource
            statementPoolFactory = new GenericKeyedObjectPoolFactory(null, -1, // unlimited
                                                                               // maxActive (per
                                                                               // key)
                                                                     GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL, 0, // maxWait
                                                                     1, // maxIdle (per key)
                                                                     maxOpenStatements); // TODO:
                                                                                         // make all
                                                                                         // configurable
        }
        if (factoryType.equals("PoolableConnectionFactory")) {
            poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, connectionPool,
                                                                      statementPoolFactory, validationQuery, readOnly,
                                                                      autocommit);
        } else if (factoryType.equals("CPDSConnectionFactory")) {
            throw new ConfigurationException("CPDSConnectionFactory not implemented yet");
        } else {
            throw new ConfigurationException("Invalid factory type: " + factoryType);
        }

        // Create DataSource
        dataSource = new PoolingDataSource(connectionPool);
        checkDatabase();
    }

    protected void checkDatabase()
        throws Exception {
        // Try to connect and query test_table. If "test_table" appears in
        // exception message, assume table needs to be created.
        Connection conn = dataSource.getConnection();
        try {
            Statement stmnt = conn.createStatement();
            stmnt.execute("select * from test_table where indexed=1;");
            stmnt.close();
        } catch (Exception ex) {
            if (ex.getMessage().indexOf("test_table") > 0) {
                logger.info("Creating test_table");
                makeTable();
                logger.info("test_table created successfully");
            } else {
                throw ex;
            }
        } finally {
            conn.close();
        }
    }

    /**
     * Close connection pool
     */
    @Override
    protected void cleanUp()
        throws Exception {
        if (dataSourceType.equals("BasicDataSource")) {
            ((org.apache.commons.dbcp.BasicDataSource) dataSource).close();
        } else if (dataSourceType.equals("tomcat-jdbc-pool")) {
            ((org.apache.tomcat.jdbc.pool.DataSource) dataSource).close();
        } else {
            ((org.apache.commons.dbcp2.BasicDataSource) dataSource).close();
        }
    }

    @Override
    protected ClientThread makeClientThread(long iterations, long minDelay, long maxDelay, double sigma,
        String delayType, long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
        Logger logger, Statistics stats) {

        return new DBCPClientThread(iterations, minDelay, maxDelay, sigma, delayType, queryType, rampPeriod,
                                    peakPeriod, troughPeriod, cycleType, rampType, logger, dataSource, stats,
                                    samplingRate);
    }

    // ------------------------------------------------------------------------
    // Configuration methods specific to this LoadGenerator invoked by Digester
    // when superclass execute calls digester.parse.
    // ------------------------------------------------------------------------
    public void configureDataBase(String driver, String url, String username, String password, String queryType) {
        this.driverClass = driver;
        this.connectUrl = url;
        this.connectUser = username;
        this.connectPassword = password;
        this.queryType = queryType;
    }

    public void configureDataSource(String type) {
        this.dataSourceType = type;
    }

    public void configureConnectionFactory(String type, String autoCommit, String readOnly, String validationQuery) {
        this.driverType = type;
        this.autocommit = Boolean.parseBoolean(autoCommit);
        this.readOnly = Boolean.parseBoolean(readOnly);
        this.validationQuery = validationQuery;
    }

    public void configurePoolableConnectionFactory(String type, String poolPreparedStatements, String maxOpenStatements) {
        this.factoryType = type;
        this.poolPreparedStatements = Boolean.parseBoolean(poolPreparedStatements);
        this.maxOpenStatements = Integer.parseInt(maxOpenStatements);
    }

    public void configurePool(String maxActive, String maxIdle, String minIdle, String maxWait, String exhaustedAction,
        String testOnBorrow, String testOnReturn, String timeBetweenEvictions, String testsPerEviction,
        String idleTimeout, String testWhileIdle, String type, String samplingRate)
        throws ConfigurationException {
        this.maxActive = Integer.parseInt(maxActive);
        this.maxIdle = Integer.parseInt(maxIdle);
        this.minIdle = Integer.parseInt(minIdle);
        this.maxWait = Long.parseLong(maxWait);
        this.testOnBorrow = Boolean.parseBoolean(testOnBorrow);
        this.testOnReturn = Boolean.parseBoolean(testOnReturn);
        this.timeBetweenEvictions = Long.parseLong(timeBetweenEvictions);
        this.testsPerEviction = Integer.parseInt(testsPerEviction);
        this.idleTimeout = Long.parseLong(idleTimeout);
        this.testWhileIdle = Boolean.parseBoolean(testWhileIdle);
        this.poolType = type;
        if (exhaustedAction.equals("block")) {
            this.exhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
        } else if (exhaustedAction.equals("fail")) {
            this.exhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
        } else if (exhaustedAction.equals("grow")) {
            this.exhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_GROW;
        } else {
            throw new ConfigurationException("Bad configuration setting for exhausted action: " + exhaustedAction);
        }
        this.samplingRate = Double.parseDouble(samplingRate);
    }

    public void configureAbandonedConfig(String logAbandoned, String removeAbandoned, String abandonedTimeout) {
        abandonedConfig.setLogAbandoned(Boolean.parseBoolean(logAbandoned));
        abandonedConfig.setRemoveAbandoned(Boolean.parseBoolean(removeAbandoned));
        abandonedConfig.setRemoveAbandonedTimeout(Integer.parseInt(abandonedTimeout));
    }

    /**
     * Creates and populates the test table (test_table) used in the load tests. The created table
     * will have 10,000 rows and 3 columns, populated with random data:
     * <ul>
     * <li>indexed (indexed integer column)</li>
     * <li>not_indexed (non-indexed integer column)</li>
     * <li>text (non-indexed varchar column)</li>
     * </ul>
     * 
     * @throws Exception
     */
    protected void makeTable()
        throws Exception {
        Class.forName(driverClass);
        Connection db = DriverManager.getConnection(connectUrl, connectUser, connectPassword);
        try {
            Statement sql = db.createStatement();
            String sqlText = "create table test_table (indexed int, text varchar(20)," + " not_indexed int)";
            sql.executeUpdate(sqlText);
            sqlText = "CREATE INDEX test1_id_index ON test_table (indexed);";
            sql.executeUpdate(sqlText);
            RandomDataGenerator randomData = new RandomDataGenerator();
            for (int i = 0; i < 10000; i++) {
                int indexed = randomData.nextInt(0, 100);
                int not_indexed = randomData.nextInt(0, 1000);
                String text = randomData.nextHexString(20);
                sqlText = "INSERT INTO test_table (indexed, text, not_indexed)" + "VALUES (" + indexed + "," + "'" +
                    text + "'," + not_indexed + ");";
                sql.executeUpdate(sqlText);
            }
            sql.close();
        } finally {
            db.close();
        }
    }

    /**
     * Add dbcp configuration to parameters loaded by super. Also set config file name.
     */
    @Override
    protected void configure()
        throws Exception {

        super.configure();

        digester.addCallMethod("configuration/database", "configureDataBase", 5);
        digester.addCallParam("configuration/database/driver", 0);
        digester.addCallParam("configuration/database/url", 1);
        digester.addCallParam("configuration/database/username", 2);
        digester.addCallParam("configuration/database/password", 3);
        digester.addCallParam("configuration/database/query-type", 4);

        digester.addCallMethod("configuration", "configureDataSource", 1);
        digester.addCallParam("configuration/datasource-type", 0);

        digester.addCallMethod("configuration/connection-factory", "configureConnectionFactory", 4);
        digester.addCallParam("configuration/connection-factory/type", 0);
        digester.addCallParam("configuration/connection-factory/auto-commit", 1);
        digester.addCallParam("configuration/connection-factory/read-only", 2);
        digester.addCallParam("configuration/connection-factory/validation-query", 3);

        digester.addCallMethod("configuration/poolable-connection-factory", "configurePoolableConnectionFactory", 3);
        digester.addCallParam("configuration/poolable-connection-factory/type", 0);
        digester.addCallParam("configuration/poolable-connection-factory/pool-prepared-statements", 1);
        digester.addCallParam("configuration/poolable-connection-factory/max-open-statements", 2);

        digester.addCallMethod("configuration/pool", "configurePool", 13);
        digester.addCallParam("configuration/pool/max-active", 0);
        digester.addCallParam("configuration/pool/max-idle", 1);
        digester.addCallParam("configuration/pool/min-idle", 2);
        digester.addCallParam("configuration/pool/max-wait", 3);
        digester.addCallParam("configuration/pool/exhausted-action", 4);
        digester.addCallParam("configuration/pool/test-on-borrow", 5);
        digester.addCallParam("configuration/pool/test-on-return", 6);
        digester.addCallParam("configuration/pool/time-between-evictions", 7);
        digester.addCallParam("configuration/pool/tests-per-eviction", 8);
        digester.addCallParam("configuration/pool/idle-timeout", 9);
        digester.addCallParam("configuration/pool/test-while-idle", 10);
        digester.addCallParam("configuration/pool/type", 11);
        digester.addCallParam("configuration/pool/sampling-rate", 12);

        digester.addCallMethod("configuration/abandoned-config", "configureAbandonedConfig", 3);
        digester.addCallParam("configuration/abandoned-config/log-abandoned", 0);
        digester.addCallParam("configuration/abandoned-config/remove-abandoned", 1);
        digester.addCallParam("configuration/abandoned-config/abandoned-timeout", 2);

        this.configFile = "config-dbcp.xml";
    }
}
