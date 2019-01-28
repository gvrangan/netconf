/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.tcp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServerHandler.class);
    private final Bootstrap clientBootstrap;
    private final LocalAddress localAddress;

    private Channel clientChannel;

    public ProxyServerHandler(Bootstrap clientBootstrap, LocalAddress localAddress) {
        this.clientBootstrap = clientBootstrap;
        this.localAddress = localAddress;
    }

    @Override
    public void channelActive(ChannelHandlerContext remoteCtx) {
        final ProxyClientHandler clientHandler = new ProxyClientHandler(remoteCtx);
        clientBootstrap.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline().addLast(clientHandler);
            }
        });
        ChannelFuture clientChannelFuture = clientBootstrap.connect(localAddress).awaitUninterruptibly();
        clientChannel = clientChannelFuture.channel();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOG.trace("channelInactive - closing client channel");
        clientChannel.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("Writing to client channel");
        clientChannel.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        LOG.trace("Flushing client channel");
        clientChannel.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        LOG.warn("Unexpected exception from downstream.", cause);
        ctx.close();
    }
}
