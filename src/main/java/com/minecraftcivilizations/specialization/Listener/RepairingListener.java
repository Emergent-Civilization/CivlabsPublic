package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import static org.bukkit.ChatColor.*;
import static com.minecraftcivilizations.specialization.util.MathUtils.random;
/**
 * @author Jfrogy
 */


/**
 * Blacksmith Repairing System with hunger + EXP cost + skill XP gain
 */
public class RepairingListener implements Listener {

    private static final float VOLUME = 0.8f;
    private static final float PITCH = 0.8f;
    private static final float PITCH_VARIANCE = 0.2f;

    @EventHandler
    public void onSneakRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block clickedBlock = event.getClickedBlock();
        if (!Tag.ANVIL.isTagged(clickedBlock.getType())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!(item.getItemMeta() instanceof Damageable)) return;

        Damageable meta = (Damageable) item.getItemMeta();
        if (!meta.hasDamage() || meta.getDamage() <= 0) {
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
        int level = cp.getSkillLevel(SkillType.BLACKSMITH);
        if (level < 1) {
            return;
        }

        // Repair scales with level: Level 1 = 3, Max = 10
        int repairAmount = 3 + (level - 1); // level 1 -> 3, level 2 -> 4, ..., level 8 -> 10
        repairAmount = Math.min(repairAmount, 10); // cap at 10
        repairAmount = Math.min(repairAmount, meta.getDamage()); // never repair more than item damage

        // Hunger
        int hungerCost = 1;
        if (player.getFoodLevel() < hungerCost) {
            event.setCancelled(true);
            PlayerUtil.message(player, "Food needed to repair this");
            return;
        }

        // XP cost: 1 per repair
        int xpCost = 1;
        if (!PlayerUtil.tryConsumeXp(player, xpCost)) {
            event.setCancelled(true);
            PlayerUtil.message(player,"XP needed to repair this");
            return;
        }

        // --- Cancel equip events if armor ---
        Material type = item.getType();
        if (type.name().contains("HELMET") || type.name().contains("CHESTPLATE") ||
                type.name().contains("LEGGINGS") || type.name().contains("BOOTS")) {
            event.setCancelled(true);
        }

        // --- Apply repair & hunger ---
        meta.setDamage(meta.getDamage() - repairAmount);
        item.setItemMeta(meta);
        player.setFoodLevel(player.getFoodLevel() - hungerCost);
        player.setExhaustion(player.getExhaustion() + 0.2f);

        // --- Grant Blacksmith XP ---
        int blacksmithXp = 1;
        cp.addSkillXp(SkillType.BLACKSMITH, blacksmithXp);


        // --- Feedback ---
        World world = player.getWorld();
        Location loc = player.getLocation();
        world.spawnParticle(Particle.CRIT, clickedBlock.getLocation().add(0.5, 1, 0.5), 6, 0.2, 0.3, 0.2, 0.1);
        world.playSound(loc, Sound.BLOCK_ANVIL_HIT, SoundCategory.PLAYERS, VOLUME, PITCH + random(-PITCH_VARIANCE, PITCH_VARIANCE));
        world.playSound(loc, Sound.BLOCK_COPPER_GRATE_HIT, SoundCategory.PLAYERS, VOLUME, PITCH + random(-PITCH_VARIANCE, PITCH_VARIANCE));
        world.playSound(loc, Sound.BLOCK_COPPER_GRATE_BREAK, SoundCategory.PLAYERS, VOLUME, PITCH + random(-PITCH_VARIANCE, PITCH_VARIANCE));

        //debug
//        player.sendMessage(GREEN + "You repaired " + repairAmount + " durability for " + hungerCost + " hunger and " + xpCost + " XP.");
//        player.sendMessage(LIGHT_PURPLE + "You gained " + blacksmithXp + " Blacksmith skill XP!");
    }
}
