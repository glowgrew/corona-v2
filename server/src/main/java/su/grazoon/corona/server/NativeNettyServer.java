package su.grazoon.corona.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.grazoon.corona.api.NettyServer;
import su.grazoon.corona.api.PayloadPacketHandler;
import su.grazoon.corona.api.credentials.ConnectionCredentials;
import su.grazoon.corona.common.CoronaPacketHandler;
import su.grazoon.corona.common.PayloadPacketHandlerImpl;
import su.grazoon.corona.common.packet.AlertPacket;

public class NativeNettyServer implements NettyServer {

    private static final Logger log = LoggerFactory.getLogger(NativeNettyServer.class);

    private final EventLoopGroup bossGroup, workerGroup;
    private final ServerBootstrap bootstrap;
    private final PayloadPacketHandler packetHandler;

    private ChannelFuture channelFuture;

    public NativeNettyServer(int bossThreadsAmount, int workerThreadsAmount) {
        packetHandler = new PayloadPacketHandlerImpl();

        bossGroup = new NioEventLoopGroup(bossThreadsAmount);
        workerGroup = new NioEventLoopGroup(workerThreadsAmount);

        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .option(ChannelOption.SO_BACKLOG, 100)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel socketChannel) {
                         ChannelPipeline pipeline = socketChannel.pipeline();
                         pipeline.addLast("object_encoder", new ObjectEncoder());
                         pipeline.addLast("object_decoder",
                                          new ObjectDecoder(
                                                  ClassResolvers.weakCachingResolver(getClass().getClassLoader())));
                         pipeline.addLast("server_handler", new CoronaPacketHandler(packetHandler));
                     }
                 });

        packetHandler.registerHandler(AlertPacket.class, alertPacket -> log.info(String.valueOf(alertPacket.a)));
    }

    @Override
    public void bind(ConnectionCredentials credentials) {
        try {
            channelFuture = bootstrap.bind(credentials.getHostname(), credentials.getPort()).sync();
            log.info("Corona has started on {}", credentials.getFormattedAddress());
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted.");
        }
    }

    @Override
    public void bindAndLock(ConnectionCredentials credentials) {
        try {
            channelFuture = bootstrap.bind(credentials.getHostname(), credentials.getPort()).sync();
            channelFuture.channel().closeFuture().sync();
            log.info("[LOCKED] Corona has started on {}", credentials.getFormattedAddress());
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted.");
        }
    }

    @Override
    public void shutdown() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public PayloadPacketHandler packetHandler() {
        return packetHandler;
    }
}