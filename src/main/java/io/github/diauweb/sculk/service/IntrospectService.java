package io.github.diauweb.sculk.service;

import com.google.protobuf.Empty;
import io.github.diauweb.sculk.IntrospectGrpc;
import io.github.diauweb.sculk.IntrospectOuterClass;
import io.grpc.stub.StreamObserver;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;

public class IntrospectService extends IntrospectGrpc.IntrospectImplBase {
    public static final int PROTO_VERSION = 1000;
    private final MinecraftServer serverInstance;
    public IntrospectService(MinecraftServer serverInstance) {
        this.serverInstance = serverInstance;
    }
    @Override
    public void ping(Empty request, StreamObserver<IntrospectOuterClass.PingRsp> responseObserver) {
        responseObserver.onNext(IntrospectOuterClass.PingRsp.newBuilder()
                .setGameVersion(SharedConstants.getGameVersion().getName())
                .setProtoVersion(PROTO_VERSION)
                .setDescription("Fabric; sculk-api; ")
                .build());
        responseObserver.onCompleted();
    }
}
