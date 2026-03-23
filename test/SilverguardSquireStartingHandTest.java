import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import events.Initialize;
import structures.GameState;

public class SilverguardSquireStartingHandTest {
    private GameState gameState;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
    }

    @Test
    public void silverguardSquireIsInStartingHand() {
        new Initialize().processEvent(null, gameState, null);
        assertFalse(gameState.humanHand.isEmpty());
        assertEquals("conf/gameconfs/cards/2_7_c_u_silverguard_squire.json", gameState.humanHand.get(0));
    }
}

