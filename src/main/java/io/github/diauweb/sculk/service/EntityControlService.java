package io.github.diauweb.sculk.service;

import com.google.protobuf.Empty;
import io.github.diauweb.sculk.CommonType;
import io.github.diauweb.sculk.Nbt;
import io.github.diauweb.sculk.proto.EntityControlGrpc;
import io.github.diauweb.sculk.proto.EntityControlOuterClass;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;

import java.util.ArrayList;
import java.util.UUID;

public class EntityControlService extends EntityControlGrpc.EntityControlImplBase {
    private final MinecraftServer serverInstance;
    public EntityControlService(MinecraftServer serverInstance) {
        this.serverInstance = serverInstance;
    }

    private <T> Entity ensureEntity(String uuid, StreamObserver<T> responseObserver) {
        Entity entity = null;
        for (var world : serverInstance.getWorlds()) {
            var ent = world.getEntity(UUID.fromString(uuid));
            if (ent != null) {
                entity = ent;
                break;
            }
        }

        if (entity == null) {
            responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
            return null;
        }

        return entity;
    }

    @Override
    public void listEntity(Empty request, StreamObserver<EntityControlOuterClass.ListEntityRsp> responseObserver) {
        serverInstance.getWorlds().forEach(world -> {
            world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), entity -> true).forEach(e -> {
                responseObserver.onNext(EntityControlOuterClass.ListEntityRsp.newBuilder()
                        .setUuid(e.getUuidAsString())
                        .setType(e.getType().getRegistryEntry().registryKey().getValue().toString())
                        .setWorld(world.getRegistryKey().getValue().toString())
                        .build()
                );
            });
        });
        responseObserver.onCompleted();
    }

    @Override
    public void getEntityField(EntityControlOuterClass.GetEntityFieldReq request, StreamObserver<EntityControlOuterClass.GetEntityFieldRsp> responseObserver) {
        var entity = ensureEntity(request.getUuid(), responseObserver);
        if (entity == null) return;

        NbtCompound nbt = entity.writeNbt(new NbtCompound());

        NbtElement elem;
        try {
            elem = ServiceUtils.getNbtPath(nbt, request.getPath());
        } catch (RuntimeException e) {
            responseObserver.onError(e);
            return;
        }

        assert elem != null;
        responseObserver.onNext(EntityControlOuterClass.GetEntityFieldRsp.newBuilder().setValue(ServiceUtils.toRpcNbt(elem)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void setEntityField(EntityControlOuterClass.SetEntityFieldReq request, StreamObserver<Empty> responseObserver) {
        var entity = ensureEntity(request.getUuid(), responseObserver);
        if (entity == null) return;

        NbtCompound nbt = entity.writeNbt(new NbtCompound());

        try {
            ServiceUtils.putNbtPath(nbt, request.getPath(), ServiceUtils.fromRpcNbt(request.getValue()));
            entity.readNbt(nbt);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void queryEntityField(EntityControlOuterClass.QueryEntityFieldReq request, StreamObserver<EntityControlOuterClass.QueryEntityFieldRsp> responseObserver) {
        var entity = ensureEntity(request.getUuid(), responseObserver);
        if (entity == null) return;

        NbtCompound nbt = entity.writeNbt(new NbtCompound());
        var builder = EntityControlOuterClass.QueryEntityFieldRsp.newBuilder();
        if (request.hasPath()) {
            try {
                NbtElement elem = ServiceUtils.getNbtPath(nbt, request.getPath());
                assert elem != null;
                if (elem.getType() == NbtElement.LIST_TYPE) {
                    var list = (NbtList) elem;
                    for (int i = 0; i < list.size(); i++) {
                        builder.putFields(Integer.toString(i), Nbt.NbtType.forNumber(list.get(i).getType()));
                    }
                    responseObserver.onNext(builder.build());
                    responseObserver.onCompleted();
                    return;
                } else {
                    nbt = (NbtCompound) elem;
                }
            } catch (RuntimeException e) {
                responseObserver.onError(e);
                return;
            }
        }

        final NbtCompound finalNbt = nbt;
        nbt.getKeys().forEach(key -> builder.putFields(key, Nbt.NbtType.forNumber(finalNbt.get(key).getType())));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void newEntity(EntityControlOuterClass.NewEntityReq request, StreamObserver<EntityControlOuterClass.NewEntityRsp> responseObserver) {
        NbtCompound entityNbt;

        if (request.hasFields() && request.getFields().getType() == Nbt.NbtType.TAG_COMPOUND) {
            entityNbt = (NbtCompound) ServiceUtils.fromRpcNbt(request.getFields());
        } else {
            entityNbt = new NbtCompound();
        }

        entityNbt.putString("id", request.getType());
        ServerWorld world = ServiceUtils.getWorldByKey(serverInstance, request.getWorld()).orElseGet(serverInstance::getOverworld);

        Entity entity = EntityType.loadEntityWithPassengers(entityNbt, world, ent -> {
            CommonType.Vec3 pos = request.getPos();
            ent.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), ent.getYaw(), ent.getPitch());
            return ent;
        });

        if (entity instanceof MobEntity mobEntity) {
            mobEntity.initialize(world, world.getLocalDifficulty(entity.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
        }

        assert world != null;
        if (!world.spawnNewEntityAndPassengers(entity)) {
            responseObserver.onError(Status.INTERNAL.asException());
            return;
        }
        responseObserver.onNext(EntityControlOuterClass.NewEntityRsp.newBuilder().setUuid(entity.getUuidAsString()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteEntity(EntityControlOuterClass.DeleteEntityReq request, StreamObserver<Empty> responseObserver) {
        var entity = ensureEntity(request.getUuid(), responseObserver);
        if (entity == null) return;

        entity.discard();
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
