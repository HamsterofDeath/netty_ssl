import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;

public class SslServer {

    private final int port;

    public SslServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        new SslServer(1234).start();
    }

    private void start() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        char[] password = "123456".toCharArray();  // replace with your actual keystore password
        FileInputStream fis = new FileInputStream("keystore.jks");  // replace with your actual keystore file path
        ks.load(fis, password);
        fis.close();

        System.setProperty("javax.net.ssl.keyStore", "/home/hamsterofdeath/IdeaProjects/other/netty_ssl/untitled/keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        kmf.init(ks, password);

        SslContext sslCtx = SslContextBuilder.forServer(kmf).build();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(port)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast("ssl", sslCtx.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    });

            ChannelFuture f = b.bind().sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private static class ServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                String receivedMessage = byteBuf.toString(Charset.defaultCharset());
                System.out.println("Received message:\n" + receivedMessage.trim());

                // Modify the message or prepare the response
                String responseMessage = "Response: " + receivedMessage;

                // Convert the response message to a ByteBuf
                ByteBuf responseByteBuf = Unpooled.copiedBuffer(responseMessage, Charset.defaultCharset());

                // Write the response ByteBuf back to the client
                ctx.writeAndFlush(responseByteBuf);
                System.out.println("SENT!");
            } else {
                System.out.println("Received unknown message type: " + msg.getClass());
            }
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
