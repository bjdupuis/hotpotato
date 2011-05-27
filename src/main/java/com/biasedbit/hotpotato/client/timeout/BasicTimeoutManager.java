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

package com.biasedbit.hotpotato.client.timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.internal.ExecutorUtil;

import com.biasedbit.hotpotato.client.HttpRequestContext;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.request.HttpRequestFutureListener;

/**
 * Basic implementation of a {@link TimeoutManager} that launches a thread with a {@link TimeoutChecker}.
 * <p/>
 * The {@link TimeoutChecker} will bind itself as a listener to the request future. It will block on a latch until
 * either the provided timeout expires or the operation completes.
 * <p/>
 * If the executor queues the execution of the {@link TimeoutChecker}, it is very likely that when the task starts, the
 * operation is already complete. In this case, the checker will terminate immediately.
 * <h2>Precision vs resource consumption</h1>
 * Precision is how fast a request is cancelled when it's execution times out.
 * This timer can consume more resources (unless its configured to run a single thread), but is potentially more precise
 * than {@link HashedWheelTimeoutManager}.
 * <p/>
 * <strong>The more threads it has, the more precise it will be, but also more resources it will consume.</strong>
 * Conversely, the less threads it has, the less precise it will be.
 *
 * <div class="note">
 * <div class="header>Important note:</div>
 * If an instance is {@linkplain #BasicTimeoutManager(java.util.concurrent.Executor) created with an external
 * {@code Executor}}, then this manager <strong>will not</strong> terminate the executor.
 * <p/>
 * If an instance is created with the constructor {@link #BasicTimeoutManager(int)}, it will terminate the thread pool
 * when {@link #terminate()} is called.
 * </div>
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class BasicTimeoutManager implements TimeoutManager {

    // configuration --------------------------------------------------------------------------------------------------

    private final Executor executor;

    // internal vars --------------------------------------------------------------------------------------------------

    private final boolean internalExecutor;

    // constructors ---------------------------------------------------------------------------------------------------

    public BasicTimeoutManager(final int maxThreads) {
        if (maxThreads <= 0) {
            this.executor = Executors.newCachedThreadPool();
        } else {
            this.executor = Executors.newFixedThreadPool(maxThreads);
        }
        this.internalExecutor = true;
    }

    public BasicTimeoutManager(final Executor executor) {
        this.executor = executor;
        this.internalExecutor = false;
    }

    // TimeoutManager -------------------------------------------------------------------------------------------------


    public boolean init() {
        return true;
    }


    public void terminate() {
        if (this.internalExecutor) {
            ExecutorUtil.terminate(this.executor);
        }
    }


    public void manageRequestTimeout(final HttpRequestContext context) {
        this.executor.execute(new TimeoutChecker(context));
    }

    /**
     * Checks if a given request times out before its execution completes.
     * <p/>
     * When the {@link #run()} method is called, the timeout checker binds itself as a listener to the provided {@link
     * com.biasedbit.hotpotato.client.HttpRequestContext}.
     * <p/>
     * The motive behind using a {@code TimeoutChecker} instead of having some internal thread in each connection that
     * shares state variables is that on some rare occasions, while executing runs with client and server on the same
     * host, the responseses are actually received faster than the timeout checker thread is launched. If this happens,
     * it means that the {@link com.biasedbit.hotpotato.client.connection.HttpConnection} will probably have accepted
     * another request and the timeout checker will actually be timing out a different request than the one originally
     * intended.
     * <p/>
     * Having a per-request checker does hurt performance a bit but it avoids the aforementioned problem. Besides, this
     * small performance slowdown only occurs when the request-response cycle takes 0.01~ to complete which, in real
     * scenarios, is extremely unlikely to ever happen.
     *
     * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
     */
    public static class TimeoutChecker implements Runnable, HttpRequestFutureListener {

        // internal vars --------------------------------------------------------------------------------------------------

        private final HttpRequestContext request;
        private final CountDownLatch latch;

        // constructors ---------------------------------------------------------------------------------------------------

        public TimeoutChecker(final HttpRequestContext request) {
            this.request = request;
            this.latch = new CountDownLatch(1);
        }

        // Runnable -------------------------------------------------------------------------------------------------------

        @SuppressWarnings({"unchecked"})

        public void run() {
            this.request.getFuture().addListener(this);

            // If operation completed meanwhile, operationComplete() will have been called, the latch will have been
            // counted down to zero and latch.await() will return immediately with true so everything's perfect even in
            // that twisted scenario.
            try {
                if (this.latch.await(this.request.getTimeout(), TimeUnit.MILLISECONDS)) {
                    // Explicitly released before timing out, nothing to do, request already executed or failed.
                    return;
                }
            } catch (InterruptedException e) {
                Thread.interrupted();
            }

            // Request timed out...
            // If the future has already been unlocked, then no harm is done by calling this method as it won't produce
            // any side effects.
            this.request.getFuture().setFailure(HttpRequestFuture.TIMED_OUT);
        }

        // HttpRequestFutureListener ----------------------------------------------------------------------------------


        public void operationComplete(final HttpRequestFuture httpRequestFuture) throws Exception {
            this.latch.countDown();
        }
    }
}
