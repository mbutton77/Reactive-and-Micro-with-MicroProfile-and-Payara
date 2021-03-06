package ondro.btcdataproducer;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 *
 * @author Ondrej Mihalyi
 */
public class BitstampConnector {

    public static final String BITSTAMP_APP_KEY = "de504dc5763aeef9ff52";

    private Pusher pusher;
    private ScheduledExecutorService executor;
    ScheduledFuture<?> scheduledReconnect = null;

    public void connect(Consumer<String> listener, ScheduledExecutorService executor) {
        this.connect(listener, executor, null);
    }

    public void connect(Consumer<String> listener, ScheduledExecutorService executor, ConnectionEventListener connectionListener) {
        pusher = createBitstampTradesPusher((String channel, String event, String data) -> {
            executor.submit(() -> listener.accept(data));
        });
        connectTo(pusher, Optional.ofNullable(connectionListener));
    }

    public void disconnect() {
        if (scheduledReconnect != null) {
            scheduledReconnect.cancel(true);
        }
        pusher.disconnect();
    }

    private Pusher createBitstampTradesPusher(SubscriptionEventListener subscriptionEventListener) {
        PusherOptions options = new PusherOptions();
        Pusher pusher = new Pusher(BITSTAMP_APP_KEY, options);
        // Subscribe to a channel
        Channel channel = pusher.subscribe("live_trades");
        // Bind to listen for events called "trade" sent to "live_trades"
        channel.bind("trade", subscriptionEventListener);
        return pusher;
    }

    private void connectTo(Pusher pusher, Optional<ConnectionEventListener> connectionListener) {
        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                log().info("State changed to " + change.getCurrentState()
                        + " from " + change.getPreviousState());
                connectionListener.ifPresent(l -> l.onConnectionStateChange(change));
            }

            @Override
            public void onError(String message, String code, Exception e) {
                int WAIT_SECONDS_BEFORE_RECONNECT = 5;
                log().warning("There was a problem connecting! Will attempt to reconnect in " + WAIT_SECONDS_BEFORE_RECONNECT + " seconds");
                scheduledReconnect = executor.schedule(() -> {
                    scheduledReconnect = null;
                    pusher.connect();
                }, WAIT_SECONDS_BEFORE_RECONNECT, TimeUnit.SECONDS);
                connectionListener.ifPresent(l -> l.onError(message, code, e));
            }

            private Logger log() {
                return Logger.getLogger(this.getClass().getName());
            }
        }, ConnectionState.ALL
        );
    }

}
