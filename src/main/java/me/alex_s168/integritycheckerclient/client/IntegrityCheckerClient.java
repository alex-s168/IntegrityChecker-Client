package me.alex_s168.integritycheckerclient.client;

import com.google.common.reflect.ClassPath;
import io.netty.buffer.Unpooled;
import me.alex_s168.integritycheckerclient.Compression;
import me.alex_s168.integritycheckerclient.IntegrityChecker;
import me.alex_s168.integritycheckerclient.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;

import java.util.*;
import java.util.stream.Stream;

public class IntegrityCheckerClient implements ClientModInitializer {

    private Stream<String[]> getClasses() {
        try {
            ClassPath classPath = ClassPath.from(IntegrityCheckerClient.class.getClassLoader());
            Set<ClassPath.ClassInfo> classes = classPath.getAllClasses();
            return classes.stream().map((it) -> it.getName().split("\\."));
        } catch (Exception e) {
            return Stream.<String[]>builder().build();
        }
    }

    private static int diff(String[] x, String[] to) {
        if (x.length != to.length) {
            return Math.abs(x.length - to.length);
        }
        for (int i = 0; i < x.length; i++) {
            if (!x[i].equals(to[i])) {
                return i;
            }
        }
        return 0;
    }

    private static List<String> generateDirectoryStructure(List<String[]> classes) {
        List<String> output = new ArrayList<>();
        String[] current = new String[0];

        for (String[] clazz : classes) {
            String[] pack = Arrays.copyOfRange(clazz, 0, clazz.length - 1);
            String name = clazz[clazz.length - 1];

            int diff = diff(pack, current);
            if (diff == 0) { // same package
                output.add(name);
            } else {
                for (int i = 0; i < current.length - diff; i++) {
                    output.add("-");
                }
                for (int i = 0; i < diff; i++) {
                    output.add("+" + pack[diff + i]);
                }
                output.add(name);
                current = pack;
            }
        }
        for (int i = 0; i < current.length; i++) {
            output.add("-");
        }
        return output;
    }

    private boolean startsWith(String[] x, String[] to) {
        if (x.length < to.length) {
            return false;
        }
        for (int i = 0; i < to.length; i++) {
            if (!x[i].equals(to[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] removeEmpty(String[] arr) {
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list.toArray(new String[0]);
    }

    @Override
    public void onInitializeClient() {

        ClientPlayNetworking.registerGlobalReceiver(Packets.PACKET_SERVER_REQUEST_ID, (client, handler, buf, responseSender) -> {
            // process exempt list
            int count = buf.readVarInt();
            List<String[]> exempt = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String unsplit = buf.readString();
                String[] split = unsplit.split("\\.");
                exempt.add(removeEmpty(split));
            }

            List<String[]> classes = getClasses().filter((it) -> {
                // filter classes that start with something in the exempt list
                for (String[] ex : exempt) {
                    if (startsWith(it, ex)) {
                        return false;
                    }
                }
                return true;
            }).toList();
            try {
                String output = String.join(";", generateDirectoryStructure(classes));
                System.out.println(output);
                byte[] compressed = Compression.compress(output);

                PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                out.writeVarInt(compressed.length);
                System.out.println("Sending " + compressed.length + " bytes");
                out.writeBytes(compressed);
                ClientPlayNetworking.send(Packets.PACKET_CLIENT_SEND_ID, out);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((ClientPlayNetworkHandler handler,
                                                  PacketSender sender,
                                                  MinecraftClient client) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(IntegrityChecker.VERSION);
            ClientPlayNetworking.send(Packets.PACKET_CLIENT_USES_ICHECK_ID, buf);
        });

    }
}
