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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

//import org.apache.commons.pool.ObjectPool;
//import org.apache.commons.pool2.ObjectPool;
//import org.apache.commons.pool.KeyedObjectPool;
//import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.Statistics;

/**
 * <p>
 * Client thread that borrows and returns objects from a pool in a loop. See {@link ClientThread
 * ClientThread javadoc} for a description of how times between requests are computed. In addition
 * to request latency, pool numIdle and numActive statistics and stats on instance idle time are
 * tracked. Constructors are provided for both 1.x and version 2 simple and keyed pools.
 * </p>
 * 
 * <p>
 * Note that this class is *not* threadsafe.
 * </p>
 */
public class PoolClientThread
    extends ClientThread {

    /** Version 1.x object pool (if keyed = false) */
    private final org.apache.commons.pool.ObjectPool pool;

    /** Version 1.x keyed pool (if keyed = true) */
    private final org.apache.commons.pool.KeyedObjectPool keyedPool;

    /** Version 2 object pool (if keyed = false) */
    private final org.apache.commons.pool2.ObjectPool<Waiter> pool2;

    /** Version 2 keyed pool (if keyed = true) */
    private final org.apache.commons.pool2.KeyedObjectPool<Integer, Waiter> keyedPool2;

    /** Whether or not pool being tested is keyed */
    private boolean keyed;

    /** Randomly generated keys (if keyed = true) */
    private List<Integer> keys;

    /** Source of randomness (for keys, if keyed = true) */
    private final RandomDataGenerator randomData = new RandomDataGenerator();

    /** Statistics on numActive */
    private SummaryStatistics numActiveStats = new SummaryStatistics();

    /** Statistics on numIdle */
    private SummaryStatistics numIdleStats = new SummaryStatistics();

    /** Sampling rate for numActive, numIdle */
    private double samplingRate;

    /** Statistics on instance idle time */
    private SummaryStatistics instanceIdleTimeStats = new SummaryStatistics();

    /** Just-borrowed Waiter instance (used to grab idle time stats in cleanUp) */
    private Waiter waiter = null;

    /**
     * Create a pool client thread for a version 1.x ObjectPool.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum mean time between client requests
     * @param maxDelay maximum mean time between client requests
     * @param delayType distribution of time between client requests
     * @param rampPeriod ramp period of cycle for cyclic load
     * @param peakPeriod peak period of cycle for cyclic load
     * @param troughPeriod trough period of cycle for cyclic load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param stats Statistics container
     * @param pool ObjectPool
     * @param samplingRate proportion of requests for which numIdle and numActive will be sampled
     *        for statistical analysis
     */
    public PoolClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                            long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
                            Logger logger, Statistics stats, org.apache.commons.pool.ObjectPool pool,
                            double samplingRate) {

        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
              rampType, logger, stats);
        this.pool = pool;
        this.pool2 = null;
        this.keyedPool2 = null;
        this.keyedPool = null;
        this.keyed = false;
        this.samplingRate = samplingRate;
    }

    /**
     * Create a pool client thread for a version 1.x KeyedObjectPool.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum mean time between client requests
     * @param maxDelay maximum mean time between client requests
     * @param delayType distribution of time between client requests
     * @param rampPeriod ramp period of cycle for cyclic load
     * @param peakPeriod peak period of cycle for cyclic load
     * @param troughPeriod trough period of cycle for cyclic load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param stats Statistics container
     * @param keyedPool KeyedObjectPool
     * @param samplingRate proportion of requests for which numIdle and numActive will be sampled
     *        for statistical analysis
     */
    public PoolClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                            long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
                            Logger logger, Statistics stats, org.apache.commons.pool.KeyedObjectPool keyedPool,
                            double samplingRate) {

        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
              rampType, logger, stats);

        this.keyedPool = keyedPool;
        this.pool = null;
        this.pool2 = null;
        this.keyedPool2 = null;
        this.keyed = true;
        this.samplingRate = samplingRate;
        keys = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) { // TODO: make number of keys configurable
            keys.add(new Integer(i));
        }
    }

    /**
     * Create a pool client thread for a version 1.x ObjectPool.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum mean time between client requests
     * @param maxDelay maximum mean time between client requests
     * @param delayType distribution of time between client requests
     * @param rampPeriod ramp period of cycle for cyclic load
     * @param peakPeriod peak period of cycle for cyclic load
     * @param troughPeriod trough period of cycle for cyclic load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param stats Statistics container
     * @param pool ObjectPool
     * @param samplingRate proportion of requests for which numIdle and numActive will be sampled
     *        for statistical analysis
     */
    public PoolClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                            long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
                            Logger logger, Statistics stats, org.apache.commons.pool2.ObjectPool<Waiter> pool,
                            double samplingRate) {

        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
              rampType, logger, stats);
        this.pool2 = pool;
        this.pool = null;
        this.keyedPool2 = null;
        this.keyedPool = null;
        this.keyed = false;
        this.samplingRate = samplingRate;
    }

    /**
     * Create a pool client thread for a version 1.x KeyedObjectPool.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum mean time between client requests
     * @param maxDelay maximum mean time between client requests
     * @param delayType distribution of time between client requests
     * @param rampPeriod ramp period of cycle for cyclic load
     * @param peakPeriod peak period of cycle for cyclic load
     * @param troughPeriod trough period of cycle for cyclic load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param stats Statistics container
     * @param keyedPool KeyedObjectPool
     * @param samplingRate proportion of requests for which numIdle and numActive will be sampled
     *        for statistical analysis
     */
    public PoolClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                            long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
                            Logger logger, Statistics stats,
                            org.apache.commons.pool2.KeyedObjectPool<Integer, Waiter> keyedPool, double samplingRate) {

        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
              rampType, logger, stats);

        this.keyedPool2 = keyedPool;
        this.pool = null;
        this.pool2 = null;
        this.keyedPool = null;
        this.keyed = true;
        this.samplingRate = samplingRate;
        keys = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) { // TODO: make number of keys configurable
            keys.add(new Integer(i));
        }
    }

    /** Borrow and return */
    @Override
    public void execute()
        throws Exception {
        if (keyed) {
            Integer key = keys.get(randomData.nextInt(0, 19));
            waiter = keyedPool2 == null ? (Waiter) keyedPool.borrowObject(key) : keyedPool2.borrowObject(key);
            waiter.doWait();
            if (keyedPool2 == null) {
                keyedPool.returnObject(key, waiter);
            } else {
                keyedPool2.returnObject(key, waiter);
            }
        } else {
            waiter = pool2 == null ? (Waiter) pool.borrowObject() : pool2.borrowObject();
            waiter.doWait();
            if (pool2 == null) {
                pool.returnObject(waiter);
            } else {
                pool2.returnObject(waiter);
            }
        }
    }

    @Override
    protected void cleanUp()
        throws Exception {
        // Capture pool metrics
        if (randomData.nextUniform(0, 1) < samplingRate) {
            if (keyed) {
                numIdleStats.addValue(keyedPool2 == null ? keyedPool.getNumIdle() : keyedPool2.getNumIdle());
                numActiveStats.addValue(keyedPool2 == null ? keyedPool.getNumActive() : keyedPool2.getNumActive());
            } else {
                numIdleStats.addValue(pool2 == null ? pool.getNumIdle() : pool2.getNumIdle());
                numActiveStats.addValue(pool2 == null ? pool.getNumActive() : pool2.getNumActive());
            }
        }
        if (waiter != null) {
            instanceIdleTimeStats.addValue(waiter.getLastIdleTimeMs());
        }
        waiter = null;
    }

    @Override
    protected void finish()
        throws Exception {
        // Add metrics to stats
        stats.addStatistics(numIdleStats, Thread.currentThread().getName(), "numIdle");
        stats.addStatistics(numActiveStats, Thread.currentThread().getName(), "numActive");
        stats.addStatistics(instanceIdleTimeStats, Thread.currentThread().getName(), "instance idle time");
    }
}
