// code 1
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class SslClient {
    private EventLoopGroup eventloopGroop = null;
    private String remoteHost;
    private int remotePort;
    private AtomicBoolean opened = new AtomicBoolean(false);
    private ChannelFuture channelFuture = null;

    public SslClient(String remotehost, int port) {
        this.remoteHost = remotehost;
        this.remotePort = port;
    }

    public void open(EventLoopGroup eventLoopGroup) throws Exception {
        if (opened.compareAndSet(false, true)) {
            eventloopGroop = eventLoopGroup == null ? new NioEventLoopGroup() : eventLoopGroup;
            Bootstrap bootstrap = new Bootstrap();
            final ByteBufClientHandler handler = new ByteBufClientHandler(this);
            bootstrap.group(eventloopGroop).channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            SSLContext sslContext = SSLContext.getDefault();
                            SSLEngine engine = sslContext.createSSLEngine();
                            engine.setUseClientMode(true);

                            pipeline.addLast("ssl", new SslHandler(engine));
                            pipeline.addLast( handler);
                        }
                    });
            channelFuture = bootstrap.connect(this.remoteHost, this.remotePort).sync();
        }
    }

    public void open() throws Exception {
        open(null);
    }

    public void close() {
        if (eventloopGroop != null && opened.compareAndSet(true, false)) {
            eventloopGroop.shutdownGracefully();
        }
    }

    void exceptionCaught(Throwable cause) throws Exception {
        this.close();
        throw new IOException("Disconnected unexpectedly.", cause);
    }

    public void sendMessage(String message) {
        ByteBuf byteBuf = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        channelFuture.channel().writeAndFlush(byteBuf);
    }

    protected static class ByteBufClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        SslClient nettySocketClient;

        public ByteBufClientHandler(SslClient nettySocketClient) {
            this.nettySocketClient = nettySocketClient;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
            this.nettySocketClient.exceptionCaught(cause);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            System.out.println("Something came in");
            CharSequence message = msg.readCharSequence(msg.readableBytes(), StandardCharsets.UTF_8);
            System.out.println("Hey: " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.ssl.trustStore", "/home/hamsterofdeath/IdeaProjects/other/netty_ssl/untitled/client_truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");

        // Replace with your server host and port
        String host = "localhost";
        int port = 1234;

        SslClient client = new SslClient(host, port);

        // Open connection
        client.open();

        // Prepare message
        String message = "Hello, world!";

        // Send message
        client.sendMessage(message);
    }
}