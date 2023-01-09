package io.github.diauweb.sculk.service;

import com.google.protobuf.Empty;
import io.github.diauweb.sculk.BlockControlGrpc;
import io.github.diauweb.sculk.BlockControlOuterClass;
import io.github.diauweb.sculk.CommonType;
import io.github.diauweb.sculk.SculkApiMod;
import io.github.diauweb.sculk.mixin.BlockEntityInvoker;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class BlockControlService extends BlockControlGrpc.BlockControlImplBase {
    private MinecraftServer serverInstance;
    public BlockControlService(MinecraftServer serverInstance) {
        this.serverInstance = serverInstance;
    }

    @Override
    public void getBlock(BlockControlOuterClass.GetBlockReq request, StreamObserver<BlockControlOuterClass.GetBlockRsp> responseObserver) {
        long start = System.currentTimeMillis();
        CommonType.BlockPos pos = request.getPos();
        var worldOptional = ServiceUtils.getWorldByKey(serverInstance, request.getWorld());
        if (worldOptional.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("world does not exist").asRuntimeException());
            return;
        }

        var world = worldOptional.get();
        var blockRsp = BlockControlOuterClass.GetBlockRsp.newBuilder();
        var blockPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());

        var blockState = world.getBlockState(blockPos);
        var block = blockState.getBlock();

        blockRsp.setName(Registries.BLOCK.getId(block).toString());

        var blockStateRsp = BlockControlOuterClass.BlockState.newBuilder();
        for (var prop : blockState.getProperties()) {
            blockStateRsp.putState(prop.getName(), blockState.get(prop).toString());
        }
        blockRsp.setState(blockStateRsp);

        if (blockState.hasBlockEntity()) {
            var blockEntity = world.getChunk(blockPos).getBlockEntity(blockPos);
            NbtCompound data = new NbtCompound();

            assert blockEntity != null;
            ((BlockEntityInvoker) blockEntity).callWriteNbt(data);
            blockRsp.setData(ServiceUtils.toRpcNbt(data));
        }

        responseObserver.onNext(blockRsp.build());
        responseObserver.onCompleted();
        SculkApiMod.logger.debug("Calling getBlock took {}ms. Block [{}] is {}", System.currentTimeMillis() - start, blockPos.toShortString(), blockRsp.getName());
    }

    @Override
    public void setBlock(BlockControlOuterClass.SetBlockReq request, StreamObserver<Empty> responseObserver) {
        super.setBlock(request, responseObserver);
    }
}
