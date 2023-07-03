package jp.takpika.aimod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.net.StandardProtocolFamily;

import com.google.gson.Gson;

@Mod(AIMod.MOD_ID)
public class AIMod {
    public static final String MOD_ID = "aimod";
    private static final Logger LOGGER = LogManager.getLogger("aimod");
    public static List<Map<String, Object>> messages = new ArrayList<>();
    volatile ServerSocketChannel serverSocketChannel;

    public AIMod(){
        MinecraftForge.EVENT_BUS.register(this);
        unixServer thread = new unixServer();
        thread.start();
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.contains(" whispers to you: ")){
            String[] messageData = message.split(" whispers to you: ");
            String author = messageData[0];
            String mes = messageData[1];
            LOGGER.info(author + " says: " + mes);
            Map<String, Object> data = new HashMap<>();
            data.put("id", getUNIXTime());
            data.put("author", author);
            data.put("message", mes);
            messages.add(data);
        }
    }

    public Integer getUNIXTime() {
        Date now = new Date();
        long longtime = now.getTime() / 1000;
        return (int) longtime;
    }

    class unixServer extends Thread {
        public void run() {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            long pid = bean.getPid();
            Path path = Paths.get("/tmp/mcai." + pid + ".socket");
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
            Gson gson = new Gson();
            while (true) {
                try {
                    Files.deleteIfExists(path);
                    serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                    serverSocketChannel.bind(address);
                    while (true) {
                        var client = serverSocketChannel.accept();
                        var buffer = ByteBuffer.allocate(1024);
                        client.read(buffer);
                        buffer.flip();
                        var message = StandardCharsets.UTF_8.decode(buffer).toString();
                        System.out.println(message);
                        PostData data = gson.fromJson(message, PostData.class);
                        setData(data);
                        client.write(StandardCharsets.UTF_8.encode(getData()));
                        client.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage());
                }
            }
        }

        private void setData(PostData data) {
            if (data == null) {
                return;
            }
            Minecraft instance = Minecraft.getInstance();
            if (data.pos != null) {
                if (instance.player != null) {
                    float xRot = instance.player.getXRot();
                    float yRot = instance.player.getYRot() % 360.0f;
                    if (yRot < 0) {
                        yRot += 360.0f;
                    }
                    instance.player.setXRot(xRot + data.pos.x);
                    instance.player.setYRot(yRot + data.pos.y);
                }
            }
            if (data.close != null) {
                if (data.close) {
                    System.exit(0);
                }
            }
            if (data.checked != null) {
                int count = 0;
                for (Map<String, Object> mes: messages) {
                    if (Objects.equals(mes.get("id").toString(), data.checked)) {
                        break;
                    }
                    count += 1;
                }
                if (count < messages.size()) {
                    messages.remove(count);
                }
            }
        }

        private String getData() {
            Minecraft instance = Minecraft.getInstance();
            Gson gson = new Gson();
            Map<String, Object> map = new HashMap<>();
            map.put("status", "ok");
            map.put("version", 1);
            map.put("active", instance.isWindowActive());
            map.put("message", messages);
            map.put("screen", instance.screen != null);
            if (instance.screen != null) {
                Map<String, Object> screenInfo = new HashMap<>();
                screenInfo.put("pause", instance.screen.isPauseScreen());
                screenInfo.put("esc", instance.screen.shouldCloseOnEsc());
                screenInfo.put("edit", instance.screen.getClass() == net.minecraft.client.gui.screens.inventory.SignEditScreen.class || instance.screen.getClass() == net.minecraft.client.gui.screens.inventory.BookEditScreen.class);
                screenInfo.put("id", instance.screen.getClass().getName());
                map.put("screenInfo", screenInfo);
            }
            if (instance.level != null && instance.player != null){
                map.put("playing", true);
                Map<String, Object> playerInfo = new HashMap<>();
                playerInfo.put("health", instance.player.getHealth());
                playerInfo.put("name", instance.player.getName().getString());
                playerInfo.put("death", instance.player.isDeadOrDying());
                playerInfo.put("gamemode", instance.gameMode.getPlayerMode().name());
                Map<String, Object> pos = new HashMap<>();
                pos.put("x", instance.player.position().x);
                pos.put("y", instance.player.position().y);
                pos.put("z", instance.player.position().z);
                playerInfo.put("pos", pos);
                Map<String, Object> dir = new HashMap<>();
                dir.put("x", instance.player.getRotationVector().x);
                float y = instance.player.getRotationVector().y % 360.0f;
                if (y < 0) {
                    y += 360.0f;
                }
                dir.put("y", y);
                playerInfo.put("direction", dir);
                map.put("player", playerInfo);
            } else {
                map.put("playing", false);
            }
            return gson.toJson(map);
        }
    }
}
