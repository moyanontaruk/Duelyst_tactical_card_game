import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import structures.GameState;
import structures.basic.Unit;
import utils.DestroySpellUtils;

public class DestroySpellStory28Test {

    private GameState gs;

    @Before
    public void setup() {
        gs = new GameState();
        gs.humanAvatarId = 100;
        gs.aiAvatarId = 200;
    }

    @Test
    public void destroyNonAvatarUnit_shouldRemoveUnitImmediately() {

        int x = 4, y = 4;
        int unitId = 123;

        Unit enemy = new Unit();
        enemy.setId(unitId);

        // place on board
        gs.boardUnits.put(gs.key(x, y), enemy);
        gs.uiUnitById.put(unitId, enemy);
        gs.unitPositionKey.put(unitId, gs.key(x, y));
        gs.unitOwner.put(unitId, "AI");
        gs.unitHealth.put(unitId, 5);
        gs.unitAttack.put(unitId, 2);

        assertNotNull(gs.boardUnits.get(gs.key(x, y)));

        boolean ok = DestroySpellUtils.destroyNonAvatarUnit(null, gs, enemy);
        assertTrue(ok);

        // removed from board immediately
        assertNull(gs.boardUnits.get(gs.key(x, y)));
        assertFalse(gs.uiUnitById.containsKey(unitId));
        assertFalse(gs.unitHealth.containsKey(unitId));
    }

    @Test
    public void destroyNonAvatarUnit_shouldRejectAvatarTarget() {

        int x = 7, y = 2;
        Unit aiAvatar = new Unit();
        aiAvatar.setId(200);

        gs.boardUnits.put(gs.key(x, y), aiAvatar);
        gs.uiUnitById.put(200, aiAvatar);
        gs.unitPositionKey.put(200, gs.key(x, y));
        gs.unitOwner.put(200, "AI");
        gs.unitHealth.put(200, 20);

        boolean ok = DestroySpellUtils.destroyNonAvatarUnit(null, gs, aiAvatar);
        assertFalse(ok);

        // still present
        assertNotNull(gs.boardUnits.get(gs.key(x, y)));
        assertTrue(gs.uiUnitById.containsKey(200));
    }
}