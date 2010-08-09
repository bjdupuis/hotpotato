package org.factor45.hotpotato.client.timeout;

import org.factor45.hotpotato.client.HttpRequestContext;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of timeout manager that uses an underlying {@link HashedWheelTimer} to manage timeouts.
 * <p/>
 * If an external {@link HashedWheelTimer} is provided, an instance of this class <strong>will not</strong> call
 * {@link HashedWheelTimer#start()} nor {@link HashedWheelTimer#start()} when {@link #init()} and {@link #terminate()}
 * are called.
 * <h2>Precision vs resource consumption</h2>
 * Since {@link HashedWheelTimer} has a periodic checking interval, this timer is not very precise. If, however, you
 * configure the {@link HashedWheelTimer} with a very small interval, it will increase precision at the cost of more
 * periodic checks (wasted CPU).
 * <p/>
 * The default tick is 500ms. This means that in the worst case scenario, a request will be cancelled 500ms over the
 * timeout set. If this is acceptable, use this implementation rather than {@link BasicTimeoutManager}. You can always
 * configure a lower tick time although for HTTP requests even 1 second over the limit is okay most of the times.
 *
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class HashedWheelTimeoutManager implements TimeoutManager {

    // configuration --------------------------------------------------------------------------------------------------

    private final HashedWheelTimer timer;

    // internal vars --------------------------------------------------------------------------------------------------

    private final boolean internalTimer;

    // constructors ---------------------------------------------------------------------------------------------------

    public HashedWheelTimeoutManager() {
        this.timer = new HashedWheelTimer(500, TimeUnit.MILLISECONDS, 512);
        this.internalTimer = true;
    }

    public HashedWheelTimeoutManager(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this.timer = new HashedWheelTimer(tickDuration, unit, ticksPerWheel);
        this.internalTimer = true;
    }

    public HashedWheelTimeoutManager(HashedWheelTimer timer) {
        this.timer = timer;
        this.internalTimer = false;
    }

    // TimeoutManager -------------------------------------------------------------------------------------------------

    @Override
    public boolean init() {
        if (this.internalTimer) {
            this.timer.start();
        }
        return true;
    }

    @Override
    public void terminate() {
        if (this.internalTimer) {
            this.timer.stop();
        }
    }

    @Override
    public void manageRequestTimeout(final HttpRequestContext context) {
        TimerTask task = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isExpired()) {
                    context.getFuture().setFailure(HttpRequestFuture.TIMED_OUT);
                }
            }
        };
        this.timer.newTimeout(task, context.getTimeout(), TimeUnit.MILLISECONDS);
    }
}