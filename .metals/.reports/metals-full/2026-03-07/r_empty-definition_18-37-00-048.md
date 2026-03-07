error id: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/SummonUtils.java:_empty_/GameState#unitHasAttacked#
file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/SummonUtils.java
empty definition using pc, found symbol in pc: _empty_/GameState#unitHasAttacked#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2294
uri: file://<HOME>/Documents/GitHub/IT+%20Group%20Project/MSc-IT-plus-2026-LB01-T/app/utils/SummonUtils.java
text:
```scala
package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * Common summon helpers used by:
 * - Unit card summon (TileClicked)
 * - Spell summon (#30)
 * - Opening gambit summon (#17)
 */
public final class SummonUtils {

    private SummonUtils() {}

    /**
     * Spawn a unit using a unit config json.
     * Returns the spawned unit, or null if failed (e.g., tile occupied).
     */
    public static Unit spawnUnit(ActorRef out, GameState gameState,
                                 String unitConfig,
                                 int tilex, int tiley,
                                 int atk, int hp,
                                 String owner) {

        // occupancy check
        if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return null;

        int unitId = gameState.allocateUnitId();
        Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);

        Unit unit = BasicObjectBuilders.loadUnit(unitConfig, unitId, BetterUnit.class);
        if (unit == null) return null;

        unit.setPositionByTile(tile);

        // UI: draw + stats
        BasicCommands.drawUnit(out, unit, tile);
        BasicCommands.setUnitAttack(out, unit, atk);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, unit, hp);

        // server maps
        gameState.boardUnits.put(gameState.key(tilex, tiley), unit);
        gameState.uiUnitById.put(unitId, unit);

        gameState.unitAttack.put(unitId, atk);
        gameState.unitPositionKey.put(unitId, gameState.key(tilex, tiley));

        // for story #17
        gameState.unitMaxHealth.put(unitId, hp);
        gameState.unitOwner.put(unitId, owner);

        // --- story card 19 damage trigger ability ---
        // register Zeal units (Silverguard Knight)
        if (unitConfig != null && unitConfig.contains("silverguard_knight")){
            gameState.zealUnitIds.add(unitId);
        }
        
        // --- story card 23: rush ability ---
        boolean hasRush = (unit instanceof BetterUnit) && ((BetterUnit) unit).getHasRush();
        if (!hasRush){
            gameState.unitHasMoved.put(unitId, true);
            gameState.@@unitHasAttacked.put(unitId, true);
        }

        return unit;
    }

    /**
     * Convenience: spawn a 1/1 Wraithling.
     */
    public static Unit spawnWraithling(ActorRef out, GameState gameState,
                                       int tilex, int tiley,
                                       String owner) {
        return spawnUnit(out, gameState,
                "conf/gameconfs/units/wraithling.json",
                tilex, tiley,
                1, 1,
                owner);
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/GameState#unitHasAttacked#