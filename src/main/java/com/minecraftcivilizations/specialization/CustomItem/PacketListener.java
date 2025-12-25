package com.minecraftcivilizations.specialization.CustomItem;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.minecraftcivilizations.specialization.Command.EmoteManager;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * @author Jfrogy & Alectriciti
 */

public class PacketListener extends PacketAdapter {

    EmoteManager emoteCommand;

    public PacketListener(EmoteManager command) {
        super(Specialization.getInstance(),
                PacketType.Play.Server.NAMED_SOUND_EFFECT,
                PacketType.Play.Server.SYSTEM_CHAT
        );

        this.emoteCommand = command;
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {

        // --- Block sleep messages ---
        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT) {
            var comp = event.getPacket().getChatComponents().read(0);
            if (comp != null) {
                String json = comp.getJson();
                if (json != null && (json.contains("sleep") || json.contains("Sleeping"))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        Set<Player> silenced_players = emoteCommand.getSilencedPlayers();
        if(silenced_players.isEmpty()){
            Debug.broadcast("packet", "<gold>No Silenced Players</gold>");
            return;
        }

//        Debug.broadcast("packet", "<green>packet send to " + event.getPlayer().getName() + ":</green> " + event.getPacketType().name());



        if (event.getPacketType() != PacketType.Play.Server.NAMED_SOUND_EFFECT) return;

        Player player = event.getPlayer();
//        ItemStack item = player.getInventory().getItemInMainHand();
//        if (!item.getType().equals(Material.CROSSBOW)) return;

        try {
            Object packet = event.getPacket().getHandle(); // NMS packet

            // Sound name
            java.lang.reflect.Field soundField = packet.getClass().getDeclaredField("sound");
            soundField.setAccessible(true);
            Object holder = soundField.get(packet); // Holder<SoundEvent>
            String soundName = "";
            if (holder != null) {
                java.lang.reflect.Method unwrapKey = holder.getClass().getMethod("unwrapKey");
                Object key = unwrapKey.invoke(holder);
                if (key != null) soundName = key.toString(); // e.g., minecraft:item.crossbow.loading_middle
            }

            if (!soundName.contains("crossbow")) {
                return;
            }

            // Location
            java.lang.reflect.Field xField = packet.getClass().getDeclaredField("x");
            java.lang.reflect.Field yField = packet.getClass().getDeclaredField("y");
            java.lang.reflect.Field zField = packet.getClass().getDeclaredField("z");
            xField.setAccessible(true);
            yField.setAccessible(true);
            zField.setAccessible(true);

            int xRaw = xField.getInt(packet);
            int yRaw = yField.getInt(packet);
            int zRaw = zField.getInt(packet);

            double x = xRaw / 8.0;
            double y = yRaw / 8.0;
            double z = zRaw / 8.0;

            for(Player silent_player : silenced_players){
                if(silent_player.getLocation().distance(new Location(player.getWorld(), x, y, z)) < 1){
                    //player nearby is silenced
                    Debug.broadcast("packet", "<dark_red>cancelled sound to " + event.getPlayer().getName() + "</dark_red> "+" <red>sound:</red> "+soundName);
                    event.setCancelled(true);
                    return;
                }
            }

//            player.sendMessage("Sound packet: " + soundName + " | Location: " + x + ", " + y + ", " + z);

        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | java.lang.reflect.InvocationTargetException e) {
            e.printStackTrace();
        }

        // Cancel if holding an EmoteItem
//        CustomItem custom_item = Specialization.getInstance().getCustomItemManager().getCustomItem(item);
//        if (custom_item instanceof EmoteItem) {
//            event.setCancelled(true);
//        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Only process SOUND_EFFECT packets

        Debug.broadcast("packet", "<blue>packet receive:</blue> "+event.getPacketType().name() + event.getPlayer());

        if (event.getPacketType() != PacketType.Play.Server.NAMED_SOUND_EFFECT) return;

        Player player = event.getPlayer();

        if(!player.getEquipment().getItemInMainHand().getType().equals(Material.CROSSBOW)) return;


        // Here you can filter only your emote crossbows
        // For example, check if player is holding an EmoteItem of type CLAP
        ItemStack item = player.getInventory().getItemInMainHand();
        CustomItem custom_item = Specialization.getInstance().getCustomItemManager().getCustomItem(item);
        if(custom_item instanceof EmoteItem emote_item) {
            event.setCancelled(true); // suppress the sound for this packet
        }
    }
}
