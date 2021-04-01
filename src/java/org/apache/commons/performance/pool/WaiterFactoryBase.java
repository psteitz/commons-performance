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

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base for version 1.x and 2 Waiter factories
 */
public class WaiterFactoryBase  {
    
    // TODO: implement protected getters so these can be stochastic
    /** Latency of activateObject */
    private final long activateLatency;
    
    /** Latency of destroyObject */
    private final long destroyLatency;
    
    /** Latency of makeObject */
    private final long makeLatency;
    
    /** Latency of passivateObject */
    private final long passivateLatency;
    
    /** Latency of validateObject */
    private final long validateLatency;
    
    /** Latency of doWait for Waiter instances created by this factory */
    private final long waiterLatency;
    
    /** Probability that passivation will invalidate Waiter instances */
    private final double passivateInvalidationProbability;
    
    /** Count of (makes - destroys) since last reset */
    private long activeCount = 0;
    
    /** Count of (makes - destroys) per key since last reset */
    protected Map<Integer, AtomicInteger> activeCounts = 
        new HashMap<Integer, AtomicInteger>();
    
    /** Maximum of (makes - destroys) - if exceeded IllegalStateException */
    private final long maxActive;
    
    /** Maximum of (makes - destroys) per key */
    private final long maxActivePerKey;
    
    protected static final Logger logger = 
        Logger.getLogger(WaiterFactoryBase.class.getName());

    public WaiterFactoryBase(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency,long maxActive, long maxActivePerKey,
            double passivateInvalidationProbability) {
        this.activateLatency = activateLatency;
        this.destroyLatency = destroyLatency;
        this.makeLatency = makeLatency;
        this.passivateLatency = passivateLatency;
        this.validateLatency = validateLatency;
        this.waiterLatency = waiterLatency;
        this.maxActive = maxActive;
        this.maxActivePerKey = maxActivePerKey;
        this.passivateInvalidationProbability = passivateInvalidationProbability;
    }
    
    public WaiterFactoryBase(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency,
                validateLatency, waiterLatency, Long.MAX_VALUE, Long.MAX_VALUE, 0);
    }
    
    public WaiterFactoryBase(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency,long maxActive) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency,
                validateLatency, waiterLatency, maxActive, Long.MAX_VALUE, 0);
    }
    
    protected void activate(Waiter waiter) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("activate");
        }
        doWait(activateLatency);
        waiter.setActive(true);
    }
    
    protected void destroy(Waiter waiter) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("destroy");
        }
        doWait(destroyLatency);
        waiter.setValid(false);
        waiter.setActive(false);
        // Decrement *after* destroy 
        synchronized (this) {
            activeCount--;
        }
    }
    
    public Waiter make() throws Exception {
        // Increment and test *before* make
        synchronized (this) {
            if (activeCount >= maxActive) {
                throw new IllegalStateException("Too many active instances: " +
                activeCount + " in circulation with maxActive = " + maxActive);
            } else {
                activeCount++;
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("makeObject");
        }
        doWait(makeLatency);
        return new Waiter(false, true, waiterLatency);
    }

    protected void passivate(Waiter waiter) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("passivate");
        }
        waiter.setActive(false);
        doWait(passivateLatency);
        if (Math.random() < passivateInvalidationProbability) {
            waiter.setValid(false);
        }
    }
   
    protected boolean validate(Waiter waiter) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("validate");
        }
        doWait(validateLatency);
        return waiter.isValid();
    }
    
    protected void doWait(long latency) {
        try {
            Thread.sleep(latency);
        } catch (InterruptedException ex) {
            // ignore
        }
    }
    
    public synchronized void reset() {
        activeCount = 0;
        if (activeCounts.isEmpty()) {
            return;
        }
        Iterator<Integer> it = activeCounts.keySet().iterator();
        while (it.hasNext()) {
            ((AtomicInteger) activeCounts.get(it.next())).getAndSet(0);
        }
    }

    /**
     * @return the maxActive
     */
    public synchronized long getMaxActive() {
        return maxActive;
    }
    
    public Waiter make(Integer key) throws Exception {
        synchronized (this) {
            AtomicInteger count = (AtomicInteger) activeCounts.get(key);
            if (count == null) {
                count = new AtomicInteger(1);
                activeCounts.put(key, count);
            } else {
                if (count.get() >= maxActivePerKey) {
                    throw new IllegalStateException("Too many active " +
                    "instances for key = " + key + ": " + count.get() + 
                    " in circulation " + "with maxActivePerKey = " + 
                    maxActivePerKey);
                } else {
                    count.incrementAndGet();
                }
            }
        }
        return make();
    }

}
