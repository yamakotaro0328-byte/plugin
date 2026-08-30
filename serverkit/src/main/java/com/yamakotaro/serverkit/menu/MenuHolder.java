package com.yamakotaro.serverkit.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuHolder implements InventoryHolder {

    public enum Mode {
        MAIN,
        FREEZE,
        CLAIMS
    }

    private final Mode mode;
    private Inventory inventory;
    private final Map<Integer, UUID> freezeTargets = new HashMap<>();
    private final Map<Integer, String> claimNames = new HashMap<>();

    public MenuHolder(Mode mode) {
        this.mode = mode;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Mode getMode() {
        return mode;
    }

    public void putFreezeTarget(int slot, UUID uuid) {
        freezeTargets.put(slot, uuid);
    }

    public UUID getFreezeTarget(int slot) {
        return freezeTargets.get(slot);
    }

    public void putClaimName(int slot, String name) {
        claimNames.put(slot, name);
    }

    public String getClaimName(int slot) {
        return claimNames.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
