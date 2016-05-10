/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.rest;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Handler that tracks connection establishment statistics.
 */
class ConnectionStatsHandler extends ChannelDuplexHandler {
  private final NettyMetrics metrics;
  private AtomicLong openConnections;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public ConnectionStatsHandler(NettyMetrics metrics) {
    this.metrics = metrics;
    openConnections = new AtomicLong(0);
    metrics.registerConnectionsStatsHandler(openConnections);
  }

  /**
   * Calls {@link ChannelHandlerContext#connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)} to forward
   * to the next {@link io.netty.channel.ChannelOutboundHandler} in the {@link io.netty.channel.ChannelPipeline}.
   *
   * Sub-classes may override this method to change behavior.
   */
  @Override
  public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
      ChannelPromise future)
      throws Exception {
    logger.trace("Channel Connected " + remoteAddress);
    metrics.connectionsConnectedCount.inc();
    openConnections.incrementAndGet();
    super.connect(ctx, remoteAddress, localAddress, future);
  }

  /**
   * Calls {@link ChannelHandlerContext#disconnect(ChannelPromise)} to forward
   * to the next {@link io.netty.channel.ChannelOutboundHandler} in the {@link io.netty.channel.ChannelPipeline}.
   *
   * Sub-classes may override this method to change behavior.
   */
  @Override
  public void disconnect(ChannelHandlerContext ctx, ChannelPromise future)
      throws Exception {
    logger.trace("Channel Disconnected " + ctx.channel().remoteAddress());
    metrics.connectionsDisconnectedCount.inc();
    openConnections.decrementAndGet();
    super.disconnect(ctx, future);
  }
}
