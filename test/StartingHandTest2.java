import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import events.Initialize;
import play.libs.Json;
import structures.GameState;

public class StartingHandTest2 {
    private GameState gameState;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
    }

    @Test
    public void startingHandContainsSquireAndAssassin() {
        ObjectNode eventMessage = Json.newObject();
        new Initialize().processEvent(null, gameState, eventMessage);

        assertTrue(gameState.humanHand.size() >= 2);
        assertEquals("conf/gameconfs/cards/2_7_c_u_silverguard_squire.json", gameState.humanHand.get(0));
        assertEquals("conf/gameconfs/cards/1_6_c_u_nightsorrow_assassin.json", gameState.humanHand.get(1));
    }
}

