package io.github.diauweb.sculk;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.List;

public class RpcServer {

    private final List<BindableService> services;
    private final int port;
    private final Server grpcServer;
    public RpcServer(List<BindableService> services, int port) {
        this.services = List.of(services.toArray(new BindableService[0]));
        this.port = port;
        this.grpcServer = ServerBuilder
                .forPort(port)
                .addServices(services.stream().map(BindableService::bindService).toList())
                .build();
    }

    void start() {
        try {
            grpcServer.start();
            SculkApiMod.logger.info("Started RPC server on port {}", port);
        } catch (IOException e) {
            SculkApiMod.logger.error("Failed to start RPC Service");
        }
    }
}
