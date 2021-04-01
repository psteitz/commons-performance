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

import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * Version 2 object factory with configurable latencies for object lifecycle methods.
 */
public class WaiterFactory2
    extends WaiterFactoryBase
    implements PooledObjectFactory<Waiter>, KeyedPooledObjectFactory<Integer, Waiter> {

    public WaiterFactory2(long activateLatency, long destroyLatency, long makeLatency, long passivateLatency,
                          long validateLatency, long waiterLatency, long maxActive, long maxActivePerKey,
                          double passivateInvalidationProbability) {
        super(activateLatency, destroyLatency, makeLatency, passivateLatency, validateLatency, waiterLatency,
              maxActive, maxActivePerKey, passivateInvalidationProbability);
    }

    public WaiterFactory2(long activateLatency, long destroyLatency, long makeLatency, long passivateLatency,
                          long validateLatency, long waiterLatency) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency, validateLatency, waiterLatency,
             Long.MAX_VALUE, Long.MAX_VALUE, 0);
    }

    public WaiterFactory2(long activateLatency, long destroyLatency, long makeLatency, long passivateLatency,
                          long validateLatency, long waiterLatency, long maxActive) {
        this(activateLatency, destroyLatency, makeLatency, passivateLatency, validateLatency, waiterLatency, maxActive,
             Long.MAX_VALUE, 0);
    }

    @Override
    public void activateObject(PooledObject<Waiter> waiter)
        throws Exception {
        activate(waiter.getObject());
    }

    @Override
    public void destroyObject(PooledObject<Waiter> waiter)
        throws Exception {
        destroy(waiter.getObject());
    }

    @Override
    public PooledObject<Waiter> makeObject()
        throws Exception {
        return new DefaultPooledObject<Waiter>(make());
    }

    @Override
    public void passivateObject(PooledObject<Waiter> waiter)
        throws Exception {
        passivate(waiter.getObject());
    }

    @Override
    public boolean validateObject(PooledObject<Waiter> waiter) {
        return validate(waiter.getObject());
    }

    @Override
    public void activateObject(Integer key, PooledObject<Waiter> waiter)
        throws Exception {
        activate(waiter.getObject());
    }

    @Override
    public void destroyObject(Integer key, PooledObject<Waiter> waiter)
        throws Exception {
        destroy(waiter.getObject());
        synchronized (this) {
            activeCounts.get(key).getAndDecrement();
        }
    }

    @Override
    public PooledObject<Waiter> makeObject(Integer key)
        throws Exception {
        return new DefaultPooledObject<Waiter>(make(key));
    }

    @Override
    public void passivateObject(Integer key, PooledObject<Waiter> waiter)
        throws Exception {
        passivateObject(waiter);
    }

    @Override
    public boolean validateObject(Integer key, PooledObject<Waiter> waiter) {
        return validateObject(waiter);
    }
}
