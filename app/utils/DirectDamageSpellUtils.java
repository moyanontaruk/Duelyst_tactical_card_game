package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.EffectAnimation;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;

/**
 * #21 + #26
 * Helpers for spell cards that deal direct damage to a single unit.
 */
public final class DirectDamageSpellUtils {

    private DirectDamageSpellUtils() {}

    /**
     * Applies direct damage to a target unit.
     *
     * @return true if damage was applied, false if the target is invalid.
     */
    public static boolean dealDamageToUnit(ActorRef out,
                                           GameState gameState,
                                           Unit target,
                                           int damage,
                                           String requiredOwner,
                                           boolean allowAvatarTargets,
                                           String effectConfig) {

        if (gameState == null || target == null) return false;

        int targetId = target.getId();

        if (!allowAvatarTargets &&
                (targetId == gameState.humanAvatarId || targetId == gameState.aiAvatarId)) {
            BasicCommands.addPlayer1Notification(out, "Invalid target.", 2);
            return false;
        }

        if (requiredOwner != null) {
            String owner = gameState.unitOwner.get(targetId);
            if (!requiredOwner.equals(owner)) {
                BasicCommands.addPlayer1Notification(out, "Invalid target.", 2);
                return false;
            }
        }

        Tile targetTile = BasicObjectBuilders.loadTile(
                target.getPosition().getTilex(),
                target.getPosition().getTiley()
        );

        if (effectConfig != null) {
            EffectAnimation fx = BasicObjectBuilders.loadEffect(effectConfig);
            if (fx != null) {
                BasicCommands.playEffectAnimation(out, fx, targetTile);
            }
        }

        BasicCommands.playUnitAnimation(out, target, UnitAnimationType.hit);

        int currentHealth = gameState.unitHealth.getOrDefault(targetId, 0);
        int newHealth = currentHealth - damage;
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, newHealth);
        return true;
    }
}