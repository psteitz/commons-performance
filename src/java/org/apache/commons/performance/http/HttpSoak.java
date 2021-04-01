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

package org.apache.commons.performance.http;

import java.util.logging.Logger;

import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.LoadGenerator;
import org.apache.commons.performance.Statistics;

/**
 * Simple http load / performance tester, providing another LoadGenerator
 * example.
 * 
 * Uses Commons Digester to parse and load configuration and spawns
 * HTTPClientThread instances to generate load and gather statistics.
 *
 */

public class HttpSoak extends LoadGenerator {
    
    private String url;
    private String method;
    private int socketTimeout;
    private String successKey;
    
    /** Nothing to do here yet */
    protected void init() throws Exception {}
    
    protected ClientThread makeClientThread(
            long iterations, long minDelay, long maxDelay, double sigma,
            String delayType, long rampPeriod, long peakPeriod,
            long troughPeriod, String cycleType, String rampType,
            Logger logger, Statistics stats) {
        
        return new HttpClientThread(iterations, minDelay, maxDelay,
            sigma, delayType, rampPeriod, peakPeriod, 
            troughPeriod, cycleType, rampType, logger,
            stats, url, method, socketTimeout, successKey);
    }
    
    /**
     * Add http client configuration to parameters loaded by super.
     * Also set config file name.
     */
    protected void configure() throws Exception {
        
        super.configure(); // loads run configuration
        
        digester.addCallMethod("configuration/http",
                "configureHttp", 4);
        digester.addCallParam("configuration/http/url", 0);
        digester.addCallParam("configuration/http/method", 1);
        digester.addCallParam("configuration/http/socket-timeout", 2);
        digester.addCallParam("configuration/http/success-key", 3);
        
        this.configFile = "config-http.xml";
    }
    
    // ------------------------------------------------------------------------
    // Configuration methods specific to this LoadGenerator invoked by Digester
    // when superclass execute calls digester.parse.
    // ------------------------------------------------------------------------
    public void configureHttp(String url, 
            String method, String socketTimeout, String successKey) {
        this.url = url;
        this.method = method;
        this.socketTimeout = Integer.parseInt(socketTimeout);
        this.successKey = successKey;
    }
}
