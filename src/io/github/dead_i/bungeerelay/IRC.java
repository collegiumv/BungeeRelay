package io.github.dead_i.bungeerelay;

import net.craftminecraft.bungee.bungeeyaml.bukkitapi.file.FileConfiguration;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IRC {
    public static Socket sock;
    public static BufferedReader in;
    public static PrintWriter out;
    public static FileConfiguration config;
    public static String SID;
    public static String botUID;
    public static String currentUid;
    public static String prefixModes;
    public static String chanModes;
    public static long startTime;
    public static boolean authenticated;
    public static boolean capabState;
    public static HashMap<ProxiedPlayer, Long> times = new HashMap<ProxiedPlayer, Long>();
    public static HashMap<String, Long> nickTimes = new HashMap<String, Long>();
    public static HashMap<ProxiedPlayer, String> uids = new HashMap<ProxiedPlayer, String>();
    public static HashMap<ProxiedPlayer, String> replies = new HashMap<ProxiedPlayer, String>();
    public static HashMap<String, String> users = new HashMap<String, String>();
    public static HashMap<String, Channel> chans = new HashMap<String, Channel>();
    Plugin plugin;

    private static String argModes = "";

    public IRC(Socket s, FileConfiguration c, Plugin p) throws IOException {
        // Yes this is required
        sock = s;
        config = c;
        plugin = p;

        SID = config.getString("server.id");
        botUID = SID + "AAAAAA";
        currentUid = SID + "AAAAAB";
        startTime = System.currentTimeMillis() / 1000;
        authenticated = false;
        capabState = false;

        in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        out = new PrintWriter(sock.getOutputStream(), true);

        // Send our capabilities which we pretend we can do everything
        out.println("CAPAB START 1202");
        out.println("CAPAB CAPABILITIES :PROTOCOL=1202");
        out.println("CAPAB END");
        while (sock.isConnected()) handleData(in.readLine());
    }

    private int countChar(String s, Character c)
    {
        int count = 0;
        for (Character charInString:s.toCharArray()) {
            if (charInString.equals(c)) {
                ++count;
            }
        }
        return count;
    }

    private void doBurst() {
        out.println(":" + SID + " BURST " + startTime);
        out.println(":" + SID + " VERSION :BungeeRelay-0.1");
        out.println(":" + SID + " UID " + botUID + " " + startTime + " " + config.getString("bot.nick") + " BungeeRelay " + config.getString("bot.host") + " " + config.getString("bot.ident") + " BungeeRelay " + startTime + " +o :" + config.getString("bot.realname"));
        out.println(":" + botUID + " OPERTYPE " + config.getString("bot.opertype"));
        out.println(":" + SID + " ENDBURST");
        String chan = config.getString("server.channel");
        String topic = config.getString("server.topic");
        if (chan.isEmpty()) {
            for (ServerInfo si : plugin.getProxy().getServers().values()) {
                chan = config.getString("server.chanprefix") + si.getName();
                Util.sendBotJoin(chan);
                Util.setChannelTopic(chan, topic.replace("{SERVERNAME}", si.getName()));
                for (ProxiedPlayer p : si.getPlayers()) {
                    Util.sendUserConnect(p);
                    Util.sendChannelJoin(p, chan);
                }
            }
        } else {
            Util.sendBotJoin(chan);
            Util.setChannelTopic(chan, topic.replace("{SERVERNAME}", ""));
            for (ProxiedPlayer p : plugin.getProxy().getPlayers()) {
                Util.sendUserConnect(p);
                Util.sendChannelJoin(p, chan);
            }
        }
        chan = config.getString("server.staff");
        if (!chan.isEmpty()) {
            Util.sendBotJoin(chan);
            Util.setChannelTopic(chan, config.getString("server.stafftopic"));
            Util.giveChannelModes(chan, config.getString("server.staffmodes"));
        }
    }

    public void handleData(String data) throws IOException {
        if (data == null) throw new IOException();
        if (data.isEmpty()) return;

        if (config.getBoolean("server.debug")) plugin.getLogger().info("Received: "+data);

        String[] ex = data.trim().split(" ");
        String command, sender;
        if (ex[0].charAt(0) == ':') { // We have a sender
            sender = ex[0].substring(1);
            command = ex[1];
        } else {
            sender = "";
            command = ex[0];
        }

        if (command.equals("ERROR")) {
            sock.close();
            authenticated = false;
            capabState = false;
            throw new IOException(); // This will make us reconnect

        } else if (command.equals("CAPAB")) {
            if (ex[1].equals("START")) {
                capabState = true;

            } else if (!capabState && authenticated) {
                plugin.getLogger().warning("CAPAB *MUST* start with CAPAB START after authentication");
                out.println("ERROR :Received CAPAB command without CAPAB START" + command);

            } else if (ex[1].equals("CAPABILITIES")) {
                // Dynamically find which modes require arguments
                for (String s:ex) {
                    if (s.contains("CHANMODES=")) {
                        chanModes = s.split("=")[1];
                        String[] chanmodeSets = chanModes.split(",");
                        argModes = "";
                        // The first three sets take arguments
                        for (int i = 0; i < 3; ++i) {
                            argModes += chanmodeSets[i];
                        }
                    }
                    if (s.contains("PREFIX=")) {
                        // Grab the modes inside the parens after the "="
                        prefixModes = s.split("=")[1].split("\\(")[1].split("\\)")[0];
                    } 
                }

            } else if (ex[1].equals("END")) {
                capabState = false;
                if (!authenticated) {
                    plugin.getLogger().info("Authenticating with server...");
                    out.println("SERVER " + config.getString("server.servername") + " " + config.getString("server.sendpass") + " 0 " + SID + " :" + config.getString("server.realname"));
                }
            }

        } else if (!authenticated) {
            if (command.equals("SERVER")) {
                if (!ex[2].equals(config.getString("server.recvpass"))) {
                    plugin.getLogger().warning("The server "+ex[1]+" presented the wrong password.");
                    plugin.getLogger().warning("Remember that the recvpass and sendpass are opposite to the ones in your links.conf");
                    out.println("ERROR :Password received was incorrect");
                    sock.close();
                }
                authenticated = true;
                plugin.getLogger().info("Authentication successful");
                plugin.getLogger().info("Bursting");
                doBurst();

            } else {
                plugin.getLogger().warning("Unrecognized command during authentication: " + data);
                out.println("ERROR :Unrecognized command during authentication " + command);
            }

        } else { // We have already authenticated
            if (command.equals("ADDLINE")) {
            } else if (command.equals("AWAY")) {
            } else if (command.equals("BURST")) {
            } else if (command.equals("ENDBURST")) { // We BURST'd first so do nothing
                plugin.getLogger().info("Bursting done");

            } else if (command.equals("FJOIN")) {
                if (!chans.containsKey(ex[2])) {
                    Long ts = Long.parseLong(ex[3]);
                    if (!ts.equals(Util.getChanTS(ex[2]))) chans.get(ex[2]).ts = ts;
                }
                String modes = ex[4];
                int countArgModes = 0;
                for (Character c:argModes.toCharArray()) {
                    countArgModes += countChar (modes, c);
                }
                chans.get(ex[2]).users.add(ex[5+countArgModes].split(",")[1]);
                for (ProxiedPlayer p : Util.getPlayersByChannel(ex[2])) {
                    p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.join")
                            .replace("{SENDER}", users.get(ex[6].split(",")[1])))));
                }

            } else if (command.equals("FMODE")) {
                String s = "";
                String d = "+";
                int v = 5;
                for (int i=0; i<ex[4].length(); i++) {
                    String m = Character.toString(ex[4].charAt(i));
                    String[] cm = chanModes.split(",");
                    if (m.equals("b") && chans.containsKey(ex[2])) chans.get(ex[2]).bans.add(ex[v]);
                    if (m.equals("+") || m.equals("-")) {
                        d = m;
                    }else if (cm[0].contains(m) || cm[1].contains(m) || (cm[2].contains(m) && d.equals("+"))) {
                        s = s + ex[v] + " ";
                        v++;
                    }else if (ex.length > v && users.containsKey(ex[v])) {
                        s = s + users.get(ex[v]) + " ";
                        v++;
                    }
                }
                for (ProxiedPlayer p : Util.getPlayersByChannel(ex[2])) {
                    p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.mode")
                            .replace("{SENDER}", users.get(sender))
                            .replace("{MODE}", ex[4] + " " + s))));
                }
            } else if (command.equals("FTOPIC")) {

            } else if (command.equals("KICK")) {
                String reason = Util.sliceStringArray(ex, 4).substring(1);
                String target = users.get(ex[3]);
                String senderNick = users.get(sender);
                for (ProxiedPlayer p : Util.getPlayersByChannel(ex[2])) {
                    p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.kick")
                            .replace("{SENDER}", sender)
                            .replace("{TARGET}", target)
                            .replace("{REASON}", reason))));
                }
                String full = users.get(ex[3]);
                int prefixlen = config.getString("server.userprefix").length();
                int suffixlen = config.getString("server.usersuffix").length();
                if (config.getBoolean("server.kick") && prefixlen < full.length() && suffixlen < full.length()) {
                    ProxiedPlayer player = plugin.getProxy().getPlayer(full.substring(prefixlen, full.length() - suffixlen));
                    if (player != null) {
                        player.disconnect(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.disconnectkick")
                                .replace("{SENDER}", sender)
                                .replace("{TARGET}", target)
                                .replace("{REASON}", reason))));
                    }
                }
                users.remove(ex[3]);

            } else if (command.equals("METADATA")) {
            } else if (command.equals("OPERTYPE")) {
            } else if (command.equals("PART")) {
                String reason;
                if (ex.length > 3) {
                    reason = Util.sliceStringArray(ex, 3).substring(1);
                } else {
                    reason = "";
                }
                for (ProxiedPlayer p : Util.getPlayersByChannel(ex[2])) {
                    p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.part")
                            .replace("{SENDER}", users.get(sender))
                            .replace("{REASON}", reason))));
                }

            } else if (command.equals("PING")) {
                out.println(":" + SID + " PONG " + SID + " "+ex[2]);

            } else if (command.equals("PRIVMSG")) {
                String from = users.get(sender);
                String player = users.get(ex[2]);
                int prefixlen = config.getString("server.userprefix").length();
                int suffixlen = config.getString("server.usersuffix").length();
                Collection<ProxiedPlayer> players = new ArrayList<ProxiedPlayer>();
                boolean isPM;
                if (player != null && prefixlen + suffixlen < player.length()) {
                    ProxiedPlayer to = plugin.getProxy().getPlayer(player.substring(prefixlen, player.length() - suffixlen));
                    isPM = (users.containsKey(ex[2]) && to != null);
                    if (isPM) {
                        players.add(to);
                        replies.put(to, from);
                    }
                } else {
                    isPM = false;
                }
                if (!isPM) players = Util.getPlayersByChannel(ex[2]);
                for (ProxiedPlayer p : players) {
                    int len;
                    if (ex[3].equals(":" + (char) 1 + "ACTION")) {
                        len = 4;
                    } else {
                        len = 3;
                    }
                    String s = Util.sliceStringArray(ex, len);
                    String out;
                    if (len == 4) {
                        if (isPM) {
                            out = config.getString("formats.privateme");
                        } else {
                            out = config.getString("formats.me");
                        }
                        s = s.replaceAll(Character.toString((char) 1), "");
                    } else {
                        if (isPM) {
                            out = config.getString("formats.privatemsg");
                        } else {
                            out = config.getString("formats.msg");
                        }
                        s = s.substring(1);
                    }
                    p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', out)
                            .replace("{SENDER}", from)
                            .replace("{MESSAGE}", s)));
                }

            } else if (command.equals("QUIT")) {
                String reason;
                if (ex.length > 3) {
                    reason = Util.sliceStringArray(ex, 2).substring(1);
                } else {
                    reason = "";
                }
                for (Map.Entry<String, Channel> ch : chans.entrySet()) {
                    String chan = IRC.config.getString("server.channel");
                    if (chan.isEmpty()) {
                        chan = ch.getKey();
                    }else if (!ch.getKey().equals(chan)) {
                        continue;
                    }
                    if (!ch.getValue().users.contains(sender)) continue;
                    for (ProxiedPlayer p : Util.getPlayersByChannel(chan)) {
                        p.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', config.getString("formats.quit")
                                .replace("{SENDER}", users.get(sender))
                                .replace("{REASON}", reason))));
                    }
                }
                users.remove(sender);

            } else if (command.equals("SERVER")) {
            } else if (command.equals("UID")) {
                users.put(ex[2], ex[4]);
            } else if (command.equals("VERSION")) {
            } else {
                plugin.getLogger().warning("Unrecognized command: " + data);
                out.println("ERROR :Unrecognized command " + command);
            }
        }
    }
}
