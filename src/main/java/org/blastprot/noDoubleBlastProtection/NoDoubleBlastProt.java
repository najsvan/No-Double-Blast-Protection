package org.blastprot.noDoubleBlastProtection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockExplodeEvent;
import java.util.*;

public class NoDoubleBlastProt extends JavaPlugin implements Listener {

    private final Map<UUID, Long> lastKnockbackTime = new HashMap<>();
    private final long KNOCKBACK_COOLDOWN = 50;

    private final Map<Location, Long> recentExplosions = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            recentExplosions.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
        }, 0L, 1200L);

        getLogger().info("NoDoubleBlastProt enabled - Catches ALL explosions");
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Location explosionLoc = event.getLocation();
        trackExplosion(explosionLoc);

        for (Entity entity : event.getEntity().getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;

                double distance = player.getLocation().distance(explosionLoc);
                if (distance > 8) continue;

                if (hasEnoughBlastProtection(player)) {
                    applyKnockback(player, explosionLoc);
                }
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Location explosionLoc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        trackExplosion(explosionLoc);

        for (Entity entity : event.getBlock().getWorld().getNearbyEntities(explosionLoc, 8, 8, 8)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;

                double distance = player.getLocation().distance(explosionLoc);
                if (distance > 8) continue;

                if (hasEnoughBlastProtection(player)) {
                    applyKnockback(player, explosionLoc);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();


        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {


            Location explosionLoc = findClosestExplosion(player.getLocation());
            if (explosionLoc == null) {
                explosionLoc = player.getLocation();
            }

            if (hasEnoughBlastProtection(player)) {
                applyKnockback(player, explosionLoc);
            }
        }
    }

    private void trackExplosion(Location location) {
        recentExplosions.put(location, System.currentTimeMillis());
    }

    private Location findClosestExplosion(Location playerLoc) {
        Location closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Location explosionLoc : recentExplosions.keySet()) {
            double dist = playerLoc.distance(explosionLoc);
            if (dist < closestDist && dist < 10) {
                closestDist = dist;
                closest = explosionLoc;
            }
        }

        return closest;
    }

    private boolean hasEnoughBlastProtection(Player player) {
        int count = 0;
        ItemStack[] armor = player.getInventory().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece != null &&
                    piece.getType() != Material.AIR &&
                    piece.hasItemMeta() &&
                    piece.getItemMeta().hasEnchant(Enchantment.BLAST_PROTECTION)) {
                count++;
            }
        }
        return count >= 2;
    }

    private void applyKnockback(final Player player, final Location explosionLoc) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        if (lastKnockbackTime.containsKey(playerId)) {
            long lastTime = lastKnockbackTime.get(playerId);
            if (now - lastTime < KNOCKBACK_COOLDOWN) {
                return;
            }
        }

        lastKnockbackTime.put(playerId, now);

        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    Vector knockback = calculateKnockback(player, explosionLoc);
                    player.setVelocity(knockback);
                }
            }
        });

        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                lastKnockbackTime.remove(playerId);
            }
        }, 3L);
    }

    private Vector calculateKnockback(Player player, Location explosionLoc) {
        Vector direction = player.getLocation().toVector()
                .subtract(explosionLoc.toVector());

        if (direction.lengthSquared() < 0.1) {
            direction = new Vector(0, 1, 0);
        }

        direction.normalize();

        double horizontalStrength = 0.45;
        double verticalStrength = 0.35;

        Vector knockback = direction.multiply(horizontalStrength);
        knockback.setY(knockback.getY() + verticalStrength);

        return knockback;
    }
}