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

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

/**
 * <p>
 * Base for performance / load test clients. The run method executes init, then
 * setup-execute-cleanup in a loop, gathering performance statistics, with time between executions
 * based on configuration parameters. The <code>finish</code> method is executed once at the end of
 * a run. See {@link #nextDelay()} for details on inter-arrival time computation.
 * </p>
 * 
 * <p>
 * Subclasses <strong>must</strong> implement <code>execute</code>, which is the basic client
 * request action that is executed, and timed, repeatedly. If per-request setup is required, and you
 * do not want the time associated with this setup to be included in the reported timings, implement
 * <code>setUp</code> and put the setup code there. Similarly for <code>cleanUp</code>.
 * Initialization code that needs to be executed once only, before any requests are initiated,
 * should be put into <code>init</code> and cleanup code that needs to be executed only once at the
 * end of a simulation should be put into <code>finish.</code>
 * </p>
 * 
 * <p>
 * By default, the only statistics accumulated are for the latency of the <code>execute</code>
 * method. Additional metrics can be captured and added to the {@link Statistics} for the running
 * thread.
 * </p>
 * 
 */
public abstract class ClientThread
    implements Runnable {

    // Inter-arrival time configuration parameters
    /** Minimum mean time between requests */
    private long minDelay;
    /** Maximum mean time between requests */
    private long maxDelay;
    /** Standard deviation of delay distribution */
    private double sigma;
    /** Delay type - determines how next start times are computed */
    private String delayType;
    /** Ramp length for cyclic mean delay */
    private long rampPeriod;
    /** Peak length for cyclic mean delay */
    private long peakPeriod;
    /** Trough length for cyclic mean delay */
    private long troughPeriod;
    /** Cycle type */
    private final String cycleType;
    /** Ramp type */
    private String rampType;

    /** Number of iterations */
    private final long iterations;

    // State data
    /** Start time of run */
    private long startTime;
    /** Start time of current period */
    private long periodStart;
    /** Last mean delay */
    private double lastMean;
    /** Cycle state constants */
    protected static final int RAMPING_UP = 0;
    protected static final int RAMPING_DOWN = 1;
    protected static final int PEAK_LOAD = 2;
    protected static final int TROUGH_LOAD = 3;
    /** Cycle state */
    private int cycleState = RAMPING_UP;
    /** Number of errors */
    private long numErrors = 0;
    /** Number of misses */
    private long numMisses = 0;

    /** Random data generator */
    protected RandomDataGenerator randomData = new RandomDataGenerator();
    /** Statistics container */
    protected Statistics stats;
    /** Logger shared by client threads */
    protected Logger logger;

    /**
     * Create a client thread.
     * 
     * @param iterations number of iterations
     * @param minDelay minimum mean time between client requests
     * @param maxDelay maximum mean time between client requests
     * @param sigma standard deviation of time between client requests
     * @param delayType distribution of time between client requests
     * @param rampPeriod ramp period of cycle for cyclic load
     * @param peakPeriod peak period of cycle for cyclic load
     * @param troughPeriod trough period of cycle for cyclic load
     * @param cycleType type of cycle for mean delay
     * @param rampType type of ramp (linear or random jumps)
     * @param logger common logger shared by all clients
     * @param stats Statistics instance to add results to
     */
    public ClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType, long rampPeriod,
                        long peakPeriod, long troughPeriod, String cycleType, String rampType, Logger logger,
                        Statistics stats) {
        this.iterations = iterations;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.sigma = sigma;
        this.delayType = delayType;
        this.peakPeriod = peakPeriod;
        this.rampPeriod = rampPeriod;
        this.troughPeriod = troughPeriod;
        this.cycleType = cycleType;
        this.rampType = rampType;
        this.logger = logger;
        this.stats = stats;
    }

    @Override
    public void run() {
        try {
            init();
        } catch (Exception ex) {
            logger.severe("init failed.");
            ex.printStackTrace();
            return;
        }
        long start = 0;
        startTime = System.currentTimeMillis();
        long lastStart = startTime;
        periodStart = System.currentTimeMillis();
        lastMean = maxDelay; // Ramp up, if any, starts here
        SummaryStatistics responseStats = new SummaryStatistics();
        SummaryStatistics onTimeStats = new SummaryStatistics();
        SummaryStatistics successStats = new SummaryStatistics();
        for (int i = 0; i < iterations; i++) {
            boolean onTime = true;
            boolean success = true;
            try {
                setUp();
                // Generate next inter-arrival time. If that is in the
                // past, go right away and log a miss; otherwise wait.
                final long elapsed = System.currentTimeMillis() - lastStart;
                final long nextDelay = nextDelay();
                if (elapsed > nextDelay) {
                    onTime = false;
                } else {
                    try {
                        Thread.sleep(nextDelay - elapsed);
                    } catch (InterruptedException ex) {
                        logger.info("Sleep interrupted");
                    }
                }

                // Fire the request and measure response time
                lastStart = System.currentTimeMillis();
                start = System.nanoTime();
                execute();
            } catch (Exception ex) {
                ex.printStackTrace();
                success = false;
            } finally {
                try {
                    // TODO: Keep times in ns here, convert stats for ms reporting
                    responseStats.addValue((System.nanoTime() - start) / 1000f / 1000f);
                    successStats.addValue(success ? 1 : 0);
                    onTimeStats.addValue(onTime ? 1 : 0);
                    cleanUp();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            finish();
        } catch (Exception ex) {
            logger.severe("finalize failed.");
            ex.printStackTrace();
            return;
        }

        // Use thread name as process name
        String process = Thread.currentThread().getName();

        // Record statistics
        stats.addStatistics(responseStats, process, "latency");
        stats.addStatistics(onTimeStats, process, "on time startup rate");
        stats.addStatistics(successStats, process, "successful completion rate");

        // Log accumulated statistics for this thread
        logger.info(stats.displayProcessStatistics(process));
    }

    /** Executed once at the beginning of the run */
    protected void init()
        throws Exception {
    }

    /** Executed at the beginning of each iteration */
    protected void setUp()
        throws Exception {
    }

    /** Executed in finally block of iteration try-catch */
    protected void cleanUp()
        throws Exception {
    }

    /** Executed once after the run finishes */
    protected void finish()
        throws Exception {
    }

    /**
     * Core iteration code. Timings are based on this, so keep it tight.
     */
    public abstract void execute()
        throws Exception;

    /**
     * <p>
     * Computes the next inter-arrival time (time to wait between requests) based on configured
     * values for min/max delay, delay type, cycle type, ramp type and period. Currently supports
     * constant (always returning <code>minDelay</code> delay time), Poisson and Gaussian
     * distributed random time delays, linear and random ramps, and oscillating / non-oscillating
     * cycle types.
     * </p>
     * 
     * <p>
     * <strong>loadType</strong> determines whether returned times are deterministic or random. If
     * <code>loadType</code> is not "constant", a random value with the specified distribution and
     * mean determined by the other parameters is returned. For "gaussian" <code>loadType</code>,
     * <code>sigma</code> is used as used as the standard deviation.
     * </p>
     * 
     * <p>
     * <strong>cycleType</strong> determines how the returned times vary over time. "oscillating",
     * means times ramp up and down between <code>minDelay</code> and <code>maxDelay.</code> Ramp
     * type is controlled by <code>rampType.</code> Linear <code>rampType</code> means the means
     * increase or decrease linearly over the time of the period. Random makes random jumps up or
     * down toward the next peak or trough. "None" for <code>rampType</code> under oscillating
     * <code>cycleType</code> makes the means alternate between peak (<code>minDelay</code>) and
     * trough (<code>maxDelay</code>) with no ramp between.
     * </p>
     * 
     * <p>
     * Oscillating loads cycle through RAMPING_UP, PEAK_LOAD, RAMPING_DOWN and TROUGH_LOAD states,
     * with the amount of time spent in each state determined by <code>rampPeriod</code> (time spent
     * increasing on the way up and decreasing on the way down), <code>peakPeriod</code> (time spent
     * at peak load, i.e., <code>minDelay</code> mean delay) and <code>troughPeriod</code> (time
     * spent at minimum load, i.e., <code>maxDelay</code> mean delay). All times are specified in
     * milliseconds.
     * </p>
     * 
     * <p>
     * <strong>Examples:</strong>
     * <ol>
     * 
     * <li>Given
     * 
     * <pre>
     * delayType = "constant"
     * minDelay = 250
     * maxDelay = 500
     * cycleType = "oscillating"
     * rampType = "linear" 
     * rampPeriod = 10000
     * peakPeriod = 20000
     * troughPeriod = 30000
     * </pre>
     * 
     * load will start at one request every 500 ms, which is "trough load." Load then ramps up
     * linearly over the next 10 seconds unil it reaches one request per 250 milliseconds, which is
     * "peak load." Peak load is sustained for 20 seconds and then load ramps back down, again
     * taking 10 seconds to get down to "trough load," which is sustained for 30 seconds. The cycle
     * then repeats.</li>
     * 
     * <li>
     * 
     * <pre>
     * delayType = "gaussian"
     * minDelay = 250
     * maxDelay = 500
     * cycleType = "oscillating"
     * rampType = "linear" 
     * rampPeriod = 10000
     * peakPeriod = 20000
     * troughPeriod = 30000
     * sigma = 100
     * </pre>
     * 
     * produces a load pattern similar to example 1, but in this case the computed delay value is
     * fed into a gaussian random number generator as the mean and 100 as the standard deviation -
     * i.e., <code>nextDelay</code> returns random, gaussian distributed values with means moving
     * according to the cyclic pattern in example 1.</li>
     * 
     * <li>
     * 
     * <pre>
     * delayType = "constant"
     * minDelay = 250
     * maxDelay = 500
     * cycleType = "none"
     * rampType = "linear" 
     * rampPeriod = 10000
     * </pre>
     * 
     * produces a load pattern that increases linearly from one request every 500ms to one request
     * every 250ms and then stays constant at that level until the run is over. Other parameters are
     * ignored in this case.</li>
     * 
     * <li>
     * 
     * <pre>
     * delayType = "poisson"
     * minDelay = 250
     * maxDelay = 500
     * cycleType = "none"
     * rampType = "none"
     * </pre>
     * 
     * produces inter-arrival times that are poisson distributed with mean 250ms. Note that when
     * rampType is "none," the value of <code>minDelay</code> is used as the (constant) mean delay.</li>
     * </ol>
     * 
     * @return next value for delay
     */
    protected long nextDelay()
        throws ConfigurationException {
        double targetDelay = 0;
        final double dMinDelay = minDelay;
        final double dMaxDelay = maxDelay;
        final double delayDifference = dMaxDelay - dMinDelay;
        final long currentTime = System.currentTimeMillis();
        if (cycleType.equals("none")) {
            if (rampType.equals("none") || (currentTime - startTime) > rampPeriod) { // ramped up
                targetDelay = dMinDelay;
            } else if (rampType.equals("linear")) { // single period linear
                double prop = (double) (currentTime - startTime) / (double) rampPeriod;
                targetDelay = dMaxDelay - delayDifference * prop;
            } else { // Random jumps down to delay - single period
                // TODO: govern size of jumps as in oscillating
                // Where we last were as proportion of way down to minDelay
                final double lastProp = (dMaxDelay - lastMean) / delayDifference;
                // Make a random jump toward 1 (1 = all the way down)
                final double prop = randomData.nextUniform(lastProp, 1);
                targetDelay = dMaxDelay - delayDifference * prop;
            }
        } else if (cycleType.equals("oscillating")) {
            // First change cycle state if we need to
            adjustState(currentTime);
            targetDelay = computeCyclicDelay(currentTime, dMinDelay, dMaxDelay);
        } else {
            throw new ConfigurationException("Cycle type not supported: " + cycleType);
        }

        // Remember last mean for ramp up / down
        lastMean = targetDelay;

        if (delayType.equals("constant")) {
            return Math.round(targetDelay);
        }

        // Generate and return random deviate
        if (delayType.equals("gaussian")) {
            return Math.round(randomData.nextGaussian(targetDelay, sigma));
        } else { // must be Poisson
            return randomData.nextPoisson(targetDelay);
        }
    }

    /**
     * Adjusts cycleState, periodStart and lastMean if a cycle state transition needs to happen.
     * 
     * @param currentTime current time
     */
    protected void adjustState(long currentTime) {
        long timeInPeriod = currentTime - periodStart;
        if (((cycleState == RAMPING_UP || cycleState == RAMPING_DOWN) && timeInPeriod < rampPeriod) ||
            (cycleState == PEAK_LOAD && timeInPeriod < peakPeriod) ||
            (cycleState == TROUGH_LOAD && timeInPeriod < troughPeriod)) {
            return; // No state change
        }
        switch (cycleState) {
            case RAMPING_UP:
                if (peakPeriod > 0) {
                    cycleState = PEAK_LOAD;
                } else {
                    cycleState = RAMPING_DOWN;
                }
                lastMean = minDelay;
                periodStart = currentTime;
                break;

            case RAMPING_DOWN:
                if (troughPeriod > 0) {
                    cycleState = TROUGH_LOAD;
                } else {
                    cycleState = RAMPING_UP;
                }
                lastMean = maxDelay;
                periodStart = currentTime;
                break;

            case PEAK_LOAD:
                if (rampPeriod > 0) {
                    cycleState = RAMPING_DOWN;
                    lastMean = minDelay;
                } else {
                    cycleState = TROUGH_LOAD;
                    lastMean = maxDelay;
                }
                periodStart = currentTime;
                break;

            case TROUGH_LOAD:
                if (rampPeriod > 0) {
                    cycleState = RAMPING_UP;
                    lastMean = maxDelay;
                } else {
                    cycleState = PEAK_LOAD;
                    lastMean = minDelay;
                }
                periodStart = currentTime;
                break;

            default:
                throw new IllegalStateException("Illegal cycle state: " + cycleState);
        }
    }

    protected double computeCyclicDelay(long currentTime, double min, double max) {

        // Constant load states
        if (cycleState == PEAK_LOAD) {
            return min;
        }
        if (cycleState == TROUGH_LOAD) {
            return max;
        }

        // No ramp - stay at min or max load during ramp
        if (rampType.equals("none")) { // min or max, no ramp
            if (cycleState == RAMPING_UP) {
                return max;
            } else {
                return min;
            }
        }

        // Linear ramp type and ramping up or down
        double diff = max - min;
        if (rampType.equals("linear")) {
            double prop = (double) (currentTime - periodStart) / (double) rampPeriod;
            if (cycleState == RAMPING_UP) {
                return max - diff * prop;
            } else {
                return min + diff * prop;
            }
        } else { // random jumps down, then back up
            // Where we last were as proportion of way down to minDelay
            double lastProp = (max - lastMean) / diff;
            // Where we would be if this were a linear ramp
            double linearProp = (double) (currentTime - periodStart) / (double) rampPeriod;
            // Need to govern size of jumps, otherwise "convergence"
            // can be too fast - use linear ramp as governor
            if ((cycleState == RAMPING_UP && (lastProp > linearProp)) ||
                (cycleState == RAMPING_DOWN && ((1 - lastProp) > linearProp)))
                lastProp = (cycleState == RAMPING_UP) ? linearProp : (1 - linearProp);
            double prop = 0;
            if (cycleState == RAMPING_UP) { // Random jump toward 1
                prop = randomData.nextUniform(lastProp, 1);
            } else { // Random jump toward 0
                prop = randomData.nextUniform(0, lastProp);
            }
            // Make sure sequence is monotone
            if (cycleState == RAMPING_UP) {
                return Math.min(lastMean, max - diff * prop);
            } else {
                return Math.max(lastMean, min + diff * prop);
            }
        }
    }

    public long getMinDelay() {
        return minDelay;
    }

    public long getMaxDelay() {
        return maxDelay;
    }

    public double getSigma() {
        return sigma;
    }

    public String getDelayType() {
        return delayType;
    }

    public long getRampPeriod() {
        return rampPeriod;
    }

    public long getPeakPeriod() {
        return peakPeriod;
    }

    public long getTroughPeriod() {
        return troughPeriod;
    }

    public String getCycleType() {
        return cycleType;
    }

    public String getRampType() {
        return rampType;
    }

    public long getIterations() {
        return iterations;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getPeriodStart() {
        return periodStart;
    }

    public double getLastMean() {
        return lastMean;
    }

    public int getCycleState() {
        return cycleState;
    }

    public long getNumErrors() {
        return numErrors;
    }

    public long getNumMisses() {
        return numMisses;
    }

    public Statistics getStats() {
        return stats;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setPeriodStart(long periodStart) {
        this.periodStart = periodStart;
    }

    public void setLastMean(double lastMean) {
        this.lastMean = lastMean;
    }

    public void setCycleState(int cycleState) {
        this.cycleState = cycleState;
    }

    public void setNumErrors(long numErrors) {
        this.numErrors = numErrors;
    }

    public void setNumMisses(long numMisses) {
        this.numMisses = numMisses;
    }

    public void setRampType(String rampType) {
        this.rampType = rampType;
    }

    public void setMinDelay(long minDelay) {
        this.minDelay = minDelay;
    }

    public void setMaxDelay(long maxDelay) {
        this.maxDelay = maxDelay;
    }

    public void setSigma(double sigma) {
        this.sigma = sigma;
    }

    public void setDelayType(String delayType) {
        this.delayType = delayType;
    }

    public void setRampPeriod(long rampPeriod) {
        this.rampPeriod = rampPeriod;
    }

    public void setPeakPeriod(long peakPeriod) {
        this.peakPeriod = peakPeriod;
    }

    public void setTroughPeriod(long troughPeriod) {
        this.troughPeriod = troughPeriod;
    }

}
