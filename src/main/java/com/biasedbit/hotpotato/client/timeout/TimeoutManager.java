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

import com.biasedbit.hotpotato.client.HttpRequestContext;

/**
 * Facility to manage timeouts for requests.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public interface TimeoutManager {

    boolean init();

    void terminate();

    /**
     * Manage the timeout for the provided context.
     *
     * @param context The request context to monitor. Timeout value is extracted from {@link
     *                com.biasedbit.hotpotato.client.HttpRequestContext#getTimeout()}.
     */
    void manageRequestTimeout(HttpRequestContext context);
}
