package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;

/**
 * Story Card #28: Destroy Unit (non-avatar only).
 * When a destroy effect triggers, the unit immediately dies.
 */
public final class DestroySpellUtils {

    private DestroySpellUtils() {}

    /**
     * Destroys a unit immediately if it is a valid non-avatar target.
     *
     * @return true if destroyed, false if invalid target (e.g., avatar or null).
     */
    public static boolean destroyNonAvatarUnit(ActorRef out, GameState gameState, Unit target) {
        if (gameState == null || target == null) return false;

        int id = target.getId();

        // Targeting restriction: non-avatar only
        if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) {
            BasicCommands.addPlayer1Notification(out, "Invalid target (cannot destroy an avatar).", 2);
            return false;
        }

        // Immediately kill: set health to 0 -> Story #13 handles death animation + removal
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, 0);
        return true;
    }
}