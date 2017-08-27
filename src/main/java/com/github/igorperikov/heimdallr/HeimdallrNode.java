package com.github.igorperikov.heimdallr;

import com.github.igorperikov.heimdallr.generated.ClusterStateTO;
import com.github.igorperikov.heimdallr.generated.NodeDefinitionTO;
import com.github.igorperikov.heimdallr.generated.Type;
import com.github.igorperikov.heimdallr.init.ClientBootstrapHelper;
import com.github.igorperikov.heimdallr.init.ServerBootstrapHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

@Slf4j
public class HeimdallrNode {
    private final int port;
    private final UUID label;

    @Getter
    @Setter
    private ClusterStateTO clusterState;

    private InetSocketAddress peerNodeAddress;

    public HeimdallrNode(int port) {
        this.port = port;
        this.label = UUID.randomUUID();
        log.info("My name is {}", label);
    }

    public HeimdallrNode(int port, String peerAddress, int peerPort) {
        this(port);
        this.peerNodeAddress = new InetSocketAddress(peerAddress, peerPort);
    }

    public void start() {
        ScheduledFuture<?> infoPrintingFuture = null;
        EventLoopGroup parentEventLoopGroup = new NioEventLoopGroup(1);
        EventLoopGroup childEventLoopGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = ServerBootstrapHelper.build(parentEventLoopGroup, childEventLoopGroup, port, this);
            Channel serverChannel = b.bind().sync().channel();
            log.info("Current node started listening on port {}", label, port);
            if (peerNodeAddress != null) {
                bootstrapFromPeerNode(childEventLoopGroup);
            } else {
                proceedLoneNode();
            }
            infoPrintingFuture = new ClusterInfoLogger().startPrintingClusterInfo(serverChannel.eventLoop(), this);
            serverChannel.closeFuture().sync();
            log.info("Shutting down node {}", label);
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        } finally {
            releaseResources(infoPrintingFuture, parentEventLoopGroup, childEventLoopGroup);
        }
    }

    private void bootstrapFromPeerNode(EventLoopGroup childEventLoopGroup) throws InterruptedException {
        Bootstrap bootstrap = ClientBootstrapHelper.build(childEventLoopGroup, peerNodeAddress, this);
        ChannelFuture channelFuture = bootstrap.connect();
        log.info("Sending request to peer node");
        ClusterStateTO build = ClusterStateTO.newBuilder().putNodes(label.toString(), getNodeDefinition()).build();
        channelFuture.sync().channel().writeAndFlush(build).sync();
    }

    private void proceedLoneNode() {
        clusterState = ClusterStateTO.newBuilder().putNodes(label.toString(), getNodeDefinition()).build();
    }

    private void releaseResources(
            ScheduledFuture<?> infoPrintingFuture,
            EventLoopGroup parentEventLoopGroup,
            EventLoopGroup childEventLoopGroup
    ) {
        if (infoPrintingFuture != null) {
            infoPrintingFuture.cancel(true);
        }
        parentEventLoopGroup.shutdownGracefully().syncUninterruptibly();
        childEventLoopGroup.shutdownGracefully().syncUninterruptibly();
    }

    public NodeDefinitionTO getNodeDefinition() {
        return NodeDefinitionTO.newBuilder()
                .setTimestamp(Instant.now().toString())
                .setType(Type.LIVE)
                .setLabel(label.toString())
                .setAddress(getNodeAddress().toString())
                .build();
    }

    public void addClusterNode(NodeDefinitionTO nodeDefinition) {
        clusterState.getNodesMap().put(nodeDefinition.getLabel(), nodeDefinition);
    }

    public InetSocketAddress getNodeAddress() {
        // TODO: localhost -> ip detection
        return new InetSocketAddress("localhost", port);
    }
}
