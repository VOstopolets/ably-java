package io.ably.lib.test.realtime;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by VOstopolets on 8/26/16.
 */
public class RealitimeReauthTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Setup.getTestVars();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        Setup.clearTestVars();
    }

    @Test
    public void upgrade_capabilities() {
        AblyRealtime ablyRx = null, ablyTx = null;
        String channelName = "testchannel";//resume_simple
        int messageCount = 5;
        long delay = 200;

        try {
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
            ablyTx = new AblyRealtime(opts);

            /* create restricted recv */
            ClientOptions restrictedOpts = testVars.createOptions(testVars.keys[6].keyStr);
            ablyRx = new AblyRealtime(restrictedOpts);

			/* create and attach channel to send on */
            final Channel channelTx = ablyTx.channels.get(channelName);
            channelTx.attach();
            (new Helpers.ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

			/* create and attach channel to recv on */
            final Channel channelRx = ablyRx.channels.get(channelName);
            channelRx.attach();
            (new Helpers.ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

			/* subscribe */
            Helpers.MessageWaiter messageWaiter =  new Helpers.MessageWaiter(channelRx);

			/* publish first messages to the channel */
            Helpers.CompletionSet msgComplete1 = new Helpers.CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete1.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

			/* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete1.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
            messageWaiter.reset();

            /* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
            ablyRx.connection.connectionManager.requestState(ConnectionState.failed);

			/* wait */
            try { Thread.sleep(2000L); } catch(InterruptedException e) {}

            /* change capability */
            Auth.AuthOptions authOptions = new Auth.AuthOptions() {{
                key = testVars.keys[1].keyStr;
                force = true;
            }};
            Auth.TokenDetails tokenDetails = ablyRx.auth.requestToken(null, authOptions);
            assertNotNull("Expected token value", tokenDetails.token);
            ablyRx.options.key = tokenDetails.token;
            ablyRx.options.tokenDetails = tokenDetails;
            ablyRx.options.authCallback = new Auth.TokenCallback() {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    Setup.TestVars optsTestVars = Setup.getTestVars();
                    ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[1].keyStr);
                    optsForToken.logLevel = Log.VERBOSE;
                    final AblyRest ablyForToken = new AblyRest(optsForToken);
                    return ablyForToken.auth.requestToken(params, null);
                }
            };

			/* reconnect the rx connection */
            ablyRx.connection.connect();

            /* publish further messages to the channel */
            Helpers.CompletionSet msgComplete2 = new Helpers.CompletionSet();
            for(int i = 0; i < messageCount; i++) {
                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
                try { Thread.sleep(delay); } catch(InterruptedException e){}
            }

			/* wait for the publish callback to be called */
            errors = msgComplete2.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);



            /* Default test vars */
//            Setup.TestVars testVars = Setup.getTestVars();
//
//            /* All capabilities options */
//            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
//
//            /* Create sender */
//            ablyTx = new AblyRealtime(opts);
//
//            /* Create restricted receiver */
//            //Setup.Key restrictedKey = testVars.keys[0];
//            //ClientOptions restrictedOpts = testVars.createOptions(restrictedKey.keyStr);
//            ablyRx = new AblyRealtime(opts);
//
//            /* create and attach channel to send on */
//            final Channel channelTx = ablyTx.channels.get(channelName);
//            channelTx.attach();
//            (new Helpers.ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
//            assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);
//
//			/* create and attach channel to recv on */
//            final Channel channelRx = ablyRx.channels.get(channelName);
//            channelRx.attach();
//            (new Helpers.ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
//            assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);
//
//            /* subscribe */
//            Helpers.MessageWaiter messageWaiter =  new Helpers.MessageWaiter(channelRx);
//
//			/* publish first messages to the channel */
//            Helpers.CompletionSet msgComplete1 = new Helpers.CompletionSet();
//            for(int i = 0; i < messageCount; i++) {
//                System.out.println("publish test_event Test message (upgrade_capabilities) index = " + i);
//                channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete1.add());
//                try { Thread.sleep(delay); } catch(InterruptedException e){}
//            }
//
//			/* wait for the publish callback to be called */
//            ErrorInfo[] errors = msgComplete1.waitFor();
//            assertTrue("Verify success from all message callbacks", errors.length == 0);
//
//			/* wait for the subscription callback to be called */
//            messageWaiter.waitFor(messageCount);
//            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
//            messageWaiter.reset();




//            ably = new AblyRest(opts);
//            /* Get first TokenDetails */
//            Auth.TokenDetails tokenDetails = ably.auth.requestToken(null, new Auth.AuthOptions(){{ key = restrictedKey.keyStr; }});
//            ably.options.tokenDetails = tokenDetails;
//            System.out.println("1: capability = " + tokenDetails.capability + " ; capability = " + restrictedKey.capability);

            /* Get second TokenDetails */
//            Setup.Key testKey2 = testVars.keys[2];
//            Auth.TokenDetails tokenDetails2 = ably.auth.requestToken(null, new Auth.AuthOptions(){{key = testKey2.keyStr;}});
//            ably.options.tokenDetails = tokenDetails2;
//            System.out.println("2: capability = " + tokenDetails2.capability + " ; capability = " + testKey2.capability);

        } catch (AblyException e) {
            e.printStackTrace();
            fail("authcapability0: Unexpected exception");
        }
    }

    /**
     * Reauthorise (spec: RTC8)
     * <p>
     * merge
     * {@link RealtimeConnectFailTest#connect_token_expire_disconnected()}
     * {@link RealtimeResumeTest#resume_simple()}
     * <p>
     * Connect to the service using two library instances to set
     * up separate send and recv connections.
     * Wait for one connection enters the disconnected state, after a token
     * used for successful connection expires.
     * Obtain a new token, disconnect the current transport and resume the connection.
     * <p>
     * Verify that performs an upgrade of capabilities without
     * any message loss during the upgrade process.
     */
    @Test
    public void reauth_token_expire_disconnected() {
        AblyRealtime ablyRx = null, ablyTx = null;
        String channelName = "resume_simple";
        int messageCount = 5;
        long delay = 200;

        try {
            //TODO: why used two instance of Ably (AblyRest and AblyRealtime)?

            final Setup.TestVars optsTestVars = Setup.getTestVars();
            ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[0].keyStr);
            optsForToken.logLevel = Log.VERBOSE;
            final AblyRest ablyForToken = new AblyRest(optsForToken);
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams() {{
                ttl = 5000L;
            }}, null);

            System.out.println("Expected token value = " + tokenDetails.token);
            assertNotNull("Expected token value", tokenDetails.token);

			/* implement callback, using Ably instance with key */
            final class TokenGenerator implements Auth.TokenCallback {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    ++cbCount;
                    return ablyForToken.auth.requestToken(params, null);
                }

                public int getCbCount() {
                    return cbCount;
                }

                private int cbCount = 0;
            }
            ;

            TokenGenerator authCallback = new TokenGenerator();

			/* create Ably realtime instance without key */
            final Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = testVars.createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = authCallback;
            opts.logLevel = Log.VERBOSE;
            AblyRealtime ably = new AblyRealtime(opts);

			/* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* wait for disconnected state (on token expiry) */
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

			/* wait for connected state (on token renewal) */
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* verify that our token generator was called */
            assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());

			/* end */
            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }
}
