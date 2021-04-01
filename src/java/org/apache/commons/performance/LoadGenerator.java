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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.commons.digester.Digester;
 
/**
 * <p>Base class for load / performance test runners.
 * Uses Commons Digester to parse and load configuration and spawns
 * {@link ClientThread} instances to generate load and gather statistics.</p>
 * 
 * <p>Subclasses <code>must</code> implement <code>makeClientThread</code> to
 * create client thread instances to be kicked off by <code>execute.</code>
 * Subclasses will also in general override <code>configure</code> to load
 * additional configuration parameters and pass them on to the client in 
 * <code>makeClientThread.</code>  Implementations of <code>configure</code>
 * should start with a <code>super()</code> call so that the base configuration
 * parameters are loaded. This method should also set the <code>configFile</code>
 * property to a valid URI or filespec (suitable argument for Digester's parse
 * method). Setup code that needs to be executed before any client threads are
 * spawned should be put in <code>init</code></p>
 * 
 * <p>See 
 * <a href="http://svn.apache.org/viewvc/commons/sandbox/performance/trunk/src/java/org/apache/commons/performance/dbcp/DBCPSoak.java?view=markup">
 * DBCPSoak</a> and its 
 * <a href="http://svn.apache.org/viewvc/commons/sandbox/performance/trunk/dbcp/config-dbcp.xml?view=markup">
 * sample configuration file</a> for an example.  As in that example, additional
 * sections of the config file should be parsed and loaded in the overridden
 * <code>configure</code> method. The "run" section is required by the base
 * implementation. That example also illustrates how <code>init</code>
 * can be used to set up resources or data structures required by the client
 * threads.</p>
 *
 */
public abstract class LoadGenerator {
    
    /** logger */
    protected static final Logger logger = 
        Logger.getLogger(LoadGenerator.class.getName());
    
    /** Statistics aggregator */
    private static Statistics stats = new Statistics();
    
    // Client thread properties
    protected long minDelay;
    protected long maxDelay;
    protected double sigma;
    protected String delayType;
    protected String rampType;
    protected long rampPeriod;
    protected long peakPeriod;
    protected long troughPeriod;
    protected String cycleType;
    
    // Run properties
    private long numClients;
    private long iterations;
    
    protected Digester digester = new Digester();
    protected String configFile = null;
    
    /**
     * <p>Invokes {@link #configure()} to load digester rules, then digester.parse,
     * then {@link #init} to initialize configuration members. Then spawns and
     * executes {@link #numClients} ClientThreads using {@link #makeClientThread}
     * to create the ClientThread instances. Waits for all spawned threads to
     * terminate and then logs accumulated statistics, using 
     * {@link Statistics#displayOverallSummary}</p>
     * 
     * <p>Subclasses should not need to override this method, but must implement
     * {@link #makeClientThread} and may override {@link #configure} and
     * {@link #init} to prepare data to pass to <code>makeClientThread</code>, 
     * and {@link #cleanUp} to clean up after all threads terminate.
     * </p>
     * 
     * @throws Exception
     */
    public void execute() throws Exception {
        configure();
        parseConfigFile();
        init();
        // Spawn and execute client threads
		ExecutorService ex = Executors.newFixedThreadPool((int)numClients);
		for (int i = 0; i < numClients; i++) {
            ClientThread clientThread = makeClientThread(iterations, minDelay,
                    maxDelay, sigma, delayType, rampPeriod, peakPeriod,
                    troughPeriod, cycleType, rampType, logger, stats);
			ex.execute(clientThread);
		} 
        ex.shutdown();
        // hard time limit of one day for now 
        // TODO: make this configurable
        ex.awaitTermination(60 * 60 * 24, TimeUnit.SECONDS);
        
        // Log summary statistics for accumulated metrics
        logger.info(stats.displayOverallSummary());
        
        // clean up
        cleanUp();
	}
    
    protected abstract ClientThread makeClientThread(
            long iterations, long minDelay, long maxDelay, double sigma,
            String delayType, long rampPeriod, long peakPeriod,
            long troughPeriod, String cycleType, String rampType,
            Logger logger, Statistics stats);
    
