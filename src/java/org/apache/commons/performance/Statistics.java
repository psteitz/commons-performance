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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.AggregateSummaryStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

/**
 * <p>
 * Container for {@link SummaryStatistics} accumulated during {@link ClientThread} executions.
 * </p>
 * 
 * <p>
 * Maintains a HashMap of {@link SummaryStatistics} instances with a composite key of the form
 * (process,type). "Process" typically identifies the client thread and "type" identifies the metric
 * - e.g., "latency", "numActive."
 * </p>
 * 
 * <p>
 * {@link ClientThread#run()} adds one <code>SummaryStatistics</code> instance, with key = (current
 * thread id,"latency").
 * </p>
 * 
 */
public class Statistics
    implements Serializable {

    /**
     * Map of SummaryStatistics keyed on <process,type>, where process corresponds to a thread being
     * monitored and type is a user-supplied characteristic being measured and tracked. For example,
     * <thread name, "latency">.
     */
    private HashMap<StatisticsKey, SummaryStatistics> data = new HashMap<StatisticsKey, SummaryStatistics>();

    /**
     * Adds the results of the given SummaryStatistics instance under the key <process,type>
     * 
     * @param stats the SummaryStatistics whose results we are adding
     * @param process name of the associated process
     * @param type description of the associated metric
     */
    public synchronized void addStatistics(SummaryStatistics stats, String process, String type) {
        StatisticsKey key = new StatisticsKey(process, type);
        data.put(key, stats);
    }

    /**
     * Retrieves the SummaryStatistics corresponding to the given process and type, if this exists;
     * null otherwise.
     * 
     * @param process name of the associated process
     * @param type description of the associated metric
     * @return SummaryStatistics for the given <process,type>; null if there is no such element in
     *         the container
     */
    public synchronized SummaryStatistics getStatistics(String process, String type) {
        StatisticsKey key = new StatisticsKey(process, type);
        return data.get(key);
    }

    /**
     * Returns the full list of SummaryStatistics corresponding to the given <code>type</code> -
     * i.e, the list of statistics of the given type across processes. For example,
     * <code>getStatisticsByType("latency")</code> will return a list of latency summaries, one for
     * each process, assuming "latency" is the name of an accumulated metric.
     * 
     * @param type the type value to get statistics for
     * @return the List of SummaryStatistics stored under the given type
     */
    public synchronized List<SummaryStatistics> getStatisticsByType(String type) {
        ArrayList<SummaryStatistics> result = new ArrayList<SummaryStatistics>();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                result.add(data.get(key));
            }
        }
        return result;
    }

    /**
     * Returns the full list of SummaryStatistics corresponding to the given <code>process</code> -
     * i.e, the list of statistics of of different types maintained for the given process.
     * 
     * @param process the process to get statistics for
     * @return the List of SummaryStatistics for the given process
     */
    public synchronized List<SummaryStatistics> getStatisticsByProcess(String process) {
        ArrayList<SummaryStatistics> result = new ArrayList<SummaryStatistics>();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.process.equals(process)) {
                result.add(data.get(key));
            }
        }
        return result;
    }

    /**
     * <p>
     * Returns a SummaryStatistics instance describing the mean of the given metric across processes
     * - i.e., the "mean of the means", the "min of the means" etc. More precisely, the returned
     * SummaryStatistics describes the distribution of the individual process means for the given
     * metric.
     * </p>
     * 
     * <p>
     * The same results could be obtained by iterating over the result of {
     * {@link #getStatisticsByType(String)} for the given <code>type</code>, extracting the mean and
     * adding its value to a SummaryStatistics instance.
     * </p>
     * 
     * @param type the metric to get summary mean statistics for
     * @return a SummaryStatistics instance describing the process means for the given metric
     */
    public synchronized SummaryStatistics getMeanSummary(String type) {
        SummaryStatistics result = new SummaryStatistics();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                result.addValue(data.get(key).getMean());
            }
        }
        return result;
    }

    /**
     * Returns SummaryStatistics for the standard deviation of the given metric across processes.
     * 
     * @param type the metric to get summary standard deviation statistics for
     * @return a SummaryStatistics instance describing the process standard deviations for the given
     *         metric
     * @see #getMeanSummary(String)
     */
    public synchronized SummaryStatistics getStdSummary(String type) {
        SummaryStatistics result = new SummaryStatistics();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                result.addValue(data.get(key).getStandardDeviation());
            }
        }
        return result;
    }

    /**
     * Returns SummaryStatistics for the minimum of the given metric across processes.
     * 
     * @param type the metric to get summary minimum statistics for
     * @return a SummaryStatistics instance describing the process minima for the given metric
     * @see #getMeanSummary(String)
     */
    public synchronized SummaryStatistics getMinSummary(String type) {
        SummaryStatistics result = new SummaryStatistics();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                result.addValue(data.get(key).getMin());
            }
        }
        return result;
    }

    /**
     * Returns SummaryStatistics for the maximum of the given metric across processes.
     * 
     * @param type the metric to get summary maximum statistics for
     * @return a SummaryStatistics describing the process maxima for the given metric
     * @see #getMeanSummary(String)
     */
    public synchronized SummaryStatistics getMaxSummary(String type) {
        SummaryStatistics result = new SummaryStatistics();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                result.addValue(data.get(key).getMax());
            }
        }
        return result;
    }

    /**
     * Returns overall SummaryStatistics, aggregating the results for metrics of the given type
     * across processes.
     * 
     * @param type the metric to get overall statistics for
     * @return a SummaryStatistics instance summarizing the combined dataset for the given metric
     */
    public synchronized StatisticalSummaryValues getOverallSummary(String type) {
        ArrayList<SummaryStatistics> contributingStats = new ArrayList<SummaryStatistics>();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            StatisticsKey key = it.next();
            if (key.type.equals(type)) {
                contributingStats.add(data.get(key));
            }
        }
        return AggregateSummaryStatistics.aggregate(contributingStats);
    }

    /**
     * Returns the List of processes corresponding to statistics.
     * 
     * @return List of processes represented in the container
     */
    public synchronized List<String> getProcesses() {
        ArrayList<String> result = new ArrayList<String>();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            String currProcess = it.next().process;
            if (!result.contains(currProcess)) {
                result.add(currProcess);
            }
        }
        return result;
    }

    /**
     * Returns the List of types corresponding to statistics.
     * 
     * @return List of types represented in the container
     */
    public synchronized List<String> getTypes() {
        ArrayList<String> result = new ArrayList<String>();
        Iterator<StatisticsKey> it = data.keySet().iterator();
        while (it.hasNext()) {
            String currType = it.next().type;
            if (!result.contains(currType)) {
                result.add(currType);
            }
        }
        return result;
    }

    /**
     * Computes and formats display of summary statistics by type, as means of means, etc. with the
     * process as the unit of observation. Not currently displayed. TODO: Make inclusion of this
     * report configurable.
     * 
     * @return String representing summaries for each metric
     */
    public synchronized String displayProcessLevelSummary() {
        Iterator<String> metricsIterator = getTypes().iterator();
        StringBuffer buffer = new StringBuffer();
        while (metricsIterator.hasNext()) {
            String metric = metricsIterator.next();
            buffer.append("Overall statistics for the mean ");
            buffer.append(metric.toUpperCase());
            buffer.append("\n");
            buffer.append(getMeanSummary(metric).toString());
            buffer.append("********************************************\n");
            buffer.append("Overall statistics for the standard deviation ");
            buffer.append(metric.toUpperCase());
            buffer.append("\n");
            buffer.append(getStdSummary(metric).toString());
            buffer.append("********************************************\n");
            buffer.append("Overall statistics for the min ");
            buffer.append(metric.toUpperCase());
            buffer.append("\n");
            buffer.append(getMinSummary(metric).toString());
            buffer.append("********************************************\n");
            buffer.append("Overall statistics for the max ");
            buffer.append(metric.toUpperCase());
            buffer.append("\n");
            buffer.append(getMaxSummary(metric).toString());
        }
        return buffer.toString();
    }

    /**
     * Displays overall statistics for each metric, aggregating data across threads.
     * 
     * @return overall statistics report
     */
    public synchronized String displayOverallSummary() {
        Iterator<String> metricsIterator = getTypes().iterator();
        StringBuffer buffer = new StringBuffer();
        while (metricsIterator.hasNext()) {
            String metric = metricsIterator.next();
            buffer.append("********************************************\n");
            buffer.append("Overall summary statistics (all threads combined) ");
            buffer.append(metric.toUpperCase());
            buffer.append("\n");
            buffer.append(getOverallSummary(metric).toString());
            buffer.append("\n");
            buffer.append("********************************************\n");
        }
        return buffer.toString();
    }

    /**
     * Displays statistics for the given process
     * 
     * @param process the process to retrieve metrics for
     * @return String representing all currently defined statistics for the given process
     */
    public synchronized String displayProcessStatistics(String process) {
        Iterator<String> metricsIterator = getTypes().iterator();
        StringBuffer buffer = new StringBuffer();
        while (metricsIterator.hasNext()) {
            String metric = metricsIterator.next();
            buffer.append("*********************************************\n");
            buffer.append(metric.toUpperCase());
            buffer.append(" for ");
            buffer.append(process);
            buffer.append(" ");
            buffer.append(getStatistics(process, metric).toString());
            buffer.append("\n********************************************\n");
        }
        return buffer.toString();
    }

    /**
     * Composite key (<process,type>).
     */
    private static class StatisticsKey
        implements Serializable {

        public StatisticsKey(String process, String type) {
            this.process = process;
            this.type = type;
        }

        private String process = null;
        private String type = null;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StatisticsKey) || obj == null) {
                return false;
            } else {
                StatisticsKey other = (StatisticsKey) obj;
                return (other.process.equals(this.process)) && (other.type.equals(this.type));
            }
        }

        @Override
        public int hashCode() {
            return 7 + 11 * process.hashCode() + 17 * type.hashCode();
        }

        public String getType() {
            return type;
        }

        public String getProcess() {
            return process;
        }
    }

}
