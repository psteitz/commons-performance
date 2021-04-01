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

/**
 * Test ClientThread 
 */
public class TesterClientThread extends ClientThread {
    
    /** 
     *  Distribution parameters for simulated service latency.
     *  To configure constant delay - i.e., the same latency each time,
     *  supply serviceDelayType = "constant" and meanServiceDelay = the
     *  desired delay to the constructor.
     */
    private final long minServiceDelay;
    private final long maxServiceDelay;
    private final double meanServiceDelay;
    private final double sigmaServiceDelay;
    private final String serviceDelayType;
    
    /** 
     * Lifecycle events trackers
     */
    private boolean initialized = false;
    private boolean finalized = false;
    private long setups = 0;
    private long tearDowns = 0;
    
    /** to hurl or not to hurl  */
    private boolean hurling = false;
    
    public boolean isHurling() {
        return hurling;
    }

    public void setHurling(boolean hurling) {
        this.hurling = hurling;
    }

    /** Executed once at the beginning of the run */
    protected void init() throws Exception {
        initialized = true;
    }
    
    /** Executed at the beginning of each iteration */
    protected void setUp() throws Exception {
        setups++;
    }
    
    /** Executed in finally block of iteration try-catch */
    protected void cleanUp() throws Exception {
        tearDowns++;
    }
    
    /** Executed once after the run finishes */
    protected void finish() throws Exception {
        finalized = true;
    }
    
   public boolean isInitialized() {
        return initialized;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public long getSetups() {
        return setups;
    }

    public long getTearDowns() {
        return tearDowns;
    }

    public TesterClientThread(long iterations, long minDelay, long maxDelay,
            double sigma, String delayType, long rampPeriod, long peakPeriod,
            long troughPeriod, String cycleType, String rampType,
            Logger logger, Statistics stats, long minServiceDelay,
            long maxServiceDelay, double meanServiceDelay,
            double sigmaServiceDelay, String serviceDelayType) {
    
        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod,
                peakPeriod, troughPeriod, cycleType, rampType, logger,
                stats);
        this.minServiceDelay = minServiceDelay;
        this.maxServiceDelay = maxServiceDelay;
        this.meanServiceDelay = meanServiceDelay;
        this.sigmaServiceDelay = sigmaServiceDelay;
        this.serviceDelayType = serviceDelayType;
    }
    
    /** 
     * Simulate server latency using service latency parameters
     */
   public void execute() throws Exception {
       if (hurling) {
           throw new RuntimeException("Bang!");
       }
       if (meanServiceDelay <= 0) {
           return;
       }
       if (serviceDelayType.equals("constant")) {
           Thread.sleep(Math.round(meanServiceDelay));
       } else if (serviceDelayType.equals("gaussian")) {
           Thread.sleep(Math.round(randomData.nextGaussian(
                   meanServiceDelay, sigmaServiceDelay))); 
       } else if (serviceDelayType.equals("poisson")) {
           Thread.sleep(Math.round(
                   randomData.nextPoisson(meanServiceDelay)));
       }
       else if (serviceDelayType.equals("uniform")) {
           Thread.sleep(Math.round(
                   randomData.nextUniform(minServiceDelay, maxServiceDelay)));
       }
        
    }
}
