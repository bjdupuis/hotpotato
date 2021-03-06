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

package com.biasedbit.hotpotato.session.handler;

import com.biasedbit.hotpotato.client.CannotExecuteRequestException;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import com.biasedbit.hotpotato.session.HandlerSessionFacade;
import com.biasedbit.hotpotato.session.RecursiveAwareHttpRequest;
import com.biasedbit.hotpotato.util.HostPortAndUri;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;

/**
 * Automatically handles response codes 301, 302, 303 and 309, following the redirection links provided in the
 * responses bearing those status codes.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class RedirectResponseHandler implements ResponseCodeHandler {

    @Override
    public int[] handlesResponseCodes() {
        return new int[]{301, 302, 303, 309};
    }

    @Override
    public <T> void handleResponse(HandlerSessionFacade session, HttpRequestFuture<T> initialFuture,
                                   HttpRequestFuture<T> future, HostPortAndUri target,
                                   RecursiveAwareHttpRequest request, HttpResponseProcessor<T> processor) {

        if (request.getMethod().equals(HttpMethod.GET) || request.getMethod().equals(HttpMethod.HEAD)) {
            String location = future.getResponse().getHeader(HttpHeaders.Names.LOCATION);
            if (location == null) {
                initialFuture.setFailure(future.getResponse(), HttpRequestFuture.INVALID_REDIRECT);
                return;
            }

            boolean isAbsolute = location.startsWith("http");
            HostPortAndUri path;
            if (isAbsolute) {
                path = HostPortAndUri.splitUrl(location);
            } else {
                path = new HostPortAndUri(target);
                path.setUri(location);
            }

            try {
                session.execute(path, initialFuture, request, processor);
            } catch (CannotExecuteRequestException e) {
                initialFuture.setFailure(e);
            }
        }
    }
}
