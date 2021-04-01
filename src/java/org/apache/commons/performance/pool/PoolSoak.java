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

package org.apache.commons.performance.pool;

import java.util.logging.Logger;

import org.apache.commons.dbcp.AbandonedConfig;
import org.apache.commons.dbcp.AbandonedObjectPool;
import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.ConfigurationException;
import org.apache.commons.performance.LoadGenerator;
import org.apache.commons.performance.Statistics;
import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.apache.commons.pool.impl.StackKeyedObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * Configurable load / performance tester for commons pool. Uses Commons Digester to parse and load
 * configuration and spawns PoolClientThread instances to generate load and gather statistics.
 * 
 */
public class PoolSoak
    extends LoadGenerator {

    // Pool instances
    private org.apache.commons.pool.impl.GenericObjectPool genericObjectPool;
    private org.apache.commons.pool.impl.GenericKeyedObjectPool genericKeyedObjectPool;
    private org.apache.commons.pool2.impl.GenericObjectPool<Waiter> genericObjectPool2;
    private org.apache.commons.pool2.impl.GenericKeyedObjectPool<Integer, Waiter> genericKeyedObjectPool2;
    private StackObjectPool stackObjectPool;
    private SoftReferenceObjectPool softReferenceObjectPool;
    private StackKeyedObjectPool stackKeyedObjectPool;

    // Pool properties
    private String poolType;
    private int maxActive; // maxActive for GOP, maxTotal for GKOP
    private int maxActivePerKey; // maxActive for GKOP
    private int maxIdle;
    private int minIdle;
    private long maxWait;
    private byte exhaustedAction;
    private boolean testOnBorrow;
    private boolean testOnReturn;
    private long timeBetweenEvictions;
    private int testsPerEviction;
    private long idleTimeout;
    private boolean testWhileIdle;
    private AbandonedConfig abandonedConfig = new AbandonedConfig();
    private boolean lifo;
    private double samplingRate;

    // WaiterFactory properties
    private long activateLatency;
    private long destroyLatency;
    private long makeLatency;
    private long passivateLatency;
    private long validateLatency;
    private long waiterLatency;
    private double passivateInvalidationProbability;

    /**
     * Add pool configuration to parameters loaded by super. Also set config file name.
     */
    @Override
    protected void configure()
        throws Exception {
        super.configure();
        digester.addCallMethod("configuration/factory", "configureFactory", 7);
        digester.addCallParam("configuration/factory/activate-latency", 0);
        digester.addCallParam("configuration/factory/destroy-latency", 1);
        digester.addCallParam("configuration/factory/make-latency", 2);
        digester.addCallParam("configuration/factory/passivate-latency", 3);
        digester.addCallParam("configuration/factory/validate-latency", 4);
        digester.addCallParam("configuration/factory/waiter-latency", 5);
        digester.addCallParam("configuration/factory/passivate-invalidation-probability", 6);
        digester.addCallMethod("configuration/pool", "configurePool", 15);
        digester.addCallParam("configuration/pool/max-active", 0);
        digester.addCallParam("configuration/pool/max-active-per-key", 1);
        digester.addCallParam("configuration/pool/max-idle", 2);
        digester.addCallParam("configuration/pool/min-idle", 3);
        digester.addCallParam("configuration/pool/max-wait", 4);
        digester.addCallParam("configuration/pool/exhausted-action", 5);
        digester.addCallParam("configuration/pool/test-on-borrow", 6);
        digester.addCallParam("configuration/pool/test-on-return", 7);
        digester.addCallParam("configuration/pool/time-between-evictions", 8);
        digester.addCallParam("configuration/pool/tests-per-eviction", 9);
        digester.addCallParam("configuration/pool/idle-timeout", 10);
        digester.addCallParam("configuration/pool/test-while-idle", 11);
        digester.addCallParam("configuration/pool/lifo", 12);
        digester.addCallParam("configuration/pool/type", 13);
        digester.addCallParam("configuration/pool/sampling-rate", 14);
        digester.addCallMethod("configuration/abandoned-config", "configureAbandonedConfig", 3);
        digester.addCallParam("configuration/abandoned-config/log-abandoned", 0);
        digester.addCallParam("configuration/abandoned-config/remove-abandoned", 1);
        digester.addCallParam("configuration/abandoned-config/abandoned-timeout", 2);

        this.configFile = "config-pool.xml";

    }

    /**
     * Create object pool and factory
     */
    @Override
    protected void init()
        throws Exception {
        // Create factory
        WaiterFactory factory = new WaiterFactory(activateLatency, destroyLatency, makeLatency, passivateLatency,
                                                  validateLatency, waiterLatency, maxActive, maxActivePerKey,
                                                  passivateInvalidationProbability);

        WaiterFactory2 factory2 = new WaiterFactory2(activateLatency, destroyLatency, makeLatency, passivateLatency,
                                                     validateLatency, waiterLatency, maxActive, maxActivePerKey,
                                                     passivateInvalidationProbability);

        // Create object pool
        if (poolType.equals("GenericObjectPool")) {
            genericObjectPool = new org.apache.commons.pool.impl.GenericObjectPool(factory);
            genericObjectPool.setMaxActive(maxActive);
            genericObjectPool.setWhenExhaustedAction(exhaustedAction);
            genericObjectPool.setMaxWait(maxWait);
            genericObjectPool.setMaxIdle(maxIdle);
            genericObjectPool.setMinIdle(minIdle);
            genericObjectPool.setTestOnBorrow(testOnBorrow);
            genericObjectPool.setTestOnReturn(testOnReturn);
            genericObjectPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            genericObjectPool.setNumTestsPerEvictionRun(testsPerEviction);
            genericObjectPool.setMinEvictableIdleTimeMillis(idleTimeout);
            genericObjectPool.setTestWhileIdle(testWhileIdle);
            genericObjectPool.setLifo(lifo);
        } else if (poolType.equals("GenericObjectPool2")) {
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config
                .setBlockWhenExhausted(exhaustedAction == org.apache.commons.pool.impl.GenericObjectPool.WHEN_EXHAUSTED_BLOCK
                    ? true : false);
            config.setMaxIdle(maxIdle);
            config.setMinIdle(minIdle);
            config.setMaxWaitMillis(maxWait);
            config.setTestOnBorrow(testOnBorrow);
            config.setTestWhileIdle(testWhileIdle);
            config.setTestOnReturn(testOnReturn);
            config.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            config.setNumTestsPerEvictionRun(testsPerEviction);
            config.setMinEvictableIdleTimeMillis(idleTimeout);
            config.setLifo(lifo);
            config.setMaxTotal(maxActive);
            genericObjectPool2 = new org.apache.commons.pool2.impl.GenericObjectPool<Waiter>(factory2, config);
        } else if (poolType.equals("AbandonedObjectPool")) {
            genericObjectPool = new AbandonedObjectPool(factory, abandonedConfig);
            genericObjectPool.setMaxActive(maxActive);
            genericObjectPool.setWhenExhaustedAction(exhaustedAction);
            genericObjectPool.setMaxWait(maxWait);
            genericObjectPool.setMaxIdle(maxIdle);
            genericObjectPool.setMinIdle(minIdle);
            genericObjectPool.setTestOnBorrow(testOnBorrow);
            genericObjectPool.setTestOnReturn(testOnReturn);
            genericObjectPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            genericObjectPool.setNumTestsPerEvictionRun(testsPerEviction);
            genericObjectPool.setMinEvictableIdleTimeMillis(idleTimeout);
            genericObjectPool.setTestWhileIdle(testWhileIdle);
            genericObjectPool.setLifo(lifo);
        } else if (poolType.equals("GenericKeyedObjectPool")) {
            genericKeyedObjectPool = new org.apache.commons.pool.impl.GenericKeyedObjectPool(factory);
            genericKeyedObjectPool.setMaxActive(maxActivePerKey);
            genericKeyedObjectPool.setMaxTotal(maxActive);
            genericKeyedObjectPool.setWhenExhaustedAction(exhaustedAction);
            genericKeyedObjectPool.setMaxWait(maxWait);
            genericKeyedObjectPool.setMaxIdle(maxIdle);
            genericKeyedObjectPool.setMinIdle(minIdle);
            genericKeyedObjectPool.setTestOnBorrow(testOnBorrow);
            genericKeyedObjectPool.setTestOnReturn(testOnReturn);
            genericKeyedObjectPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            genericKeyedObjectPool.setNumTestsPerEvictionRun(testsPerEviction);
            genericKeyedObjectPool.setMinEvictableIdleTimeMillis(idleTimeout);
            genericKeyedObjectPool.setTestWhileIdle(testWhileIdle);
            genericKeyedObjectPool.setLifo(lifo);
        } else if (poolType.equals("GenericKeyedObjectPool2")) {
            GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
            config.setMaxTotal(maxActive);
            config.setMaxTotalPerKey(maxActivePerKey);
            config
                .setBlockWhenExhausted(exhaustedAction == org.apache.commons.pool.impl.GenericObjectPool.WHEN_EXHAUSTED_BLOCK
                    ? true : false);
            config.setMaxWaitMillis(maxWait);
            config.setMaxIdlePerKey(maxIdle);
            config.setMinIdlePerKey(minIdle);
            config.setTestOnBorrow(testOnBorrow);
            config.setTestOnReturn(testOnReturn);
            config.setTimeBetweenEvictionRunsMillis(timeBetweenEvictions);
            config.setNumTestsPerEvictionRun(testsPerEviction);
            config.setMinEvictableIdleTimeMillis(idleTimeout);
            config.setTestWhileIdle(testWhileIdle);
            config.setLifo(lifo);
            genericKeyedObjectPool2 = new org.apache.commons.pool2.impl.GenericKeyedObjectPool<Integer, Waiter>(
                                                                                                                factory2,
                                                                                                                config);
        } else if (poolType.equals("StackObjectPool")) {
            stackObjectPool = new StackObjectPool(factory);
        } else if (poolType.equals("SoftReferenceObjectPool")) {
            softReferenceObjectPool = new SoftReferenceObjectPool(factory);
        } else if (poolType.equals("StackKeyedObjectPool")) {
            stackKeyedObjectPool = new StackKeyedObjectPool(factory);
        } else {
            throw new ConfigurationException("invalid pool type configuration: " + poolType);
        }

        logger.info(displayConfig());
    }

    /**
     * Close object pool
     */
    @Override
    protected void cleanUp()
        throws Exception {
        if (genericObjectPool != null) {
            genericObjectPool.close();
        }
        if (genericKeyedObjectPool != null) {
            genericKeyedObjectPool.close();
        }
        if (genericObjectPool2 != null) {
            genericObjectPool2.close();
        }
        if (genericKeyedObjectPool2 != null) {
            genericKeyedObjectPool2.close();
        }
        if (stackObjectPool != null) {
            stackObjectPool.close();
        }
        if (softReferenceObjectPool != null) {
            softReferenceObjectPool.close();
        }
        if (stackKeyedObjectPool != null) {
            stackKeyedObjectPool.close();
        }
    }

    /**
     * Create and return a PoolClientThread
     */
    @Override
    protected ClientThread makeClientThread(long iterations, long minDelay, long maxDelay, double sigma,
        String delayType, long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
        Logger logger, Statistics stats) {
        if (poolType.equals("GenericObjectPool")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, genericObjectPool,
                                        samplingRate);
        }
        if (poolType.equals("GenericKeyedObjectPool")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, genericKeyedObjectPool,
                                        samplingRate);
        }
        if (poolType.equals("GenericObjectPool2")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, genericObjectPool2,
                                        samplingRate);
        }
        if (poolType.equals("GenericKeyedObjectPool2")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, genericKeyedObjectPool2,
                                        samplingRate);
        }
        if (poolType.equals("StackKeyedObjectPool")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, stackKeyedObjectPool,
                                        samplingRate);
        }
        if (poolType.equals("StackObjectPool")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, stackObjectPool, samplingRate);
        }
        if (poolType.equals("SoftReferenceObjectPool")) {
            return new PoolClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats, softReferenceObjectPool,
                                        samplingRate);
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Configuration methods specific to this LoadGenerator invoked by Digester
    // when superclass execute calls digester.parse.
    // ------------------------------------------------------------------------
    public void configureFactory(String activateLatency, String destroyLatency, String makeLatency,
        String passivateLatency, String validateLatency, String waiterLatency, String passivateInvalidationProbability) {

        this.activateLatency = Long.parseLong(activateLatency);
        this.destroyLatency = Long.parseLong(destroyLatency);
        this.makeLatency = Long.parseLong(makeLatency);
        this.passivateLatency = Long.parseLong(passivateLatency);
        this.validateLatency = Long.parseLong(validateLatency);
        this.waiterLatency = Long.parseLong(waiterLatency);
        this.passivateInvalidationProbability = Double.parseDouble(passivateInvalidationProbability);
    }

    public void configurePool(String maxActive, String maxActivePerKey, String maxIdle, String minIdle, String maxWait,
        String exhaustedAction, String testOnBorrow, String testOnReturn, String timeBetweenEvictions,
        String testsPerEviction, String idleTimeout, String testWhileIdle, String lifo, String type, String samplingRate)
        throws ConfigurationException {
        this.maxActive = Integer.parseInt(maxActive);
        this.maxActivePerKey = Integer.parseInt(maxActivePerKey);
        this.maxIdle = Integer.parseInt(maxIdle);
        this.minIdle = Integer.parseInt(minIdle);
        this.maxWait = Long.parseLong(maxWait);
        this.testOnBorrow = Boolean.parseBoolean(testOnBorrow);
        this.testOnReturn = Boolean.parseBoolean(testOnReturn);
        this.timeBetweenEvictions = Long.parseLong(timeBetweenEvictions);
        this.testsPerEviction = Integer.parseInt(testsPerEviction);
        this.idleTimeout = Long.parseLong(idleTimeout);
        this.testWhileIdle = Boolean.parseBoolean(testWhileIdle);
        this.lifo = Boolean.parseBoolean(lifo);
        this.poolType = type;
        if (exhaustedAction.equals("block")) {
            this.exhaustedAction = org.apache.commons.pool.impl.GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
        } else if (exhaustedAction.equals("fail")) {
            this.exhaustedAction = org.apache.commons.pool.impl.GenericObjectPool.WHEN_EXHAUSTED_FAIL;
        } else if (exhaustedAction.equals("grow")) {
            this.exhaustedAction = org.apache.commons.pool.impl.GenericObjectPool.WHEN_EXHAUSTED_GROW;
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

    // Pool getters for unit tests
    protected org.apache.commons.pool.impl.GenericObjectPool getGenericObjectPool() {
        return genericObjectPool;
    }

    protected org.apache.commons.pool.impl.GenericKeyedObjectPool getGenericKeyedObjectPool() {
        return genericKeyedObjectPool;
    }

    public String displayConfig() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("***********************************\n");
        buffer.append("Initialized pool with properties:\n");
        buffer.append("poolType: ");
        buffer.append(poolType);
        buffer.append("\n");
        buffer.append("exhaustedAction: ");
        buffer.append(exhaustedAction);
        buffer.append("\n");
        buffer.append("maxActive: ");
        buffer.append(maxActive);
        buffer.append("\n");
        buffer.append("maxActivePerKey: ");
        buffer.append(maxActivePerKey);
        buffer.append("\n");
        buffer.append("maxIdle: ");
        buffer.append(maxIdle);
        buffer.append("\n");
        buffer.append("minIdle: ");
        buffer.append(minIdle);
        buffer.append("\n");
        buffer.append("testOnBorrow: ");
        buffer.append(testOnBorrow);
        buffer.append("\n");
        buffer.append("testWhileIdle: ");
        buffer.append(testWhileIdle);
        buffer.append("\n");
        buffer.append("timeBetweenEvictions: ");
        buffer.append(timeBetweenEvictions);
        buffer.append("\n");
        buffer.append("testsPerEviction: ");
        buffer.append(testsPerEviction);
        buffer.append("\n");
        buffer.append("idleTimeout: ");
        buffer.append(idleTimeout);
        buffer.append("\n");
        buffer.append("lifo: ");
        buffer.append(lifo);
        buffer.append("\n");
        buffer.append("abandonedConfig:\n");
        buffer.append("  logAbandoned: ");
        buffer.append(abandonedConfig.getLogAbandoned());
        buffer.append("\n");
        buffer.append("  removeAbandoned: ");
        buffer.append(abandonedConfig.getRemoveAbandoned());
        buffer.append("\n");
        buffer.append("  abandonedTimeout: ");
        buffer.append(abandonedConfig.getRemoveAbandonedTimeout());
        buffer.append("\n");
        buffer.append("***********************************\n");
        return buffer.toString();
    }

}
