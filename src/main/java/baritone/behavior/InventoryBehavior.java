/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.Helper;
import baritone.utils.ToolSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Predicate;

public final class InventoryBehavior extends Behavior implements Helper {

    int ticksSinceLastInventoryMove;
    int[] lastTickRequestedMove; // not everything asks every tick, so remember the request while coming to a halt
    private ItemStack pendingSource;
    private ItemStack pendingDestination;
    private ItemStack[] pendingInventory;
    private LocalPlayer pendingPlayer;
    private int pendingSelectedSlot;
    private int pendingMenuState;
    private int safeContextTicks;
    private long inventoryTick;
    private long pendingLastRequestedTick;


    public InventoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        // POST must not double the cooldown or run a second inventory operation.
        if (event.getState() != EventState.PRE) {
            return;
        }
        if (event.getType() == TickEvent.Type.OUT || !canAutomateInventory()) {
            clearPendingMove();
            ticksSinceLastInventoryMove = 0;
            safeContextTicks = 0;
            return;
        }
        inventoryTick++;
        safeContextTicks = Math.min(safeContextTicks + 1, 2);
        ticksSinceLastInventoryMove = Math.min(ticksSinceLastInventoryMove + 1, Integer.MAX_VALUE - 1);
        if (lastTickRequestedMove != null && (inventoryTick - pendingLastRequestedTick > 2 || !pendingMoveStillMatches())) {
            clearPendingMove();
        }
        // Deliberately NO background restocking and NO execution of queued requests.
        // Only a live builder/placement/mining caller may renew and execute a demand.
        // Closing inventory, cancelling a task or waiting out a cooldown is not consent
        // to pull a manually moved item back into a reserved hotbar slot.
    }

    /** Restock a tool only when execution is actually about to mine this block. */
    public int toolSlotFor(Block block, ToolSet tools, boolean preferSilkTouch) {
        int hotbar = tools.getBestSlot(block, preferSilkTouch);
        if (!canAutomateInventory()) {
            return hotbar;
        }
        int source = tools.getBestInventorySlot(block, preferSilkTouch);
        if (source < 9) {
            return hotbar;
        }
        OptionalInt empty = getTempHotbarSlot(i -> !ctx.player().getInventory().getItem(i).isEmpty());
        int target = empty.orElse(hotbar);
        return requestSwapWithHotBar(source, target) ? target : -1;
    }

    public boolean attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        return destination.isPresent() && requestSwapWithHotBar(inMainInvy, destination.getAsInt());
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        // No permanently reserved slots: prefer the first empty eligible hotbar slot.
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (ctx.player().getInventory().getNonEquipmentItems().get(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }
        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }
        // Stable choice: never reroll a queued request onto another occupied slot.
        return OptionalInt.of(candidates.get(0));
    }

    private boolean requestSwapWithHotBar(int inInventory, int inHotbar) {
        // Builder/throwaway callers bypass onTick's maintenance, so guard here too.
        if (!canAutomateInventory()) {
            clearPendingMove();
            ticksSinceLastInventoryMove = 0;
            safeContextTicks = 0;
            return false;
        }
        if (safeContextTicks < 2 || !InventoryMovePolicy.validSwap(inInventory, inHotbar)) {
            return false;
        }
        NonNullList<ItemStack> inventory = ctx.player().getInventory().getNonEquipmentItems();
        if (inventory.get(inInventory).isEmpty()) {
            return false;
        }
        if (lastTickRequestedMove != null && (inventoryTick - pendingLastRequestedTick > 2 || !pendingMoveStillMatches())) {
            clearPendingMove();
            return false; // Do not silently rebase an obsolete request onto new contents.
        }
        if (lastTickRequestedMove != null
                && (lastTickRequestedMove[0] != inInventory || lastTickRequestedMove[1] != inHotbar)) {
            clearPendingMove();
            return false;
        }
        if (lastTickRequestedMove == null) {
            lastTickRequestedMove = new int[]{inInventory, inHotbar};
            pendingSource = inventory.get(inInventory).copy();
            pendingDestination = inventory.get(inHotbar).copy();
            pendingPlayer = ctx.player();
            pendingSelectedSlot = pendingPlayer.getInventory().getSelectedSlot();
            pendingMenuState = pendingPlayer.inventoryMenu.getStateId();
            pendingInventory = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                pendingInventory[i] = inventory.get(i).copy();
            }
        }
        pendingLastRequestedTick = inventoryTick;
        if (ticksSinceLastInventoryMove < Baritone.settings().ticksBetweenInventoryMoves.value) {
            logDebug("Inventory move requested but delaying " + ticksSinceLastInventoryMove + " " + Baritone.settings().ticksBetweenInventoryMoves.value);
            return false;
        }
        if (Baritone.settings().inventoryMoveOnlyIfStationary.value && !baritone.getInventoryPauserProcess().stationaryForInventoryMove()) {
            logDebug("Inventory move requested but delaying until stationary");
            return false;
        }
        // Revalidate immediately before the sole SWAP packet is sent.
        if (!canAutomateInventory() || !pendingMoveStillMatches()) {
            clearPendingMove();
            return false;
        }
        ItemStack expectedSource = pendingSource.copy();
        ItemStack expectedDestination = pendingDestination.copy();
        ctx.playerController().windowClick(ctx.player().inventoryMenu.containerId, InventoryMovePolicy.menuSlot(inInventory), inHotbar, ContainerInput.SWAP, ctx.player());
        ticksSinceLastInventoryMove = 0;
        clearPendingMove();
        return ItemStack.matches(expectedSource, inventory.get(inHotbar))
                && ItemStack.matches(expectedDestination, inventory.get(inInventory))
                && ctx.player().inventoryMenu.getCarried().isEmpty();
    }

    private boolean canAutomateInventory() {
        LocalPlayer player = ctx.player();
        return InventoryMovePolicy.canAutomate(Baritone.settings().allowInventory.value,
                player != null && ctx.world() != null, ctx.minecraft().gui.screen() != null,
                player != null && player.containerMenu == player.inventoryMenu,
                player != null && player.inventoryMenu.getCarried().isEmpty());
    }

    private boolean pendingMoveStillMatches() {
        if (lastTickRequestedMove == null || pendingInventory == null || pendingPlayer != ctx.player()
                || pendingSelectedSlot != ctx.player().getInventory().getSelectedSlot()
                || pendingMenuState != ctx.player().inventoryMenu.getStateId()
                || !InventoryMovePolicy.validSwap(lastTickRequestedMove[0], lastTickRequestedMove[1])) {
            return false;
        }
        NonNullList<ItemStack> inventory = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = 0; i < 36; i++) {
            if (!ItemStack.matches(pendingInventory[i], inventory.get(i))) {
                return false;
            }
        }
        return pendingSource != null && pendingDestination != null;
    }

    private void clearPendingMove() {
        lastTickRequestedMove = null;
        pendingSource = null;
        pendingDestination = null;
        pendingInventory = null;
        pendingPlayer = null;
    }

    public boolean hasGenericThrowaway() {
        for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
            if (throwaway(false, stack -> item.equals(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && maybe.equals(((BlockItem) stack.getItem()).getBlock().getStateForPlacement(new BlockPlaceContext(new UseOnContext(ctx.world(), ctx.player(), InteractionHand.MAIN_HAND, stack, new BlockHitResult(new Vec3(ctx.player().position().x, ctx.player().position().y, ctx.player().position().z), Direction.UP, ctx.playerFeet(), false)) {}))))) {
            return true; // gotem
        }
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
        }
        for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
            if (throwaway(select, stack -> item.equals(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        return throwaway(select, desired, Baritone.settings().allowInventory.value);
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired, boolean allowInventory) {
        LocalPlayer p = ctx.player();
        if (p == null || (select && (ctx.minecraft().gui.screen() != null
                || p.containerMenu != p.inventoryMenu || !p.inventoryMenu.getCarried().isEmpty()))) {
            return false;
        }
        NonNullList<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.get(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    p.getInventory().setSelectedSlot(i);
                }
                return true;
            }
        }
        if (desired.test(p.getItemBySlot(EquipmentSlot.OFFHAND))) {
            // main hand takes precedence over off hand
            // that means that if we have block A selected in main hand and block B in off hand, right clicking places block B
            // we've already checked above ^ and the main hand can't possible have an acceptablethrowawayitem
            // so we need to select in the main hand something that doesn't right click
            // so not a shovel, not a hoe, not a block, etc
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.get(i);
                if (item.isEmpty() || item.getItem().components().has(DataComponents.TOOL)) {
                    if (select) {
                        p.getInventory().setSelectedSlot(i);
                    }
                    return true;
                }
            }
        }

        if (allowInventory) {
            for (int i = 9; i < 36; i++) {
                if (desired.test(inv.get(i))) {
                    if (select) {
                        OptionalInt target = getTempHotbarSlot(slot -> false);
                        if (target.isEmpty() || !requestSwapWithHotBar(i, target.getAsInt())) {
                            return false;
                        }
                        p.getInventory().setSelectedSlot(target.getAsInt());
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
