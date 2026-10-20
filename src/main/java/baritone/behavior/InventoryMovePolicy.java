package baritone.behavior;

/** Side-effect-free checks for automated player-inventory swaps. */
final class InventoryMovePolicy {
    private InventoryMovePolicy() {}

    static boolean canAutomate(boolean enabled, boolean inWorld, boolean screenOpen,
                               boolean playerInventoryMenu, boolean cursorEmpty) {
        return enabled && inWorld && !screenOpen && playerInventoryMenu && cursorEmpty;
    }

    static boolean validSwap(int inventorySlot, int hotbarSlot) {
        return inventorySlot >= 0 && inventorySlot < 36
                && hotbarSlot >= 0 && hotbarSlot < 9 && inventorySlot != hotbarSlot;
    }

    static int menuSlot(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= 36) {
            throw new IllegalArgumentException("Not a player inventory slot: " + inventorySlot);
        }
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
