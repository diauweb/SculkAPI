package io.github.diauweb.sculk;

import io.github.diauweb.sculk.service.BlockControlService;
import io.github.diauweb.sculk.service.EntityControlService;
import io.github.diauweb.sculk.service.IntrospectService;
import io.grpc.BindableService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SculkApiMod implements ModInitializer {

    private static final List<Class<? extends BindableService>> serviceRegistry = List.of(
            BlockControlService.class,
            EntityControlService.class,
            IntrospectService.class
    );

    private RpcServer rpcServer;

    public static Logger logger = LogManager.getLogger("sculk-api");
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var services = new ArrayList<BindableService>();
            for (var serviceClazz : serviceRegistry) {
                try {
                    var constructor = serviceClazz.getConstructor(MinecraftServer.class);
                    var instance = constructor.newInstance(server);
                    services.add(instance);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            rpcServer = new RpcServer(services, 25566);
            new Thread(() -> rpcServer.start()).start();
        });
    }
}
