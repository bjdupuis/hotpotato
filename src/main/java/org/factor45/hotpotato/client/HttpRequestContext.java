/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.factor45.hotpotato.client;

import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * State holder context for a request.
 * <p/>
 * This structure is passed between the {@link org.factor45.hotpotato.client.HttpClient} and the {@link
 * org.factor45.hotpotato.client.connection.HttpConnection} and associates a
 * {@link org.jboss.netty.handler.codec.http.HttpRequest} to a {@link HttpResponseProcessor} and a
 * {@link org.factor45.hotpotato.request.HttpRequestFuture}.
 * <p/>
 * It also contains other information such as the host address to which this request was originally inteded for, as well
 * as its port and the timeout for the HTTP request/response operation to complete.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class HttpRequestContext<T> {

    // internal vars --------------------------------------------------------------------------------------------------

    private final String host;
    private final int port;
    private final int timeout;
    private final HttpRequest request;
    private final HttpResponseProcessor<T> processor;
    private final HttpRequestFuture<T> future;

    // constructors ---------------------------------------------------------------------------------------------------

    public HttpRequestContext(String host, int port, int timeout, HttpRequest request,
                              HttpResponseProcessor<T> processor, HttpRequestFuture<T> future) {
        if (host == null) {
            throw new IllegalArgumentException("Host cannot be null");
        }
        if ((port <= 0) || (port > 65536)) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (request == null) {
            throw new IllegalArgumentException("HttpRequest cannot be null");
        }
        if (processor == null) {
            throw new IllegalArgumentException("HttpResponseProcessor cannot be null");
        }
        if (future == null) {
            throw new IllegalArgumentException("HttpRequestFuture cannot be null");
        }

        this.timeout = timeout < 0 ? 0 : timeout;
        this.host = host;
        this.port = port;
        this.request = request;
        this.processor = processor;
        this.future = future;
    }

    public HttpRequestContext(String host, int timeout, HttpRequest request, HttpResponseProcessor<T> processor,
                              HttpRequestFuture<T> future) {
        this(host, 80, timeout, request, processor, future);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeout() {
        return timeout;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponseProcessor<T> getProcessor() {
        return processor;
    }

    public HttpRequestFuture<T> getFuture() {
        return future;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append(this.request.getProtocolVersion()).append(' ')
                .append(this.request.getMethod()).append(' ')
                .append(this.request.getUri()).append(" (")
                .append(this.host).append(':')
                .append(this.port).append(')').append("@").append(Integer.toHexString(this.hashCode())).toString();
    }
}
