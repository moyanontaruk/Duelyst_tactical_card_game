package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;

import java.util.Iterator;
import java.util.Map;

public final class StunRules {

    private StunRules() {}

    /**
     * Apply stun to a non-avatar unit.
     * The unit will be unable to move/attack during its owner's next turn.
     */
    public static boolean applyStunToUnit(ActorRef out, GameState gameState, Unit target) {
        if (gameState == null || target == null) return false;

        int unitId = target.getId();

        // Non-avatar only
        if (unitId == gameState.humanAvatarId || unitId == gameState.aiAvatarId) {
            BasicCommands.addPlayer1Notification(out, "Invalid target (cannot stun an avatar).", 2);
            return false;
        }

        String owner = gameState.unitOwner.get(unitId);
        if (owner == null) return false;

        gameState.stunnedUntilEndOfOwnersTurn.put(unitId, owner);
        return true;
    }

    /**
     * Returns true only during the stunned unit owner's blocked turn.
     */
    public static boolean isStunnedThisTurn(GameState gameState, int unitId) {
        if (gameState == null) return false;

        String blockedOwnerTurn = gameState.stunnedUntilEndOfOwnersTurn.get(unitId);
        if (blockedOwnerTurn == null) return false;

        return blockedOwnerTurn.equals(gameState.activePlayer);
    }

    /**
     * Called when a player's turn ends.
     * Clears any stuns that were active for that player's turn only.
     */
    public static void clearStunsForEndingPlayer(GameState gameState, String endingPlayer) {
        if (gameState == null || endingPlayer == null) return;

        Iterator<Map.Entry<Integer>> it =
                gameState.stunnedUntilEndOfOwnersTurn.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, String> e = it.next();
            if (endingPlayer.equals() {
                it.remove();
            }
        }
    }

    /**
     * Convenience helper for move/attack rejection.
     */
    public static boolean rejectIfStunned(ActorRef out, GameState gameState, int unitId) {
        if (!isStunnedThisTurn(gameState, unitId)) return false;

        BasicCommands.addPlayer1Notification(out);
        return true;
    }
}