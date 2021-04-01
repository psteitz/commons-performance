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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.KeyedPoolableObjectFactory;

/**
 * Version 1.x object factory with configurable latencies for object lifecycle methods.
 */
public class WaiterFactory extends WaiterFactoryBase implements PoolableObjectFactory, KeyedPoolableObjectFactory {

    public WaiterFactory(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency,long maxActive, long maxActivePerKey,
            double passivateInvalidationProbability) {
        super(activateLatency, destroyLatency, makeLatency,
                passivateLatency, validateLatency, waiterLatency, maxActive,
                maxActivePerKey, passivateInvalidationProbability);
    }
    
    public WaiterFactory(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency,
                validateLatency, waiterLatency, Long.MAX_VALUE, Long.MAX_VALUE, 0);
    }
    
    public WaiterFactory(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency,long maxActive) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency,
                validateLatency, waiterLatency, maxActive, Long.MAX_VALUE, 0);
    }

    public void activateObject(Object obj) throws Exception {
        activate((Waiter) obj);
    }

    public void destroyObject(Object obj) throws Exception {
        destroy((Waiter) obj);
    }
    
    public Object makeObject() throws Exception {
        return make();
    }

    public void passivateObject(Object obj) throws Exception {
        passivate((Waiter) obj);
    }
    

    public boolean validateObject(Object obj) {
        return validate((Waiter) obj);
    }

    // KeyedPoolableObjectFactory methods
    
    public void activateObject(Object key, Object obj) throws Exception {
        activate((Waiter) obj);
    }

    public void destroyObject(Object key, Object obj) throws Exception {
        destroy((Waiter) obj);
        synchronized (this) {
            ((AtomicInteger) activeCounts.get(key)).getAndDecrement(); 
        }
    }

    public Object makeObject(Object key) throws Exception {
        return makeObject((Integer) key);
    }
    
    public Object makeObject(Integer key) throws Exception {
        return make(key);
    }

    public void passivateObject(Object key, Object obj) throws Exception {
        passivateObject(obj);
    }
    
    public boolean validateObject(Object key, Object obj) {
        return validateObject(obj);
    }

}
