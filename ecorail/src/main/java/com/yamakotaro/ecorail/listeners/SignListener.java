package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.EconomyHolder;
import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ManagedCart;
import com.yamakotaro.ecorail.items.TicketItemFactory;
import com.yamakotaro.ecorail.signs.TicketSign;
import com.yamakotaro.ecorail.signs.TicketSignManager;
import com.yamakotaro.ecorail.station.Station;
import com.yamakotaro.ecorail.station.StationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;

/**
 * A sign whose first line reads "[ecorail]" (any case) becomes a ticket-selling sign: line 2/3
 * name the from/to stations, line 4 the fare. Right-clicking it boards the player on a fresh
 * minecart, paid for either with a matching physical ticket item or, failing that, Vault money.
 */
public class SignListener implements Listener {

    private static final String SIGN_TAG = "[ecorail]";

    private final JavaPlugin plugin;
    private final StationManager stationManager;
    private final TicketSignManager ticketSignManager;
    private final TicketItemFactory ticketItemFactory;
    private final EconomyHolder economyHolder;
    private final CartManager cartManager;
    private final Messages messages;

    public SignListener(JavaPlugin plugin, StationManager stationManager, TicketSignManager ticketSignManager,
                         TicketItemFactory ticketItemFactory, EconomyHolder economyHolder, CartManager cartManager, Messages messages) {
        this.plugin = plugin;
        this.stationManager = stationManager;
        this.ticketSignManager = ticketSignManager;
        this.ticketItemFactory = ticketItemFactory;
        this.economyHolder = economyHolder;
        this.cartManager = cartManager;
        this.messages = messages;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String tag = plainLine(event, 0);
        if (!tag.equalsIgnoreCase(SIGN_TAG)) {
            return;
        }
        Optional<Station> from = stationManager.find(plainLine(event, 1));
        Optional<Station> to = stationManager.find(plainLine(event, 2));
        if (from.isEmpty() || to.isEmpty()) {
            event.getPlayer().sendMessage(messages.get("sign.invalid-stations", Map.of()));
            event.setCancelled(true);
            return;
        }
        double price;
        try {
            price = Double.parseDouble(plainLine(event, 3).trim());
            if (price < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            event.getPlayer().sendMessage(messages.get("sign.invalid-price", Map.of()));
            event.setCancelled(true);
            return;
        }

        event.line(0, Component.text("[EcoRail]", NamedTextColor.GREEN));
        event.line(1, Component.text(from.get().name(), NamedTextColor.DARK_GREEN));
        event.line(2, Component.text(to.get().name(), NamedTextColor.DARK_GREEN));
        event.line(3, Component.text(formatPrice(price), NamedTextColor.GOLD));

        Block block = event.getBlock();
        ticketSignManager.register(new TicketSign(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                from.get().id(), to.get().id(), price));
        event.getPlayer().sendMessage(messages.get("sign.created", Map.of(
                "from", from.get().name(), "to", to.get().name(), "price", formatPrice(price))));
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ticketSignManager.unregister(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Bukkit fires this once per hand on a right-click and also for left-clicks - without
        // both checks a single click could double-charge the fare (main hand + off hand).
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }
        Optional<TicketSign> maybeSign = ticketSignManager.find(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (maybeSign.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        TicketSign sign = maybeSign.get();
        Optional<Station> from = stationManager.find(sign.fromStationId());
        Optional<Station> to = stationManager.find(sign.toStationId());
        if (from.isEmpty() || to.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        boolean paidWithTicket = ticketItemFactory.matches(handItem, sign.fromStationId(), sign.toStationId());
        if (paidWithTicket) {
            handItem.setAmount(handItem.getAmount() - 1);
        } else if (!chargeFare(player, sign.price())) {
            return;
        }

        boardMinecart(player, from.get(), to.get());
        player.sendMessage(messages.get(paidWithTicket ? "board.boarded-ticket" : "board.boarded-paid", Map.of(
                "to", to.get().name(), "price", formatPrice(sign.price()))));
    }

    /** @return true if the fare was charged (or is free); false if the player couldn't pay and was already messaged. */
    private boolean chargeFare(Player player, double price) {
        Economy economy = economyHolder.get();
        if (economy == null) {
            player.sendMessage(messages.get("board.no-economy", Map.of()));
            return false;
        }
        if (!economy.has(player, price)) {
            player.sendMessage(messages.get("board.insufficient-funds", Map.of("price", formatPrice(price))));
            return false;
        }
        economy.withdrawPlayer(player, price);
        return true;
    }

    private void boardMinecart(Player player, Station from, Station to) {
        World world = Bukkit.getWorld(from.world());
        Location spawnLocation = new Location(world, from.centerX(), from.y(), from.centerZ());
        Minecart minecart = world.spawn(spawnLocation, Minecart.class);
        minecart.addPassenger(player);
        double launchSpeed = plugin.getConfig().getDouble("physics.launch-speed", 0.4);
        minecart.setVelocity(new Vector(from.dirX() * launchSpeed, 0, from.dirZ() * launchSpeed));
        cartManager.register(new ManagedCart(minecart.getUniqueId(), from.world(),
                spawnLocation.getBlockX() >> 4, spawnLocation.getBlockZ() >> 4, to.id(),
                player.getUniqueId(), from.dirX(), from.dirZ()));
    }

    private static String plainLine(SignChangeEvent event, int index) {
        return PlainTextComponentSerializer.plainText().serialize(event.line(index));
    }

    private static String formatPrice(double price) {
        return price == Math.floor(price) ? String.valueOf((long) price) : String.valueOf(price);
    }
}
