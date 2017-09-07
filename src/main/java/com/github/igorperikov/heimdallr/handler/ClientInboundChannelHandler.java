package com.github.igorperikov.heimdallr.handler;

import com.github.igorperikov.heimdallr.ClusterStateResolver;
import com.github.igorperikov.heimdallr.HeimdallrNode;
import com.github.igorperikov.heimdallr.generated.ClusterStateTO;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ClientInboundChannelHandler extends SimpleChannelInboundHandler<ClusterStateTO> {
    private final HeimdallrNode node;
    private final ClusterStateResolver resolver = new ClusterStateResolver();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ClusterStateTO msg) throws Exception {
        log.info("Peer node answered her cluster state");
        ClusterStateTO resolvedState = resolver.resolve(node.getClusterState(), msg);
        node.setClusterState(resolvedState);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info("Read completed, closing channel");
        ctx.close();
    }
}