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

package com.biasedbit.hotpotato.client.host;

import com.biasedbit.hotpotato.client.HttpRequestContext;
import com.biasedbit.hotpotato.client.connection.HttpConnection;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class EagerDrainHostContext extends AbstractHostContext {

    // constructors ---------------------------------------------------------------------------------------------------

    public EagerDrainHostContext(String host, int port, int maxConnections) {
        super(host, port, maxConnections);
    }

    // DefaultHostContext ---------------------------------------------------------------------------------------------

    @Override
    public DrainQueueResult drainQueue() {
        // 1. Test if there's anything to drain
        if (this.queue.isEmpty()) {
            return DrainQueueResult.QUEUE_EMPTY;
        }

        // 2. There are contents to drain, test if there are any connections created.
        if (this.connectionPool.getConnections().isEmpty()) {
            // 2a. No connections open, test if there is still room to create a new one.
            if (this.connectionPool.getTotalConnections() < this.maxConnections) {
                return DrainQueueResult.OPEN_CONNECTION;
            } else {
                return DrainQueueResult.NOT_DRAINED;
            }
        }

        // 3. There is content to drain and there are connections, drain as much as possible in a single loop.
        boolean drained = false;
        for (HttpConnection connection : this.connectionPool.getConnections()) {
            // Drain the first element in queue.
            // There will always be an element in the queue, ensured by 1. or by the premature exit right below.
            while (connection.isAvailable()) {
                // Peek the next request and see if the connection is able to accept it.
                HttpRequestContext context = this.queue.peek();
                if (connection.execute(context)) {
                    // Request was accepted by the connection, remove it from the queue.
                    this.queue.remove();
                    if (this.queue.isEmpty()) {
                        // Prematurely exit in case there are no further requests to execute.
                        // Returning prematurely dispenses additional check before queue.remove()
                        return DrainQueueResult.DRAINED;
                    }
                    // Otherwise, result WILL be DRAINED, no matter if we manage do execute another request or not.
                    drained = true;
                }
                // Request was not accepted by this connection, keep trying other connections.
            }
        }
        if (drained) {
            return DrainQueueResult.DRAINED;
        }

        // 4. There were connections open but none of them was available; if possible, request a new one.
        if (this.connectionPool.getTotalConnections() < this.maxConnections) {
            return DrainQueueResult.OPEN_CONNECTION;
        } else {
            return DrainQueueResult.NOT_DRAINED;
        }
    }
}
