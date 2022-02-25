package jp.takpika.aimod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import com.google.gson.Gson;

@Mod(AIMod.MOD_ID)
public class AIMod {
    public static final String MOD_ID = "aimod";
    private static final Logger LOGGER = LogManager.getLogger("aimod");
    public static List<Map<String, Object>> messages = new ArrayList<>();

    public AIMod(){
        MinecraftForge.EVENT_BUS.register(this);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.createContext("/", new aiHttpHandler());
            server.setExecutor(null);
            server.start();
        }catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
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

    static class aiHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            setData(t.getRequestURI());
            String response = getData();
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private void setData(URI uri) {
            Map<String, Object> data = parseURI(uri);
            Minecraft instance = Minecraft.getInstance();
            if (instance.player != null) {
                if (data.containsKey("name") && data.containsKey("message")) {
                    instance.player.chat("/tell " + data.get("name") + " " + data.get("message"));
                }
                if (data.containsKey("x")) {
                    float xRot = instance.player.getXRot();
                    instance.player.setXRot(xRot + (float) data.get("x"));
                }
                if (data.containsKey("y")) {
                    float yRot = instance.player.getYRot() % 360.0f;
                    if (yRot < 0) {
                        yRot += 360.0f;
                    }
                    instance.player.setYRot(yRot + (float) data.get("y"));
                }
                if (data.containsKey("close")) {
                    if ((Boolean) data.get("close")) {
                        System.exit(0);
                    }
                }
                if (data.containsKey("checked")) {
                    int count = 0;
                    for (Map<String, Object> mes: messages) {
                        if (Objects.equals(mes.get("id").toString(), data.get("checked").toString())) {
                            break;
                        }
                        count += 1;
                    }
                    if (count < messages.size()) {
                        messages.remove(count);
                    }
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
            if (instance.level != null && instance.player != null){
                map.put("playing", true);
                Map<String, Object> playerInfo = new HashMap<>();
                playerInfo.put("health", instance.player.getHealth());
                playerInfo.put("name", instance.player.getName().getString());
                playerInfo.put("death", instance.player.isDeadOrDying());
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
                playerInfo.put("screen", instance.screen != null);
                if (instance.screen != null) {
                    Map<String, Object> screenInfo = new HashMap<>();
                    screenInfo.put("pause", instance.screen.isPauseScreen());
                    screenInfo.put("esc", instance.screen.shouldCloseOnEsc());
                    screenInfo.put("edit", instance.screen.getClass() == net.minecraft.client.gui.screens.inventory.SignEditScreen.class || instance.screen.getClass() == net.minecraft.client.gui.screens.inventory.BookEditScreen.class);
                    screenInfo.put("id", instance.screen.getClass().toString());
                    playerInfo.put("screeninfo", screenInfo);
                }
                map.put("player", playerInfo);
            } else {
                map.put("playing", false);
            }
            return gson.toJson(map);
        }

        public Map<String, Object> parseURI(URI rawuri) {
            String uri = rawuri.toString().substring(1);
            Map<String, Object> data = new HashMap<>();
            if (uri.length() > 1){
                if (uri.charAt(0) == '?') {
                    uri = uri.substring(1);
                    String[] qs = uri.split("&");
                    for (String q: qs) {
                        String[] pair = q.split("=");
                        String regex = "^-?[0-9]*$";
                        Pattern p = Pattern.compile(regex);
                        try{
                            pair[1] = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        }catch (Exception ignored){
                        }
                        if (p.matcher(pair[1]).find()) {
                            long num = Long.parseLong(pair[1]);
                            data.put(pair[0], num);
                            continue;
                        }
                        try {
                            Float num = Float.parseFloat(pair[1]);
                            data.put(pair[0], num);
                            continue;
                        }catch (Exception ignored) {
                        }
                        String lower = pair[1].toLowerCase();
                        if (lower.equals("true") || lower.equals("false")){
                            data.put(pair[0], lower.equals("true"));
                            continue;
                        }
                        data.put(pair[0], pair[1]);
                    }
                }
            }
            return data;
        }
    }
}
