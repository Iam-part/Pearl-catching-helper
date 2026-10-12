package com.example.pearlcatch;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Pearl Catch (client-side, Minecraft 26.2, Mojang names).
 *
 * G: throw an ender pearl, wait `delay` ticks, throw a wind charge (in the direction you're looking).
 * [ and ]: change the delay; it is printed in chat. Delay 0 = both in the same tick.
 */
public class PearlCatchClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pearlcatch", "main"));

    private KeyMapping runKey, delayUpKey, delayDownKey;

    private boolean waiting = false;
    private int timer;
    private int windSlot, originalSlot;

    private int delayTicks = 3;

    @Override
    public void onInitializeClient() {
        runKey       = reg("key.pearlcatch.run",        InputConstants.KEY_G);
        delayUpKey   = reg("key.pearlcatch.delay_up",   InputConstants.KEY_RBRACKET);
        delayDownKey = reg("key.pearlcatch.delay_down", InputConstants.KEY_LBRACKET);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private KeyMapping reg(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    private void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            waiting = false;
            return;
        }

        while (delayUpKey.consumeClick()) {
            delayTicks = Math.min(20, delayTicks + 1);
            chat(player, "Delay: " + delayTicks + " tick" + (delayTicks == 1 ? "" : "s"));
        }
        while (delayDownKey.consumeClick()) {
            delayTicks = Math.max(0, delayTicks - 1);
            chat(player, "Delay: " + delayTicks + " tick" + (delayTicks == 1 ? "" : "s"));
        }

        // second half: wind charge after the delay
        if (waiting) {
            if (--timer > 0) return;
            use(mc, player, windSlot);
            finish(player);
            return;
        }

        boolean pressed = false;
        while (runKey.consumeClick()) pressed = true;
        if (!pressed) return;

        Inventory inv = player.getInventory();
        int pearl = find(inv, Items.ENDER_PEARL);
        int wind  = find(inv, Items.WIND_CHARGE);
        if (pearl < 0 || wind < 0) {
            chat(player, "Missing " + (pearl < 0 ? "ender pearl" : "") +
                    (pearl < 0 && wind < 0 ? " and " : "") + (wind < 0 ? "wind charge" : ""));
            return;
        }

        originalSlot = inv.getSelectedSlot();
        int pearlSlot = ensureHotbar(mc, player, pearl, -1);
        windSlot      = ensureHotbar(mc, player, wind, pearlSlot);

        use(mc, player, pearlSlot);

        if (delayTicks == 0) {
            use(mc, player, windSlot);
            inv.setSelectedSlot(originalSlot);
        } else {
            timer = delayTicks;
            waiting = true;
        }
    }

    /** Hotbar (0-8) first, then main inventory (9-35). */
    private int find(Inventory inv, Item item) {
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    /** If the item is in the main inventory, swap it into a hotbar slot. */
    private int ensureHotbar(Minecraft mc, LocalPlayer player, int slot, int avoid) {
        if (slot < 9) return slot;
        int target = -1;
        for (int i = 8; i >= 0; i--) {
            if (i != avoid) { target = i; break; }
        }
        // inventory index 9-35 equals the player menu slot id
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, target,
                ContainerInput.SWAP, player);
        return target;
    }

    private void use(Minecraft mc, LocalPlayer player, int hotbarSlot) {
        player.getInventory().setSelectedSlot(hotbarSlot);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND); // syncs the selected slot first
    }

    private void finish(LocalPlayer player) {
        player.getInventory().setSelectedSlot(originalSlot);
        waiting = false;
    }

    private void chat(LocalPlayer player, String msg) {
        player.sendSystemMessage(Component.literal("[PearlCatch] " + msg));
    }
}
