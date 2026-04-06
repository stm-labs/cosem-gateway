/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.transport;

import io.cosemgateway.handler.PushReceiver;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Netty-based TCP server that accepts push connections from DLMS/COSEM meters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServer {

    /** Minimum WPDU header size in bytes. */
    private static final int WPDU_HEADER_LEN = 8;

    @Value("${cosem.tcp.port:4059}")
    private int port;

    @Value("${cosem.tcp.idle-timeout-seconds:120}")
    private int idleTimeoutSeconds;

    private final PushReceiver pushReceiver;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new IdleStateHandler(idleTimeoutSeconds, 0, 0, TimeUnit.SECONDS))
                                .addLast(new DlmsFrameDecoder())
                                .addLast(new DlmsPushHandler());
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("DLMS/COSEM TCP server listening on port {}", port);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping DLMS/COSEM TCP server");
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * Netty frame decoder that reassembles DLMS WPDU frames from the TCP stream.
     */
    private static class DlmsFrameDecoder extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < WPDU_HEADER_LEN) {
                return;
            }

            in.markReaderIndex();
            in.skipBytes(6);
            int payloadLen = in.readUnsignedShort();
            int totalLen = WPDU_HEADER_LEN + payloadLen;
            in.resetReaderIndex();

            if (in.readableBytes() < totalLen) {
                return;
            }

            byte[] frame = new byte[totalLen];
            in.readBytes(frame);
            out.add(frame);
        }
    }

    private class DlmsPushHandler extends SimpleChannelInboundHandler<byte[]> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, byte[] frame) {
            String remoteAddr = context.channel().remoteAddress().toString();
            log.debug("Received {} bytes from {}", frame.length, remoteAddr);
            try {
                pushReceiver.onFrame(frame, remoteAddr);
            } catch (Exception e) {
                log.error("Error processing DLMS frame from {}: {}", remoteAddr, e.getMessage(), e);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) {
            if (event instanceof IdleStateEvent) {
                log.debug("Idle timeout for {}, closing", context.channel().remoteAddress());
                context.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            log.warn("Exception on channel {}: {}", context.channel().remoteAddress(), cause.getMessage());
            context.close();
        }

        @Override
        public void channelActive(ChannelHandlerContext context) {
            log.info("Meter connected: {}", context.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) {
            log.info("Meter disconnected: {}", context.channel().remoteAddress());
        }
    }
}
