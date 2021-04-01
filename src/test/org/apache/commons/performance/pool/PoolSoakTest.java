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

import java.io.InputStream;
import java.io.IOException;
import org.apache.commons.dbcp.AbandonedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class PoolSoakTest extends TestCase {
    

  public PoolSoakTest(String name) {
    super(name);
  }


  public static Test suite() {
    return new TestSuite(PoolSoakTest.class);
  }
  
  protected PoolSoak poolSoak = null;
  
  public void setUp() throws Exception {
     poolSoak = new PoolSoak();
     poolSoak.configure();
  }
  
  public void testGenericObjectPoolConfig() throws Exception {
    /** Contents of config file
    <max-active>15</max-active>
    <max-idle>15</max-idle>
    <min-idle>0</min-idle>
    <max-wait>-1</max-wait>
    <!-- block, fail, or grow -->
    <exhausted-action>block</exhausted-action>
    <test-on-borrow>false</test-on-borrow>
    <test-on-return>false</test-on-return>
    <time-between-evictions>-1</time-between-evictions>
    <tests-per-eviction>3</tests-per-eviction>
    <idle-timeout>-1</idle-timeout>
    <test-while-idle>false</test-while-idle>
    **/
     poolSoak.getDigester().parse(getInputStream("config-pool.xml"));
     poolSoak.init();
     GenericObjectPool pool = poolSoak.getGenericObjectPool();
     assertEquals(15, pool.getMaxActive());
     assertEquals(15, pool.getMaxIdle());
     assertEquals(10, pool.getMinIdle());
     assertEquals(-1, pool.getMaxWait());
     assertEquals(GenericObjectPool.WHEN_EXHAUSTED_BLOCK, 
             pool.getWhenExhaustedAction());
     assertEquals(false, pool.getTestOnBorrow());
     assertEquals(false, pool.getTestOnReturn());
     assertEquals(-1, pool.getTimeBetweenEvictionRunsMillis());
     assertEquals(3, pool.getNumTestsPerEvictionRun());
     assertEquals(-1, pool.getMinEvictableIdleTimeMillis());
     assertEquals(false, pool.getTestWhileIdle());  
  } 
  
  public void testAbandonedObjectPoolConfig() throws Exception {
   /* 
   <pool>
    <!-- GenericObjectPool or AbandonedObjectPool -->
    <type>AbandonedObjectPool</type>
    <max-active>15</max-active>
    <max-idle>-1</max-idle>
    <min-idle>0</min-idle>
    <max-wait>-1</max-wait>
    <!-- block, fail, or grow -->
    <exhausted-action>grow</exhausted-action>
    <test-on-borrow>true</test-on-borrow>
    <test-on-return>false</test-on-return>
    <time-between-evictions>-1</time-between-evictions>
    <tests-per-eviction>3</tests-per-eviction>
    <idle-timeout>-1</idle-timeout>
    <test-while-idle>true</test-while-idle>
  </pool>
  
  <!-- Ignored unless pool type is AbandonedObjectPool -->
  <abandoned-config>
    <log-abandoned>true</log-abandoned>
    <remove-abandoned>false</remove-abandoned>
    <abandoned-timeout>50000</abandoned-timeout>
  </abandoned-config> 
  */
      poolSoak.getDigester().parse(getInputStream("config-abandoned.xml"));
      poolSoak.init();
      AbandonedObjectPool pool = (AbandonedObjectPool) poolSoak.getGenericObjectPool();
      assertEquals(15, pool.getMaxActive());
      assertEquals(-1, pool.getMaxIdle());
      assertEquals(0, pool.getMinIdle());
      assertEquals(-1, pool.getMaxWait());
      assertEquals(GenericObjectPool.WHEN_EXHAUSTED_GROW, 
              pool.getWhenExhaustedAction());
      assertEquals(true, pool.getTestOnBorrow());
      assertEquals(false, pool.getTestOnReturn());
      assertEquals(-1, pool.getTimeBetweenEvictionRunsMillis());
      assertEquals(3, pool.getNumTestsPerEvictionRun());
      assertEquals(-1, pool.getMinEvictableIdleTimeMillis());
      assertEquals(true, pool.getTestWhileIdle());      
  }
  
  /**
   * Return an appropriate InputStream for the specified test file (which
   * must be inside our current package).
   * 
   * Borrowed from Commons Digester RuleTestCase.
   *
   * @param name Name of the test file we want
   * @exception IOException if an input/output error occurs
   */
  protected InputStream getInputStream(String name) throws IOException {

      return (this.getClass().getResourceAsStream
              ("/org/apache/commons/performance/pool/" + name));
  }
  
}
