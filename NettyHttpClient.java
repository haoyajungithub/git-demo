import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.HttpRequestEncoder;
import io.netty.handler.codec.http.multipart.HttpResponseDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class NettyHttpClient {

    private static final Bootstrap BOOTSTRAP;
    private static final EventLoopGroup GROUP;

    static {
        GROUP = new NioEventLoopGroup();
        BOOTSTRAP = new Bootstrap();
        BOOTSTRAP.group(GROUP)
                 .channel(NioSocketChannel.class)
                 .handler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) throws Exception {
                         ChannelPipeline pipeline = ch.pipeline();
                         // 添加编码解码器支持HTTP协议
                         pipeline.addLast(new HttpResponseDecoder());
                         pipeline.addLast(new HttpRequestEncoder());
                         // 可以根据需要添加其他处理器，比如日志处理器、业务处理器等
                         // pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                     }
                 });
    }

    public static void sendHttpRequest(String host, int port, String uri, HttpMethod method, String content) throws InterruptedException {
        ChannelFuture connectFuture = BOOTSTRAP.connect(host, port).sync();
        Channel channel = connectFuture.channel();

        // 为每个请求创建一个特定的处理器
        HttpClientRequestHandler requestHandler = new HttpClientRequestHandler(channel, uri, method, content);
        channel.pipeline().addLast(requestHandler);

        // 请求完成后，关闭连接（或根据需要管理连接生命周期）
        // 注意：这可能不是最佳实践，实际应用中可能需要复用连接
        requestHandler.getChannelFuture().addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("Request sent successfully.");
            } else {
                System.err.println("Failed to send request.");
            }
            channel.closeFuture().sync();
        });
    }

    // ... 其他辅助类和主函数代码 ...
}

class HttpClientRequestHandler extends ChannelInboundHandlerAdapter {
    private final Channel channel;
    private final String uri;
    private final HttpMethod method;
    private final String content;
    private ChannelFuture writeFuture;

    public HttpClientRequestHandler(Channel channel, String uri, HttpMethod method, String content) {
        this.channel = channel;
        this.uri = uri;
        this.method = method;
        this.content = content;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        FullHttpRequest request;
        if (HttpMethod.POST.equals(method)) {
            // POST请求处理
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            request.content().writeBytes(content.getBytes(CharsetUtil.UTF_8));
        } else {
            // GET或其他请求处理
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        }
        request.headers().set(HttpHeaderNames.HOST, "example.com");
        writeFuture = ctx.writeAndFlush(request);
    }

    public ChannelFuture getChannelFuture() {
        return writeFuture;
    }
}