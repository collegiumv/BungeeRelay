package io.github.dead_i.bungeerelay;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;

public class Util {
    private static ProxyServer proxy = ProxyServer.getInstance();

    public static void incrementUid(int pos) {
        StringBuilder sb = new StringBuilder(IRC.currentUid);
        if (IRC.currentUid.charAt(pos) == 'Z') {
            sb.setCharAt(pos, '0');
            IRC.currentUid = sb.toString();
        } else if (IRC.currentUid.charAt(pos) == '9') {
            sb.setCharAt(pos, 'A');
            IRC.currentUid = sb.toString();
            if (pos == 3) return;
            incrementUid(pos - 1);
        } else {
            sb.setCharAt(pos, (char) (IRC.currentUid.charAt(pos) + 1));
            IRC.currentUid = sb.toString();
        }
    }

    public static void incrementUid() {
        do {
            incrementUid(8);
        } while (IRC.uids.containsValue(IRC.currentUid));
    }

    public static void sendUserConnect(ProxiedPlayer player) {
        String playerUID = IRC.uids.get(player);
        User user = IRC.users.get(playerUID);
        IRC.out.println(":" + IRC.SID + " UID " + playerUID + " " + user.nickTime + " " + user.nick + " " + player.getAddress().getHostName() + " " + player.getAddress().getHostName() + " " + IRC.config.getString("formats.ident").replace("{IDENT}", player.getName()) + " " + player.getAddress().getHostString() + " " + user.signonTime + " +r :Minecraft Player");
    }

    public static void sendChannelJoin(ProxiedPlayer player) {
        IRC.out.println(":" + IRC.SID + " FJOIN " + IRC.channel + " " + IRC.channelTS + " + :," + IRC.uids.get(player));
    }

    public static void sendAll(String message) {
        for (ProxiedPlayer player : plugin.getProxy().getPlayers() {
            player.sendMessage(message);
        }
    }

    public static void updateTS(String ts) {
        long timestamp = Long.parseLong(ts).longValue();
        if (timestamp < IRC.channelTS) {
            IRC.channelTS = timestamp;
        }
    }

    public static Collection<ProxiedPlayer> getPlayersByChannel(String c) {
            return proxy.getPlayers();
    }

    public static String getUidByNick(String nick) {
        for (Map.Entry<String, String> entry : IRC.users.entrySet()) {
            if (nick.equalsIgnoreCase(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public static String sliceStringArray(String[] a, Integer l) {
        String s = "";
        for (int i=l; i<a.length; i++) {
            s += a[i] + " ";
        }
        return s.trim();
    }
}
