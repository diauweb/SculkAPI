package io.github.diauweb.sculk.service;

import com.google.protobuf.ByteString;
import io.github.diauweb.sculk.Nbt;
import io.github.diauweb.sculk.SculkApiMod;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Objects;
import java.util.Optional;

public final class ServiceUtils {

    public static void putNbtPath(NbtCompound source, Nbt.Path path, NbtElement modification) {
        NbtElement current = source;
        for (int i = 0; i < path.getPathList().size() - 1; i++) {
            var key = path.getPath(i);
            assert current != null;
            switch (current.getType()) {
                case NbtElement.COMPOUND_TYPE -> {
                    var obj = (NbtCompound) current;
                    if (!obj.contains(key)) {
                        throw new RuntimeException("cannot find key " + key);
                    }
                    current = obj.get(key);
                }
                case NbtElement.LIST_TYPE -> {
                    assert current instanceof NbtList;
                    var list = (NbtList) current;
                    int index = Integer.parseInt(key);
                    if (index >= list.size()) {
                        throw new RuntimeException("cannot find list index " + key);
                    }
                    current = list.get(index);
                }
                default -> throw new RuntimeException("cannot find non-compound" + key);
            }
        }

        var target = path.getPath(path.getPathCount() - 1);
        if (current instanceof NbtCompound obj) {
            obj.put(target, modification);
        } else if (current instanceof NbtList list) {
            list.set(Integer.parseInt(target), modification);
        } else {
            throw new RuntimeException("cannot put into non-compound "  + target);
        }
    }

    public static NbtElement getNbtPath(NbtCompound source, Nbt.Path path) {
        NbtElement current = source;
        if (path.getPathCount() == 0) return source;

        for (int i = 0; i < path.getPathList().size() - 1; i++) {
            var key = path.getPath(i);
            assert current != null;
            switch (current.getType()) {
                case NbtElement.COMPOUND_TYPE -> {
                    var obj = (NbtCompound) current;
                    if (!obj.contains(key)) {
                        throw new RuntimeException("cannot find key " + key);
                    }
                    current = obj.get(key);
                }
                case NbtElement.LIST_TYPE -> {
                    assert current instanceof NbtList;
                    var list = (NbtList) current;
                    int index = Integer.parseInt(key);
                    if (index >= list.size()) {
                        throw new RuntimeException("cannot find list index " + key);
                    }
                    current = list.get(index);
                }
                default -> throw new RuntimeException("cannot put into non-compound" + key);
            }
        }

        var target = path.getPath(path.getPathCount() - 1);
        if (current instanceof NbtCompound obj) {
            return obj.get(target);
        } else if (current instanceof NbtList list) {
            return list.get(Integer.parseInt(target));
        }
        return null;
    }

