package com.deepinmind.bear.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class NettyProxyClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyProxyClient.class);

    @Value("${namespace}")
    private String namespace;

    private EventLoopGroup group;
    private final String adminHost = "it.deepinmind.com";
    private final int adminBridgePort = 10000;
    private final String localHost = "localhost";
    private final int localPort = 8080;

    @PostConstruct
    public void start() {
        group = new NioEventLoopGroup();
        connectControlChannel();
    }

    private void connectControlChannel() {
        new Thread(() -> {
            while (group != null && !group.isShutdown()) {
                try {
                    Bootstrap b = new Bootstrap();
                    b.group(group).channel(NioSocketChannel.class)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .option(ChannelOption.SO_KEEPALIVE, true)
                            .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new ControlHandler());
                        }
                    });
                    
                    logger.info("Connecting Control Channel for: {}", namespace);
                    ChannelFuture f = b.connect(adminHost, adminBridgePort).sync();
                    if (f.isSuccess()) {
                        logger.info(">>> SUCCESS: Control Channel established with Admin at {}:{}", adminHost, adminBridgePort);
                    }
                    f.channel().writeAndFlush(Unpooled.copiedBuffer("REGISTER:" + namespace + "\n", StandardCharsets.UTF_8));
                    f.channel().closeFuture().sync();
                    logger.warn("Control Channel disconnected, retrying...");
                } catch (Exception e) {
                    logger.error("Control Channel Error: {}, retrying in 5s...", e.getMessage());
                    try { TimeUnit.SECONDS.sleep(5); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private void createDataChannel(String connId) {
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new DataBridgeHandler(connId));
            }
        });
        b.connect(adminHost, adminBridgePort).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                f.channel().writeAndFlush(Unpooled.copiedBuffer("BIND:" + connId + "\n", StandardCharsets.UTF_8));
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (group != null) group.shutdownGracefully();
    }

    private class ControlHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            String content = ((ByteBuf) msg).toString(StandardCharsets.UTF_8);
            for (String line : content.split("\n")) {
                if (line.startsWith("NEW_CONN:")) {
                    createDataChannel(line.substring(9).trim());
                }
            }
            ((ByteBuf) msg).release();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                if (((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                    // 发送心跳包
                    ctx.writeAndFlush(Unpooled.copiedBuffer("PING\n", StandardCharsets.UTF_8));
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Control connection exception: {}", cause.getMessage());
            ctx.close();
        }
    }

    private class DataBridgeHandler extends ChannelInboundHandlerAdapter {
        private final String connId;
        private Channel localChannel;

        public DataBridgeHandler(String connId) { this.connId = connId; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 解析并打印请求日志
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                buf.markReaderIndex();
                byte[] bytes = new byte[Math.min(buf.readableBytes(), 256)];
                buf.readBytes(bytes);
                buf.resetReaderIndex();
                String firstLine = new String(bytes, StandardCharsets.UTF_8).split("\n")[0].trim();
                if (!firstLine.isEmpty() && !firstLine.startsWith("BIND:")) {
                    logger.info("Proxy Request [{}]: {}", connId, firstLine);
                }
            }

            if (localChannel != null && localChannel.isActive()) {
                localChannel.writeAndFlush(((ByteBuf)msg).retain());
                return;
            }

            final Object pendingMsg = msg;
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop()).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    localChannel = ch;
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext lctx, ByteBuf lmsg) {
                            ctx.channel().writeAndFlush(lmsg.retain());
                        }
                        @Override
                        public void channelInactive(ChannelHandlerContext lctx) { ctx.close(); }
                    });
                }
            });
            b.connect(localHost, localPort).addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    localChannel.writeAndFlush(((ByteBuf)pendingMsg).retain());
                } else {
                    ctx.close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (localChannel != null) localChannel.close();
        }
    }
}
