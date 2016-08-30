package io.ably.lib.test.realtime;

import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Created by VOstopolets on 8/26/16.
 */
public class RealitimeReauthTest {

    @Test
    public void reauth_tokenDetails() {
        String wrongChannel = "wrongchannel";
        String rightChannel = "rightchannel";
        String testClientId = "testClientId";

        try {
            /* init ably for token */
            final Setup.TestVars optsTestVars = Setup.getTestVars();
            ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(wrongChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);
            System.out.println("firstToken.token = " + firstToken.token);

			/* create Ably realtime instance with tokenDetails */
            final Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = testVars.createOptions();
            opts.clientId = testClientId;
            opts.tokenDetails = firstToken;
            AblyRealtime ablyRealtime = new AblyRealtime(opts);

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);


            /* create a channel and check can't attach */
            Channel channel = ablyRealtime.channels.get(rightChannel);
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            ErrorInfo error = waiter.waitFor();
            assertNotNull("Expected error", error);
            assertEquals("Verify error code 40160 (channel is denied access)", error.code, 40160);

            /* get second token */
            tokenParams = new Auth.TokenParams();
            capability = new Capability();
            capability.addResource(wrongChannel, "*");
            capability.addResource(rightChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", secondToken.token);

            /* reauthorise */
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.key = optsTestVars.keys[0].keyStr;
            authOptions.tokenDetails = secondToken;
            authOptions.force = true;
            Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorise(authOptions, null);
            assertNotNull("Expected token value", reauthTokenDetails.token);

            /* re-attach to the channel */
            waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);

            /* verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void reauth_test() {
        String wrongChannel = "wrongchannel";
        String rightChannel = "rightchannel";
        String testClientId = "testClientId";

        try {
            /* init ably for token */
            final Setup.TestVars optsTestVars = Setup.getTestVars();
            ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(wrongChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* implement callback, using Ably instance with key */
            final class TokenGenerator implements Auth.TokenCallback {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    return ablyForToken.auth.requestToken(params, null);
                }
            }
            TokenGenerator authCallback = new TokenGenerator();

			/* create Ably realtime instance without key */
            final Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = testVars.createOptions();
            opts.tokenDetails = firstToken;
            opts.authCallback = authCallback;
            AblyRealtime ably = new AblyRealtime(opts);

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);


            /* create a channel and check can't attach */
            Channel channel = ably.channels.get(rightChannel);
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            ErrorInfo error = waiter.waitFor();
            assertNotNull("Expected error", error);
            assertEquals("Verify error code 40160 (channel is denied access)", error.code, 40160);

            /* get second token */
            tokenParams = new Auth.TokenParams();
            capability = new Capability();
            capability.addResource(wrongChannel, "*");
            capability.addResource(rightChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* reauthorise */
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.tokenDetails = secondToken;
            authOptions.force = true;
            Auth.TokenDetails reauthTokenDetails = ably.auth.authorise(authOptions, null);
            assertNotNull("Expected token value", reauthTokenDetails.token);

            /* re-attach to the channel */
            waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);

            /* verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
