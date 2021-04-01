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
package org.apache.commons.performance;

import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class ClientThreadTest extends TestCase {
    
  protected ClientThread clientThread = null;
  protected static Logger logger = Logger.getLogger(LoadGenerator.class.getName());
  protected static Statistics stats = new Statistics();
  
  // Dummy ClientThread concrete class to instantiate in tests
  class nullClientThread extends ClientThread {
      public nullClientThread(long iterations, long minDelay, long maxDelay,
              double sigma, String delayType, long rampPeriod,
              long peakPeriod, long troughPeriod, String cycleType,
              String rampType, Logger logger,
              Statistics stats) {    
          super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod,
                  peakPeriod, troughPeriod, cycleType, rampType, logger,
                  stats);
      }
      public void execute() {}
  }
  
  public ClientThreadTest(String name) {
    super(name);
  }


  public static Test suite() {
    return new TestSuite(ClientThreadTest.class);
  }
  
  public void setUp() throws Exception {
    clientThread = new nullClientThread(
            1000, // iterations
            100,  // minDelay
            1000, // maxDelay
            100,  // sigma
            "constant", // delayType
            1000, // ramp period
            2000, // peak period
            3000, // trough period
            "oscillating", // cycle type
            "linear", // ramp type
            logger, stats);
  }

  // ======================================================
  //   computCyclicDelay tests
  // ======================================================
  
  public void testComputeCyclicDelayRamp() throws Exception {
      clientThread.setCycleState(ClientThread.RAMPING_UP);
      clientThread.setPeriodStart(1000);
      assertEquals(150, clientThread.computeCyclicDelay(1500, 100, 200), 10E-12);
      assertEquals(110, clientThread.computeCyclicDelay(1900, 100, 200), 10E-12);
      clientThread.setCycleState(ClientThread.RAMPING_DOWN);
      assertEquals(150, clientThread.computeCyclicDelay(1500, 100, 200), 10E-12);
      assertEquals(190, clientThread.computeCyclicDelay(1900, 100, 200), 10E-12);
  }
  
  public void testComputeCyclicDelayConst() throws Exception {
      clientThread.setCycleState(ClientThread.PEAK_LOAD);
      clientThread.setPeriodStart(1000);
      assertEquals(100, clientThread.computeCyclicDelay(1500, 100, 200), 10E-12);
      assertEquals(100, clientThread.computeCyclicDelay(1900, 100, 200), 10E-12);
      clientThread.setCycleState(ClientThread.TROUGH_LOAD);
      assertEquals(200, clientThread.computeCyclicDelay(1500, 100, 200), 10E-12);
      assertEquals(200, clientThread.computeCyclicDelay(1900, 100, 200), 10E-12);
  }
  
  public void testCyclicDelayRandom() throws Exception {
      clientThread.setRampType("random");
      clientThread.setCycleState(ClientThread.RAMPING_UP);
      clientThread.setPeriodStart(1000);
      clientThread.setLastMean(200);
      for (int i = 1; i < 10; i++) {
          double nextMean = clientThread.computeCyclicDelay(1500, 100, 200);
          assertTrue(nextMean <= 200 && nextMean >= 100 &&
                  nextMean <= clientThread.getLastMean());
          clientThread.setLastMean(nextMean);
      }
      clientThread.setCycleState(ClientThread.RAMPING_DOWN);
      clientThread.setPeriodStart(1000);
      clientThread.setLastMean(100);
      for (int i = 1; i < 10; i++) {
          double nextMean = clientThread.computeCyclicDelay(1500, 100, 200);
          assertTrue(nextMean <= 200 && nextMean >= 100 &&
                  nextMean >= clientThread.getLastMean());
          clientThread.setLastMean(nextMean);
      }
  }

  // ======================================================
  //   adjustState tests
  // ======================================================
  public void testAdjustStateNoChange() throws Exception {
      clientThread.setPeriodStart(1000);
      clientThread.setCycleState(ClientThread.RAMPING_UP);
      clientThread.setRampPeriod(1000);
      clientThread.adjustState(1100);
      assertEquals(ClientThread.RAMPING_UP, clientThread.getCycleState());
      clientThread.setCycleState(ClientThread.RAMPING_DOWN);
      clientThread.adjustState(1100);
      assertEquals(ClientThread.RAMPING_DOWN, clientThread.getCycleState());
      clientThread.setCycleState(ClientThread.PEAK_LOAD);
      clientThread.setPeakPeriod(1000);
      clientThread.adjustState(1100);
      assertEquals(ClientThread.PEAK_LOAD, clientThread.getCycleState());
      clientThread.setCycleState(ClientThread.TROUGH_LOAD);
      clientThread.setPeakPeriod(1000);
      clientThread.adjustState(1100);
      assertEquals(ClientThread.TROUGH_LOAD, clientThread.getCycleState());
  }
  
  public void testStateTransitions() throws Exception {
      clientThread.setPeakPeriod(1500);
      clientThread.setRampPeriod(1000);
      clientThread.setTroughPeriod(2000);
      
      // Ramping up to peak
      clientThread.setPeriodStart(1000);
      clientThread.setCycleState(ClientThread.RAMPING_UP);
      clientThread.adjustState(2100);
      assertEquals(ClientThread.PEAK_LOAD, clientThread.getCycleState());
      assertEquals(2100, clientThread.getPeriodStart());
      assertEquals((double) clientThread.getMinDelay(), clientThread.getLastMean());
      
      // Peak to ramping down
      clientThread.adjustState(3700);
      assertEquals(ClientThread.RAMPING_DOWN, clientThread.getCycleState());
      assertEquals(3700, clientThread.getPeriodStart());
      assertEquals((double) clientThread.getMinDelay(), clientThread.getLastMean());
      
      // Ramping down to trough
      clientThread.adjustState(4800);
      assertEquals(ClientThread.TROUGH_LOAD, clientThread.getCycleState());
      assertEquals(4800, clientThread.getPeriodStart());
      assertEquals((double) clientThread.getMaxDelay(), clientThread.getLastMean()); 
      
      // Trough to ramping up
      clientThread.adjustState(6900);
      assertEquals(ClientThread.RAMPING_UP, clientThread.getCycleState());
      assertEquals(6900, clientThread.getPeriodStart());
      assertEquals((double) clientThread.getMaxDelay(), clientThread.getLastMean());   
  }
  
  //=======================================================
  // Lifecycle tests
  //=======================================================
  
  public void testLifeCycle() {
      TesterClientThread testerThread = new TesterClientThread(
              10, // iterations
              100,  // minDelay
              1000, // maxDelay
              100,  // sigma
              "constant", // delayType
              1000, // ramp period
              2000, // peak period
              3000, // trough period
              "oscillating", // cycle type
              "linear", // ramp type
              logger,
              stats, 
              0,          // min service delay
              100,        // max service delay
              50,         // mean service delay
              10,         // std dev of service delay
              "uniform");   // service delay distribution
       assertFalse(testerThread.isInitialized());
       testerThread.run();
       assertEquals(10, testerThread.getSetups());
       assertEquals(10, testerThread.getTearDowns());
       assertTrue(testerThread.isFinalized());
       assertTrue(testerThread.isInitialized());
  }
  
  public void testLifeCycleThrowing() {
      TesterClientThread testerThread = new TesterClientThread(
              10, // iterations
              100,  // minDelay
              1000, // maxDelay
              100,  // sigma
              "constant", // delayType
              1000, // ramp period
              2000, // peak period
              3000, // trough period
              "oscillating", // cycle type
              "linear", // ramp type
              logger,
              stats, 
              0,          // min service delay
              100,        // max service delay
              50,         // mean service delay
              10,         // std dev of service delay
              "uniform");   // service delay distribution
       assertFalse(testerThread.isInitialized());
       testerThread.setHurling(true);
       testerThread.run();
       assertEquals(10, testerThread.getSetups());
       assertEquals(10, testerThread.getTearDowns());
       assertTrue(testerThread.isFinalized());
       assertTrue(testerThread.isInitialized());
  }
  
}