    public static Nbt.NbtElement toRpcNbt(NbtElement e) {
        var builder = Nbt.NbtElement.newBuilder();
        switch (e.getType()) {
            case NbtElement.BYTE_TYPE,
                    NbtElement.SHORT_TYPE,
                    NbtElement.INT_TYPE,
                    NbtElement.LONG_TYPE -> {
                AbstractNbtNumber number = (AbstractNbtNumber) e;
                builder.setType(Nbt.NbtType.forNumber(e.getType())).setIntValue(number.longValue());
            }
            case NbtElement.FLOAT_TYPE, NbtElement.DOUBLE_TYPE -> {
                AbstractNbtNumber number = (AbstractNbtNumber) e;
                builder.setType(Nbt.NbtType.forNumber(e.getType())).setFloatValue(number.doubleValue());
            }
            case NbtElement.BYTE_ARRAY_TYPE -> {
                NbtByteArray arr = (NbtByteArray) e;
                builder.setType(Nbt.NbtType.TAG_BYTE_ARRAY).setArrayValue(ByteString.copyFrom(arr.getByteArray()));
            }
            case NbtElement.INT_ARRAY_TYPE -> {
                NbtIntArray arr = (NbtIntArray) e;
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     DataOutputStream      dos = new DataOutputStream(bos)) {
                    for (int i : arr.getIntArray()) {
                        dos.write(i);
                    }
                    builder.setType(Nbt.NbtType.TAG_INT_ARRAY).setArrayValue(ByteString.copyFrom(bos.toByteArray()));
                } catch(IOException ioe) {
                    SculkApiMod.logger.error("failed to serialize int array", ioe);
                }
            }
            case NbtElement.LONG_ARRAY_TYPE -> {
                NbtLongArray arr = (NbtLongArray) e;
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     DataOutputStream      dos = new DataOutputStream(bos)) {
                    for (long i : arr.getLongArray()) {
                        dos.writeLong(i);
                    }
                    builder.setType(Nbt.NbtType.TAG_LONG_ARRAY).setArrayValue(ByteString.copyFrom(bos.toByteArray()));
                } catch(IOException ioe) {
                    SculkApiMod.logger.error("failed to serialize long array", ioe);
                }
            }
            case NbtElement.LIST_TYPE -> {
                NbtList list = (NbtList) e;
                builder.setType(Nbt.NbtType.TAG_LIST);
                var listBuilder = Nbt.NbtList.newBuilder();
                list.forEach(elem -> listBuilder.addList(toRpcNbt(elem)));
                builder.setListValue(listBuilder);
            }
            case NbtElement.STRING_TYPE -> {
                NbtString str = (NbtString) e;
                builder.setType(Nbt.NbtType.TAG_STRING).setStringValue(str.asString());
            }
            case NbtElement.COMPOUND_TYPE -> {
                NbtCompound comp = (NbtCompound) e;
                builder.setType(Nbt.NbtType.TAG_COMPOUND);
                var compoundBuilder = Nbt.NbtCompound.newBuilder();
                comp.getKeys().forEach(key -> {
                    compoundBuilder.putObject(key, toRpcNbt(Objects.requireNonNull(comp.get(key))));
                });
                builder.setCompoundValue(compoundBuilder);
            }
            default -> throw new RuntimeException("invalid nbt type " + e.getType());
        }
        return builder.build();
    }

    public static NbtElement fromRpcNbt(Nbt.NbtElement elem) {
        return switch (elem.getType()) {
            case TAG_BYTE -> NbtByte.of((byte) elem.getIntValue());
            case TAG_SHORT -> NbtShort.of((short) elem.getIntValue());
            case TAG_INT -> NbtInt.of((int) elem.getIntValue());
            case TAG_LONG -> NbtLong.of(elem.getIntValue());
            case TAG_FLOAT -> NbtFloat.of((float) elem.getFloatValue());
            case TAG_DOUBLE -> NbtDouble.of(elem.getFloatValue());
            case TAG_BYTE_ARRAY -> new NbtByteArray(elem.getArrayValue().toByteArray());
            case TAG_STRING -> NbtString.of(elem.getStringValue());
            case TAG_LIST -> {
                NbtList list = new NbtList();
                elem.getListValue().getListList().forEach(e -> list.add(fromRpcNbt(e)));
                yield list;
            }
            case TAG_COMPOUND -> {
                NbtCompound obj = new NbtCompound();
                elem.getCompoundValue().getObjectMap().forEach((key, value) -> obj.put(key, fromRpcNbt(value)));
                yield obj;
            }
            case TAG_INT_ARRAY -> {
                IntBuffer intBuffer = ByteBuffer.wrap(elem.getArrayValue().toByteArray()).asIntBuffer();
                int[] array = new int[intBuffer.remaining()];
                intBuffer.get(array);
                yield new NbtIntArray(array);
            }
            case TAG_LONG_ARRAY -> {
                LongBuffer longBuffer = ByteBuffer.wrap(elem.getArrayValue().toByteArray()).asLongBuffer();
                long[] array = new long[longBuffer.remaining()];
                longBuffer.get(array);
                yield new NbtLongArray(array);
            }
            default -> null;
        };
    }

    public static Optional<ServerWorld> getWorldByKey(@NotNull MinecraftServer serverInstance, String str) {
        var optionalWorld = serverInstance.getWorldRegistryKeys().stream()
                .filter(i -> i.getValue().toString().equals(str))
                .findAny();
        return optionalWorld.map(serverInstance::getWorld);
    }
}
