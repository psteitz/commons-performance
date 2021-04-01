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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class WaiterTest extends TestCase {
    

  public WaiterTest(String name) {
    super(name);
  }


  public static Test suite() {
    return new TestSuite(WaiterTest.class);
  }
  
  protected WaiterFactory factory = null;
  
  /*
   public WaiterFactory(long activateLatency, long destroyLatency,
            long makeLatency, long passivateLatency, long validateLatency,
            long waiterLatency,long maxActive)
   */
  public void setUp() throws Exception {
     factory = new WaiterFactory(0, 0, 0, 0, 0, 0, 5);  // All latencies 0, maxActive = 5;
  }
  
  public void testMaxActiveExceeded() throws Exception {
     try {
         for (int i = 0; i < 6; i++) {
            factory.makeObject();
         }
         fail("Expecting IllegalStateException");
     } catch (IllegalStateException ex) {
         // Expected
     }
  }
  
  public void testMaxActive() throws Exception {
      for (int i = 0; i < 5; i++) {
          factory.makeObject();
      }
  }
  
  public void testgetLastIdleTime() throws Exception {
      Waiter waiter = (Waiter) factory.makeObject();
      factory.passivateObject(waiter); // Start last idle time clock
      Thread.sleep(200);
      factory.passivateObject(waiter); // Should make no difference
      Thread.sleep(200);
      factory.activateObject(waiter);  // last idle time updated
      assertEquals(400, waiter.getLastIdleTimeMs(), 50);
      Thread.sleep(200);
      factory.activateObject(waiter);  // Should make no difference
      Thread.sleep(200);
      factory.passivateObject(waiter);
      Thread.sleep(400);
      factory.activateObject(waiter);  // Last idle time updated
      assertEquals(400, waiter.getLastIdleTimeMs(), 50);
      factory.passivateObject(waiter); // clock started
      assertEquals(400, waiter.getLastIdleTimeMs(), 50); // but no lastIdleTime update until reactivated
  }
  
}
