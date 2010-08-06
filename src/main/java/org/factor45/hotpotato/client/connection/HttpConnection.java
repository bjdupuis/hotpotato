package org.factor45.hotpotato.client.connection;

import org.factor45.hotpotato.client.HttpRequestContext;
import org.jboss.netty.channel.ChannelHandler;

/**
 * An HTTP connection to a server.
 * <p/>
 * HTTP requests are dispatched from the {@link org.factor45.hotpotato.client.HttpClient} to the {@code HttpConnection}s
 * under the form of a {@link HttpRequestContext}.
 * <p/>
 * To execute requests in a {@code HttpConnection}, the caller must always ensure the connection can process its request
 * by calling {@link #isAvailable()} prior to {@link #execute(org.factor45.hotpotato.client.HttpRequestContext) execute()}.
 * <p/>
 * Example:
 * <pre class="code">
 * if (connection.isAvailable) {
 *     // This is not guaranteed to work.. Connection may go down or be termianted by other thread meanwhile!
 *     connection.execute(request);
 * }</pre>
 * Implementations of this interface are thread-safe. However, it is ill-advised to used them from multiple threads in
 * order to avoid entropic behaviour. If you really want to use a single connection from multiple threads, you should
 * manually synchronise externally. The reason for this is that if both threads call {@link #isAvailable()} at the same
 * time, both will be able to {@linkplain #execute(org.factor45.hotpotato.client.HttpRequestContext) submit requests}, even though the implementation
 * may not accept both (and consequently fail the last one with {@link
 * org.factor45.hotpotato.request.HttpRequestFuture#EXECUTION_REJECTED}).
 * <p/>
 * Example:
 * <pre class="code">
 * synchronized (connection) {
 *     if (connection.isAvailable) {
 *         // This is not guaranteed to work.. Connection may close meanwhile!
 *         connection.execute(request);
 *     }
 * }</pre>
 * The reason for this implementation decision is making the common case fast: a vast majority of the times that
 * {@link #isAvailable()} is called, {@link #execute(HttpRequestContext) execute()} will accept the
 * request. So rather than having only execute returning {@code true} or {@code false} based on the connection
 * availibility, this quicker call is a very reliable heuristic to determine if requests can be submitted or not.
 *
 * <div class="note">
 * <div class="header">Note:</div>
 * There is no guarantee that a request will be approved if {@link #isAvailable()} returned true. Even though the
 * odds are extremely slim, the connection may go down between the call to {@link #isAvailable()} and {@link
 * #execute(HttpRequestContext) execute()}.
 * <p/>For such cases (and only for such cases) {@link #execute(HttpRequestContext) execute()} will return {@code false}
 * rather than fail, the request in order for the caller to be given the chance to retry the same request in another
 * connection.
 * <p/>
 * In every other scenario where {@link #isAvailable()} returns false, calling {@link #execute(HttpRequestContext)
 * execute()} <strong>will fail</strong> the request (with cause {@link
 * org.factor45.hotpotato.request.HttpRequestFuture#EXECUTION_REJECTED}).
 * </div>
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HttpConnection extends ChannelHandler {

    /**
     * Shut down the connection, cancelling all requests executing meanwhile.
     * <p/>
     * After a connection is shut down, no more requests will be accepted.
     */
    void terminate();

    /**
     * Returns the unique identifier of this connection.
     *
     * @return An identifier of the connection.
     */
    String getId();

    /**
     * Returns the host address to which this connection is connected to.
     *
     * @return The host address to which this connection is currently connected to.
     */
    String getHost();

    /**
     * Returns the port to which this connection is connected to.
     *
     * @return The port of to which this connection is connected to.
     */
    int getPort();

    /**
     * Returns whether this connection is available to process a request. Connections that execute HTTP 1.0 requests
     * will <strong>never</strong> return true after the request has been approved for processing as the socket will
     * be closed by the server.
     *
     * @return true if this connection is connected and ready to execute a request
     */
    boolean isAvailable();

    /**
     * Execute a given request context in this connection. All calls to this method should first test whether this
     * connection is available or not by calling {@link #isAvailable()} first. If this method is called while
     * {@link #isAvailable()} would return {@code false}, then implementations will instantly cause a failure on the
     * request with the reason {@link org.factor45.hotpotato.request.HttpRequestFuture#EXECUTION_REJECTED} and return
     * {@code true}, meaning the request was consumed (and failed).
     * <p/>
     * The exception to the above rule is when the request is submitted and the connection goes down meanwhile. In this
     * case, the request <strong>will not</strong> be marked as failed and this method will return {@code false} so that
     * the caller may retry the same request in another connection.
     * <p/>
     * You should always, <strong>always</strong> test first with {@link #isAvailable()}.
     * <p/>
     * This is a non-blocking call.
     *
     * @param context Request execution context.
     *
     * @return {@code true} if the request was accepted, {@code false} otherwise. If a request is accepted, the
     * {@code HttpConnection} becomes responsible for calling {@link
     * org.factor45.hotpotato.request.HttpRequestFuture#setFailure(Throwable) setFailure()} or {@link
     * org.factor45.hotpotato.request.HttpRequestFuture#setSuccess(Object,
     * org.jboss.netty.handler.codec.http.HttpResponse) setSuccess()} on it.
     */
    boolean execute(HttpRequestContext context);
}
