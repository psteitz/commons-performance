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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import org.apache.commons.performance.ClientThread;
import org.apache.commons.performance.Statistics;

/**
 * Client thread that executes http requests in a loop against a configured
 * url, with the number of requests, time between requests and 
 * query strings governed by constructor parameters. See 
 * {@link ClientThread ClientThread javadoc} for a description
 * of how times between requests are computed.
 *
 */
public class HttpClientThread extends ClientThread {

    private HttpClient httpClient = new HttpClient();
    private HttpMethod httpMethod = null;
    private String successKey = null;
    
    public HttpClientThread(long iterations, long minDelay, long maxDelay,
            double sigma, String delayType, long rampPeriod,
            long peakPeriod, long troughPeriod, String cycleType,
            String rampType, Logger logger,
            Statistics stats, String url, String method,
            int socketTimeout, String successKey)  {
        
        super(iterations, minDelay, maxDelay, sigma, delayType, rampPeriod,
                peakPeriod, troughPeriod, cycleType, rampType, logger,
                stats);
        
        httpClient.getParams().setSoTimeout(socketTimeout);
        if (method.trim().toUpperCase().equals("POST")) {
            httpMethod = new PostMethod(url);
        } else {
            httpMethod = new GetMethod(url);
        }
        this.successKey = successKey;
    }

    /** Nothing to do here at this point */
    public void setUp() throws Exception {}
    
    /**
     * Execute the http method against the target url.
     * Throws HttpException if something other than 200 status is returned
     * or if there is a successKey configured and the response does not contain
     * the specified key.
     */
    public void execute() throws Exception {
        int statusCode = httpClient.executeMethod(httpMethod);
        if (statusCode != HttpStatus.SC_OK) {
            throw new HttpException("Request failed with status code: "
                    + statusCode);
        }
        // Should use stream here - for now assume body is small
        String responseBody = httpMethod.getResponseBodyAsString();
        if (successKey != null && responseBody.indexOf(successKey) < 0 ) {
            throw new HttpException("Response did not include success key: "
                    + successKey);
        }
    }
    
    /** Release http connection */
    public void cleanUp() throws Exception {
         httpMethod.releaseConnection();
    }

}
