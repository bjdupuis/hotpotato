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

package com.biasedbit.hotpotato.client;

import com.biasedbit.hotpotato.client.event.ConnectionClosedEvent;
import com.biasedbit.hotpotato.client.event.ConnectionFailedEvent;
import com.biasedbit.hotpotato.client.event.ConnectionOpenEvent;
import com.biasedbit.hotpotato.client.event.EventType;
import com.biasedbit.hotpotato.client.event.ExecuteRequestEvent;
import com.biasedbit.hotpotato.client.event.HttpClientEvent;
import com.biasedbit.hotpotato.client.event.RequestCompleteEvent;
import com.biasedbit.hotpotato.client.host.HostContext;
import com.biasedbit.hotpotato.logging.Logger;
import com.biasedbit.hotpotato.request.HttpRequestFuture;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class VerboseHttpClient extends AbstractHttpClient implements EventProcessorStatsProvider {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(VerboseHttpClient.class);

    // internal vars --------------------------------------------------------------------------------------------------

    protected long totalTime = 0;
    protected long executeRequestTime = 0;
    protected long requestCompleteTime = 0;
    protected long connectionOpenTime = 0;
    protected long connectionClosedTime = 0;
    protected long connectionFailedTime = 0;
    protected int events = 0;

    // AbstractHttpClient ---------------------------------------------------------------------------------------------


    @Override
    protected void eventHandlingLoop() {
        for (;;) {
            // Manual synchronization here because before removing an element, we first need to check whether an
            // active available connection exists to satisfy the request.
            try {
                LOG.trace("---------------------------------------------------------------");
                LOG.trace("### NEW eventHandlingLoop ITERATION ###");
                HttpClientEvent event = eventQueue.take();
                if (event == POISON) {
                    this.eventConsumerLatch.countDown();
                    return;
                }
                this.events++;
                long start = System.nanoTime();

                LOG.trace("[EHL] Handling event: {}.", event);
                LOG.trace("[EHL] Event queue ---");
                int i = 0;
                for (HttpClientEvent e : this.eventQueue) {
                    LOG.trace("      {}. {}", (++i), e);
                }
                LOG.trace("[EHL] ---------------");

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        this.handleExecuteRequest((ExecuteRequestEvent) event);
                        this.executeRequestTime += System.nanoTime() - start;
                        break;
                    case REQUEST_COMPLETE:
                        this.handleRequestComplete((RequestCompleteEvent) event);
                        this.requestCompleteTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_OPEN:
                        this.handleConnectionOpen((ConnectionOpenEvent) event);
                        this.connectionOpenTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_CLOSED:
                        this.handleConnectionClosed((ConnectionClosedEvent) event);
                        this.connectionClosedTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_FAILED:
                        this.handleConnectionFailed((ConnectionFailedEvent) event);
                        this.connectionFailedTime += System.nanoTime() - start;
                        break;
                    default:
                        // Consume and do nothing, unknown event.
                }
                this.totalTime += System.nanoTime() - start;
            } catch (InterruptedException e) {
                // ignore, poisoning the queue is the only way to stop
            }
        }
    }


    @Override
    protected void handleConnectionFailed(final ConnectionFailedEvent event) {
        // Update the list of available connections for the same host:port.
        String id = this.hostId(event.getConnection());
        HostContext context = this.contextMap.get(id);
        if (context == null) {
            throw new IllegalStateException("Context for id '" + id +
                                            "' does not exist (it may have been incorrectly cleaned up)");
        }

        context.getConnectionPool().connectionFailed();
        if ((context.getConnectionPool().hasConnectionFailures() &&
             (context.getConnectionPool().getTotalConnections() == 0))) {
            LOG.trace("[EHL-hCF] Last of connection attempts for {} failed; cancelling all queued requests.", id);
            // Connection failures occured and there are no more connections active or establishing, so its time to
            // fail all queued requests.
            context.failAllRequests(HttpRequestFuture.CANNOT_CONNECT);
        }
    }


    @Override
    protected void drainQueueAndProcessResult(final HostContext context) {
        HostContext.DrainQueueResult result = context.drainQueue();
        LOG.trace("[EHL-dQAPR] drainQueue() result was {}.", result);
        switch (result) {
            case OPEN_CONNECTION:
                this.openConnection(context);
                break;
            case QUEUE_EMPTY:
            case NOT_DRAINED:
            case DRAINED:
            default:
        }
    }


    @Override
    protected void openConnection(final HostContext context) {
        LOG.trace("[EHL-OC] Opening connection to {}.", this.hostId(context));
        super.openConnection(context);
    }

    // EventProcessorStatsProvider ------------------------------------------------------------------------------------


    public long getTotalExecutionTime() {
        return this.totalTime / 1000000;
    }


    public long getEventProcessingTime(final EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return this.executeRequestTime / 1000000;
            case REQUEST_COMPLETE:
                return this.requestCompleteTime / 1000000;
            case CONNECTION_OPEN:
                return this.connectionOpenTime / 1000000;
            case CONNECTION_CLOSED:
                return this.connectionClosedTime / 1000000;
            case CONNECTION_FAILED:
                return this.connectionFailedTime / 1000000;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }


    public float getEventProcessingPercentage(final EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return (this.executeRequestTime / (float) this.totalTime) * 100;
            case REQUEST_COMPLETE:
                return (this.requestCompleteTime / (float) this.totalTime) * 100;
            case CONNECTION_OPEN:
                return (this.connectionOpenTime / (float) this.totalTime) * 100;
            case CONNECTION_CLOSED:
                return (this.connectionClosedTime / (float) this.totalTime) * 100;
            case CONNECTION_FAILED:
                return (this.connectionFailedTime / (float) this.totalTime) * 100;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }


    public long getProcessedEvents() {
        return this.events;
    }
}
