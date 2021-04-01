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

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

public class LoadGeneratorTest
    extends TestCase {

    protected TestLoadGenerator generator = null;
    protected static Logger logger = Logger.getLogger(LoadGenerator.class.getName());
    protected static Statistics stats = new Statistics();

    class TestClientThread
        extends ClientThread {

        private long latency = 50;
        private double metricOne = 10d;
        private double metricTwo = 20d;
        private SummaryStatistics oneStats = new SummaryStatistics();
        private SummaryStatistics twoStats = new SummaryStatistics();
        private SummaryStatistics randomStats = new SummaryStatistics();
        private RandomDataGenerator randomData = new RandomDataGenerator();

        public TestClientThread(long iterations, long minDelay, long maxDelay, double sigma, String delayType,
                                long rampPeriod, long peakPeriod, long troughPeriod, String cycleType, String rampType,
                                Logger logger, Statistics stats) {
            super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod, troughPeriod, cycleType,
                  rampType, logger, stats);
        }

        public void setLatency(long latency) {
            this.latency = latency;
        }

        @Override
        public void execute()
            throws Exception {
            Thread.sleep(latency);
        }

        @Override
        protected void cleanUp() {
            oneStats.addValue(metricOne);
            twoStats.addValue(metricTwo);
            randomStats.addValue(randomData.nextUniform(0d, 1d));
        }

        @Override
        protected void finish() {
            stats.addStatistics(oneStats, Thread.currentThread().getName(), "one");
            stats.addStatistics(twoStats, Thread.currentThread().getName(), "two");
            stats.addStatistics(randomStats, Thread.currentThread().getName(), "random");
        }
    }

    class TestLoadGenerator
        extends LoadGenerator {

        @Override
        protected ClientThread makeClientThread(long iterations, long minDelay, long maxDelay, double sigma,
                                                String delayType, long rampPeriod, long peakPeriod, long troughPeriod,
                                                String cycleType, String rampType, Logger logger, Statistics stats) {
            return new TestClientThread(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                                        troughPeriod, cycleType, rampType, logger, stats);
        }

        @Override
        protected void parseConfigFile()
            throws Exception {
            getDigester().parse(this.getClass()
                                    .getResourceAsStream("/org/apache/commons/performance/pool/config-pool.xml"));
        }
    }

    public LoadGeneratorTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(LoadGeneratorTest.class);
    }

    @Override
    public void setUp()
        throws Exception {
        generator = new TestLoadGenerator();
    }

    public void testStatistics()
        throws Exception {
        generator.execute();
        Statistics statistics = generator.getStatistics();
        SummaryStatistics stats = null;
        stats = statistics.getMeanSummary("latency");
        assertEquals(50, stats.getMean(), 100.0);
        stats = statistics.getMeanSummary("one");
        assertEquals(10, stats.getMean(), 1.0);
        stats = statistics.getMeanSummary("two");
        assertEquals(20, stats.getMean(), 1.0);
        stats = statistics.getMeanSummary("random");
        assertEquals(0.5, stats.getMean(), 0.25);
    }

}