    /**
     * This method is invoked by {@link #execute()} after {@link #configure()}
     * and digester parse, just before client threads are created. Objects that
     * need to be created and passed to client threads using configuration info
     * parsed from the config file should be created in this method.
     * 
     * @throws Exception
     */
    protected void init() throws Exception {}
    
    
    /**
     * This method is invoked by {@link #execute()} after all spawned threads
     * have terminated. Override to clean up any resources allocated in 
     * {@link #init()}.
     * 
     * @throws Exception
     */
    protected void cleanUp() throws Exception {}
    
    
    /**
     * Configures basic run parameters. Invoked by Digester via a rule defined
     * in {@link #configure()}.
     * 
     * @param iterations number of iterations
     * @param clients number of client threads
     * @param minDelay minimum delay between client thread requests (ms)
     * @param maxDelay maximum delay between client thread requests (ms)
     * @param sigma standard deviation of delay
     * @param delayType type of delay (constant, gaussian, poisson)
     * @param rampType type of ramp (none, linear, random)
     * @param rampPeriod rampup/rampdown time
     * @param peakPeriod peak period
     * @param troughPeriod trough period
     * @param cycleType cycle type (none, oscillating)
     * @throws ConfigurationException
     */
    
    public void configureRun(String iterations, String clients,
            String minDelay, String maxDelay, String sigma,
            String delayType, String rampType, String rampPeriod,
            String peakPeriod, String troughPeriod, String cycleType) 
            throws ConfigurationException {
     
        this.iterations = Long.parseLong(iterations);
        this.numClients = Long.parseLong(clients);
        this.minDelay = Long.parseLong(minDelay);
        this.maxDelay = Long.parseLong(maxDelay);
        this.sigma = Double.parseDouble(sigma);
        this.delayType = delayType;
        this.rampType = rampType;
        this.rampPeriod = Long.parseLong(rampPeriod);
        this.peakPeriod = Long.parseLong(peakPeriod);
        this.troughPeriod = Long.parseLong(troughPeriod);
        this.cycleType = cycleType;
        if (cycleType.equals("oscillating") && this.rampPeriod <= 0) {
            throw new ConfigurationException(
              "Ramp period must be positive for oscillating cycle type");
        }
    }
    
    /**
     * <p>Starts preparing Digester to parse the configuration file, pushing
     * *this onto the stack and loading rules to configure basic "run" 
     * parameters.
     * </p>
     * <p>Subclasses can override this, using <code>super()</code> to load base
     * parameters and then adding additional </code>addCallMethod</code>
     * sequences for additional parameters.
     * </p>
     * 
     * @throws Exception
     */
    protected void configure() throws Exception {
        digester.push(this);
        
        digester.addCallMethod("configuration/run", 
                "configureRun", 11);
        digester.addCallParam(
                "configuration/run/iterations", 0);
        digester.addCallParam(
                "configuration/run/clients", 1);
        digester.addCallParam(
                "configuration/run/delay-min", 2);
        digester.addCallParam(
                "configuration/run/delay-max", 3);
        digester.addCallParam(
                "configuration/run/delay-sigma", 4);
        digester.addCallParam(
                "configuration/run/delay-type", 5);
        digester.addCallParam(
                "configuration/run/ramp-type", 6);
        digester.addCallParam(
                "configuration/run/ramp-period", 7);
        digester.addCallParam(
                "configuration/run/peak-period", 8);
        digester.addCallParam(
                "configuration/run/trough-period", 9);
        digester.addCallParam(
                "configuration/run/cycle-type", 10);    
    }
    
    protected void parseConfigFile() throws Exception {
        // TODO: get rid of File spec
        digester.parse(new File(configFile));
    }

    /**
     * @return the configFile
     */
    public String getConfigFile() {
        return configFile;
    }

    /**
     * @param configFile the configFile to set
     */
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    /**
     * @return the digester
     */
    public Digester getDigester() {
        return digester;
    }
    
    /**
     * @return statistics
     */
    public Statistics getStatistics() {
        return stats;
    }
}
