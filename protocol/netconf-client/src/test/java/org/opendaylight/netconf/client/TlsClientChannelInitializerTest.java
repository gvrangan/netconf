/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.Promise;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.NetconfSessionListenerFactory;

@RunWith(MockitoJUnitRunner.class)
public class TlsClientChannelInitializerTest {
    @Mock
    private SslHandlerFactory sslHandlerFactory;
    @Mock
    private NetconfClientSessionNegotiatorFactory negotiatorFactory;
    @Mock
    private NetconfClientSessionListener sessionListener;

    @SuppressWarnings("unchecked")
    @Test
    public void testInitialize() throws Exception {
        NetconfClientSessionNegotiator sessionNegotiator = mock(NetconfClientSessionNegotiator.class);
        doReturn(sessionNegotiator).when(negotiatorFactory).getSessionNegotiator(
            any(NetconfSessionListenerFactory.class), any(Channel.class), any(Promise.class));
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        Channel channel = mock(Channel.class);
        doReturn(pipeline).when(channel).pipeline();

        doReturn(pipeline).when(pipeline).addFirst(anyString(), any(ChannelHandler.class));
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));

        ChannelConfig channelConfig = mock(ChannelConfig.class);
        doReturn(channelConfig).when(channel).config();
        doReturn(1L).when(negotiatorFactory).getConnectionTimeoutMillis();
        doReturn(channelConfig).when(channelConfig).setConnectTimeoutMillis(1);

        Promise<NetconfClientSession> promise = mock(Promise.class);

        TlsClientChannelInitializer initializer = new TlsClientChannelInitializer(sslHandlerFactory,
                negotiatorFactory, sessionListener);
        initializer.initialize(channel, promise);
        verify(pipeline, times(1)).addFirst(anyString(), any(ChannelHandler.class));
    }
}
