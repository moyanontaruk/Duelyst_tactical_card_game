import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.OpeningGambitResolver;

public class SilverguardSquireOpeningGambitTest {
    private GameState gameState;
    private ActorRef out;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
        out = null;

        gameState.humanAvatarId = 100;
        gameState.aiAvatarId = 200;

        gameState.unitPositionKey.put(gameState.humanAvatarId, gameState.key(1, 2));
        gameState.unitPositionKey.put(gameState.aiAvatarId, gameState.key(7, 2));
    }

    @Test
    public void humanSquireBuffsAlliesInFrontAndBehindHumanAvatarOnly() {
        Unit front = addUnit("conf/gameconfs/units/wraithling.json", 2, 2, 1, 1, "HUMAN");
        Unit back = addUnit("conf/gameconfs/units/wraithling.json", 0, 2, 1, 1, "HUMAN");
        Unit adjacentNotFrontBack = addUnit("conf/gameconfs/units/wraithling.json", 1, 3, 1, 1, "HUMAN");
        Unit squireFrontRelativeUnit = addUnit("conf/gameconfs/units/wraithling.json", 6, 2, 1, 1, "HUMAN");

        Unit squire = addUnit("conf/gameconfs/units/silverguard_squire.json", 4, 4, 1, 1, "HUMAN");
        OpeningGambitResolver.onSummoned(out, gameState, squire, "Silverguard Squire");

        assertEquals(2, gameState.unitAttack.get(front.getId()).intValue());
        assertEquals(2, gameState.unitHealth.get(front.getId()).intValue());
        assertEquals(2, gameState.unitMaxHealth.get(front.getId()).intValue());

        assertEquals(2, gameState.unitAttack.get(back.getId()).intValue());
        assertEquals(2, gameState.unitHealth.get(back.getId()).intValue());
        assertEquals(2, gameState.unitMaxHealth.get(back.getId()).intValue());

        assertEquals(1, gameState.unitAttack.get(adjacentNotFrontBack.getId()).intValue());
        assertEquals(1, gameState.unitHealth.get(adjacentNotFrontBack.getId()).intValue());
        assertEquals(1, gameState.unitMaxHealth.get(adjacentNotFrontBack.getId()).intValue());

        assertEquals(1, gameState.unitAttack.get(squireFrontRelativeUnit.getId()).intValue());
        assertEquals(1, gameState.unitHealth.get(squireFrontRelativeUnit.getId()).intValue());
        assertEquals(1, gameState.unitMaxHealth.get(squireFrontRelativeUnit.getId()).intValue());
    }

    @Test
    public void aiSquireBuffsAlliesInFrontAndBehindAiAvatarOnly() {
        Unit front = addUnit("conf/gameconfs/units/wraithling.json", 6, 2, 1, 1, "AI");
        Unit back = addUnit("conf/gameconfs/units/wraithling.json", 8, 2, 1, 1, "AI");
        Unit adjacentNotFrontBack = addUnit("conf/gameconfs/units/wraithling.json", 7, 3, 1, 1, "AI");
        Unit enemyOnFront = addUnit("conf/gameconfs/units/wraithling.json", 5, 2, 1, 1, "HUMAN");

        Unit squire = addUnit("conf/gameconfs/units/silverguard_squire.json", 4, 0, 1, 1, "AI");
        OpeningGambitResolver.onSummoned(out, gameState, squire, "Silverguard Squire");

        assertEquals(2, gameState.unitAttack.get(front.getId()).intValue());
        assertEquals(2, gameState.unitHealth.get(front.getId()).intValue());
        assertEquals(2, gameState.unitMaxHealth.get(front.getId()).intValue());

        assertEquals(2, gameState.unitAttack.get(back.getId()).intValue());
        assertEquals(2, gameState.unitHealth.get(back.getId()).intValue());
        assertEquals(2, gameState.unitMaxHealth.get(back.getId()).intValue());

        assertEquals(1, gameState.unitAttack.get(adjacentNotFrontBack.getId()).intValue());
        assertEquals(1, gameState.unitHealth.get(adjacentNotFrontBack.getId()).intValue());
        assertEquals(1, gameState.unitMaxHealth.get(adjacentNotFrontBack.getId()).intValue());

        assertEquals(1, gameState.unitAttack.get(enemyOnFront.getId()).intValue());
        assertEquals(1, gameState.unitHealth.get(enemyOnFront.getId()).intValue());
        assertEquals(1, gameState.unitMaxHealth.get(enemyOnFront.getId()).intValue());
    }

    private Unit addUnit(String unitConfig, int x, int y, int atk, int hp, String owner) {
        int id = gameState.allocateUnitId();
        Tile tile = BasicObjectBuilders.loadTile(x, y);
        Unit unit = BasicObjectBuilders.loadUnit(unitConfig, id, BetterUnit.class);
        assertNotNull(unit);

        unit.setPositionByTile(tile);
        unit.setAttack(atk);
        unit.setHealth(hp);

        gameState.boardUnits.put(gameState.key(x, y), unit);
        gameState.uiUnitById.put(id, unit);
        gameState.unitAttack.put(id, atk);
        gameState.unitHealth.put(id, hp);
        gameState.unitMaxHealth.put(id, hp);
        gameState.unitOwner.put(id, owner);
        gameState.unitPositionKey.put(id, gameState.key(x, y));

        return unit;
    }
}

