import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import commands.BasicCommands;
import commands.DummyTell;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;

public class UnitAttackAnimationEffectTest {
    private static class CaptureTell implements DummyTell {
        private final List<ObjectNode> messages = new ArrayList<>();

        @Override
        public void tell(ObjectNode message) {
            messages.add(message);
        }
    }

    private GameState gameState;
    private CaptureTell captureTell;

    @Before
    public void setup() {
        captureTell = new CaptureTell();
        BasicCommands.altTell = captureTell;

        gameState = new GameState();
    }

    @Test
    public void unitAttackEmitsMeleeSwingProjectileEffect() {
        Unit attacker = addUnit("conf/gameconfs/units/wraithling.json", 1001, 2, 2, 2, 2, "HUMAN");
        Unit defender = addUnit("conf/gameconfs/units/wraithling.json", 1002, 3, 2, 1, 2, "AI");

        attacker.attack(gameState, null, defender);

        ObjectNode swing = captureTell.messages.stream()
                .filter(m -> m.has("messagetype") && "drawProjectile".equals(m.get("messagetype").asText()))
                .filter(m -> m.has("isMeleeSwing") && m.get("isMeleeSwing").asBoolean())
                .findFirst()
                .orElse(null);

        assertNotNull(swing);
        assertTrue(swing.has("durationMs"));
        assertTrue(swing.get("durationMs").asInt() > 0);
        assertEquals(60, swing.get("arcHeight").asInt());

        assertEquals(2, swing.get("tile").get("tilex").asInt());
        assertEquals(2, swing.get("tile").get("tiley").asInt());
        assertEquals(3, swing.get("targetTile").get("tilex").asInt());
        assertEquals(2, swing.get("targetTile").get("tiley").asInt());
    }

    private Unit addUnit(String unitConfig, int id, int x, int y, int atk, int hp, String owner) {
        Tile tile = BasicObjectBuilders.loadTile(x, y);
        Unit unit = BasicObjectBuilders.loadUnit(unitConfig, id, BetterUnit.class);
        assertNotNull(unit);

        unit.setPositionByTile(tile);
        unit.setAttack(atk);
        unit.setHealth(hp);

        gameState.boardUnits.put(gameState.key(x, y), unit);
        gameState.uiUnitById.put(id, unit);
        gameState.unitOwner.put(id, owner);
        gameState.unitPositionKey.put(id, gameState.key(x, y));

        gameState.unitAttack.put(id, atk);
        gameState.unitHealth.put(id, hp);
        gameState.unitMaxHealth.put(id, hp);

        gameState.unitHasMoved.put(id, false);
        gameState.unitHadAttacked.put(id, false);

        return unit;
    }
}

