package com.minecraftcivilizations.specialization.Reinforcement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ReinforcementManager {

    public static final NamespacedKey namespacedKey = new NamespacedKey(Specialization.getInstance(), "reinforcedBlocks");

    private static final Map<Vector, Long> lastTimeSpawnedParticle = new HashMap<>();
    private static final Map<Chunk, Set<Reinforcement>> cachedReinforcements = new HashMap<>();
    private static final Map<Chunk, Long> cacheTime = new HashMap<>();
    private static final Map<Chunk, Integer> chunkIndices = new HashMap<>();

    private static final long cooldown = 1000L; // per-block particle cooldown
    private static final long CACHE_EXPIRE_MS = 2 * 60 * 1000L; // 2 minutes
    private static final int CHUNK_RADIUS = 3; //scan radius around player to show particles


    /**
     * Optimized by Jfrogy and redisiged by Jfrogy
     */

    // --------------------- PARTICLE STREAMING ---------------------
    public static void startReinforcement() {

        // --- Player scan every 3 seconds ---
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<Chunk, Long>> it = cacheTime.entrySet().iterator();

                // Clean old cache
                while (it.hasNext()) {
                    Map.Entry<Chunk, Long> e = it.next();
                    if (now - e.getValue() > CACHE_EXPIRE_MS) {
                        cachedReinforcements.remove(e.getKey());
                        chunkIndices.remove(e.getKey());
                        it.remove();
                    }
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingReinforcementItem(player)) continue;

                    Chunk playerChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = playerChunk.getX() + dx;
                            int cz = playerChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            if (!cachedReinforcements.containsKey(chunk)) {
                                Set<Reinforcement> set = getReinforcedBlocks(chunk);
                                if (set != null && !set.isEmpty()) {
                                    cachedReinforcements.put(chunk, set);
                                    cacheTime.put(chunk, now);
                                }
                            } else {
                                cacheTime.put(chunk, now); // refresh cache activity
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(Specialization.getInstance(), 0L, 60L); // every 3 seconds (60 ticks)

        // --- Particle update every tick ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingReinforcementItem(player)) continue;

                    Chunk baseChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = baseChunk.getX() + dx;
                            int cz = baseChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            Set<Reinforcement> set = cachedReinforcements.get(chunk);
                            if (set == null || set.isEmpty()) continue;

                            List<Reinforcement> list = new ArrayList<>(set);
                            int index = chunkIndices.getOrDefault(chunk, 0);

                            int batchSize = list.size() > 25 ? 3 : 1; // batch 3 if >25 reinforced blocks
                            for (int i = 0; i < batchSize; i++) {
                                index = (index + 1) % list.size();
                                Reinforcement r = list.get(index);
                                spawnParticle(player, r);
                            }

                            chunkIndices.put(chunk, index);
                        }
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 1L); // every tick
    }

        private static void spawnParticle(Player player, Reinforcement r) {
        long now = System.currentTimeMillis();
        synchronized (lastTimeSpawnedParticle) {
            Long last = lastTimeSpawnedParticle.get(r.location());
            if (last != null && now - last < cooldown) return;
            lastTimeSpawnedParticle.put(r.location(), now);
        }

        Block b = player.getWorld().getBlockAt(r.location().getBlockX(), r.location().getBlockY(), r.location().getBlockZ());
        Location base = b.getLocation().add(0.5, 0.5, 0.5);

        double offset = 0.55;
            double random_a = Math.random()*0.33;
            double random_b = Math.random()*0.33;
        Vector[] dirs = {
                new Vector(offset, random_a, random_b),
                new Vector(-offset, random_b, random_a),
                new Vector(random_a, offset, random_b),
                new Vector(random_b, -offset, random_a),
                new Vector(random_a, random_b, offset),
                new Vector(random_b, random_a, -offset)
        };

        Particle.DustOptions dust = r.isHeavy() ? new Particle.DustOptions(Color.fromRGB(150,150, 150), 1.6f)
                : new Particle.DustOptions(Color.fromRGB(250,150, 100), 1.0f);



        for (Vector v : dirs) {
            Location loc = base.clone().add(v);
//                    .add(Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05);
//            double velX = (Math.random() - 0.5) * 0.02;
//            double velY = (Math.random() - 0.5) * 0.02;
//            double velZ = (Math.random() - 0.5) * 0.02;



            // REDSTONE particle with no gravity and slight drift
            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust, true);
        }
    }



    // --------------------- ITEM CHECK ---------------------
    private static boolean isHoldingReinforcementItem(Player player) {
        if (player == null) return false;
        player.getInventory().getItemInMainHand();
        Material type = player.getInventory().getItemInMainHand().getType();
        return type == Material.IRON_INGOT || type == Material.COPPER_INGOT;
    }


    // --------------------- REINFORCEMENT METHODS ---------------------
    public static boolean addReinforcement(Player player, Block block, boolean isHeavy) {
        if (isHeavy ? isHeavilyReinforced(block) : (isLightlyReinforced(block) || isHeavilyReinforced(block)))
            return false;

        Chunk chunk = block.getChunk();
        Set<Reinforcement> blocks = getReinforcedBlocks(chunk);
        if (blocks == null) blocks = new HashSet<>();
        if (!blocks.add(new Reinforcement(block.getLocation().toVector(), isHeavy))) return false;

        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(blocks));
        cachedReinforcements.put(chunk, blocks);
        cacheTime.put(chunk, System.currentTimeMillis());

        Player target = player != null ? player : block.getWorld().getNearbyPlayers(block.getLocation(), 4.0)
                .stream().findFirst().orElse(null);
        if (target != null) {
            CustomPlayer cp = CoreUtil.getPlayer(target.getUniqueId());
            if (cp != null) cp.addSkillXp(SkillType.BUILDER, isHeavy ? 15.0 : 5.0);
        }
        return true;
    }

    public static boolean addReinforcement(Block block, boolean isHeavy) {
        return addReinforcement(null, block, isHeavy);
    }

    public static boolean addReinforcementSilent(Block b, boolean h) {
        if (h ? isHeavilyReinforced(b) : (isLightlyReinforced(b) || isHeavilyReinforced(b))) return false;
        Chunk c = b.getChunk();
        Set<Reinforcement> r = getReinforcedBlocks(c);
        if (r == null) r = new HashSet<>();
        if (!r.add(new Reinforcement(b.getLocation().toVector(), h))) return false;
        c.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(r));
        cachedReinforcements.put(c, r);
        cacheTime.put(c, System.currentTimeMillis());
        return true;
    }

    public static void removeReinforcement(Block block) {
        Chunk chunk = block.getChunk();
        Set<Reinforcement> reinforcedBlocks = getReinforcedBlocks(chunk);
        if (reinforcedBlocks == null) return;
        reinforcedBlocks.remove(new Reinforcement(block.getLocation().toVector(), false));
        reinforcedBlocks.remove(new Reinforcement(block.getLocation().toVector(), true));
        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(reinforcedBlocks));
        cachedReinforcements.put(chunk, reinforcedBlocks);
        cacheTime.put(chunk, System.currentTimeMillis());
    }

    public static boolean isReinforced(Block block) {
        return getReinforcement(block) != null;
    }

    public static boolean isHeavilyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.isHeavy();
    }

    public static boolean isLightlyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && !r.isHeavy();
    }

    private static Reinforcement getReinforcement(Block block) {
        Set<Reinforcement> blocks = getReinforcedBlocks(block.getChunk());
        if (blocks == null) return null;
        for (Reinforcement r : blocks) {
            if (r.location().getBlockX() == block.getX() &&
                    r.location().getBlockY() == block.getY() &&
                    r.location().getBlockZ() == block.getZ()) {
                return r;
            }
        }
        return null;
    }

    private static Set<Reinforcement> getReinforcedBlocks(Chunk chunk) {
        if (cachedReinforcements.containsKey(chunk)) {
            cacheTime.put(chunk, System.currentTimeMillis());
            return cachedReinforcements.get(chunk);
        }
        if (!chunk.getPersistentDataContainer().has(namespacedKey)) return null;
        String s = chunk.getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
        Set<Reinforcement> set = new Gson().fromJson(s, new TypeToken<Set<Reinforcement>>() {}.getType());
        if (set != null && !set.isEmpty()) {
            cachedReinforcements.put(chunk, set);
            cacheTime.put(chunk, System.currentTimeMillis());
        }
        return set;
    }
}
