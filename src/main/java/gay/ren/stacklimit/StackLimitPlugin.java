package gay.ren.stacklimit;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class StackLimitPlugin extends JavaPlugin implements Listener {

    private final Map<Material, Integer> itemMaxMap = new HashMap<>();
    private final List<String> clipMessages = new ArrayList<>();
    private final Set<UUID> notifiedOps = new HashSet<>();
    private static final int MAX_ALLOWED = 99;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        processConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("StackLimit enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("StackLimit disabled.");
    }

    private void processConfig() {
        itemMaxMap.clear();
        clipMessages.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("items");
        if (section == null) {
            getLogger().warning("No items section found in config.yml");
            return;
        }
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            int configured = getConfig().getInt("items." + key, 0);
            int clipped = configured;
            if (configured > MAX_ALLOWED) {
                clipped = MAX_ALLOWED;
                changed = true;
                String msg = "[StackLimit] Config value for '" + key + "' was " + configured + " and has been clipped to " + MAX_ALLOWED;
                clipMessages.add(msg);
                getLogger().warning(msg);
            }
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                getLogger().warning("Unknown material in config: " + key);
                continue;
            }
            itemMaxMap.put(mat, clipped);
            if (clipped != configured) getConfig().set("items." + key, clipped);
        }
        if (changed) saveConfig();
        notifiedOps.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("stacklimit")) return false;
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            processConfig();
            sender.sendMessage("[StackLimit] config reloaded.");
            return true;
        }
        sender.sendMessage("Usage: /stacklimit reload");
        return true;
    }

    private void applyMaxToStack(ItemStack stack) {
        Integer max = itemMaxMap.get(stack.getType());
        if (max != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
            meta.setMaxStackSize(max);
            if (stack.getAmount() > max) stack.setAmount(max);
            stack.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        applyMaxToStack(stack);
        entity.setItemStack(stack);
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Item entity = event.getItemDrop();
        ItemStack stack = entity.getItemStack();
        applyMaxToStack(stack);
        entity.setItemStack(stack);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        applyMaxToStack(result);
        event.getInventory().setResult(result);
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        applyMaxToStack(stack);
        entity.setItemStack(stack);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.isOp()) return;
        UUID id = p.getUniqueId();
        if (notifiedOps.contains(id)) return;
        if (!clipMessages.isEmpty()) {
            for (String m : clipMessages) p.sendMessage(m);
        }
        notifiedOps.add(id);
    }
}