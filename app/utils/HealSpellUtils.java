package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.EffectAnimation;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * Story #27 helper:
 * heal a target unit by a fixed amount, capped at its starting/max health.
 */
public final class HealSpellUtils {

    private HealSpellUtils() {}

    /**
     * Heal a target unit.
     *
     * @return true if target is valid and spell resolved
     */
    public static boolean healUnit(ActorRef out,
                                   GameState gameState,
                                   Unit target,
                                   int healAmount,
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

        int currentHealth = gameState.unitHealth.getOrDefault(targetId, 0);
        int maxHealth = gameState.unitMaxHealth.getOrDefault(targetId, currentHealth);
        int newHealth = Math.min(currentHealth + healAmount, maxHealth);

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

        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, newHealth);
        return true;
    }
}