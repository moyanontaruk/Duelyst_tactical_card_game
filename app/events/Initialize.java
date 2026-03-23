package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;
import utils.UnitDeathUtils;

import java.io.File;
import java.util.Arrays;


public class Initialize implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        // ---- flags used by template/tests ----
        gameState.gameInitialized = true;
        gameState.something = true;

        // ---- reset basic state ----
        gameState.gameOver = false;
        gameState.winner = null;

        gameState.turnNumber = 1;
        gameState.activePlayer = "HUMAN";

        // Story #3: starting health = 20
        gameState.humanHealth = 20;
        gameState.aiHealth = 20;
        gameState.humanMana=2;

        // (optional) if your project uses mana, keep defaults or reset
        // gameState.humanMana = 0;
        // gameState.aiMana = 0;

        // ---- clear runtime state ----
        gameState.boardUnits.clear();
        gameState.uiUnitById.clear();
        gameState.highlightedTargetTiles.clear();

        gameState.unitHealth.clear();
        gameState.unitAttack.clear();
        gameState.unitPositionKey.clear();

        gameState.humanDeck.clear();
        gameState.humanHand.clear();

        gameState.highlightedMovedTiles.clear();

        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;
        gameState.selectUnitId = null;

        gameState.unitMaxHealth.clear();
        gameState.unitOwner.clear();
        gameState.unitName.clear();
        gameState.unitHasMoved.clear();
        gameState.unitHadAttacked.clear();
        gameState.stunnedUntilEndOfOwnersTurn.clear();

        gameState.hornOfForsaken = false;
        gameState.hornRobustness = 0;

        gameState.nextUnitId = 1000;



        // for Story #17/#30
        if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.clear();
        if (gameState.unitOwner != null) gameState.unitOwner.clear();

        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;

        // ----------------------------------------------------
        // 1) Draw board tiles (9x5)
        // ----------------------------------------------------
      for (int x = 0; x < 9; x++) {
        for (int y = 0; y < 5; y++) {
            Tile t = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, t, 0);
            sleep(20);
    }
}

        // ----------------------------------------------------
        // 2) Draw avatars
        // ----------------------------------------------------
        int hx = 1, hy = 2;
        int ax = 7, ay = 2;

        Tile humanTile = BasicObjectBuilders.loadTile(hx, hy);
        Tile aiTile = BasicObjectBuilders.loadTile(ax, ay);

        // --- story card 14 healing/damage -----
        // link avatar Id so health can change
        gameState.humanAvatarId = 100;
        gameState.aiAvatarId = 200;

        Unit humanAvatar = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 100, Unit.class);
        humanAvatar.setPositionByTile(humanTile);
        BasicCommands.drawUnit(out, humanAvatar, humanTile);
        sleep(80);
        BasicCommands.setUnitAttack(out, humanAvatar, 2);
        sleep(80);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, humanAvatar, 20);
        sleep(80);

        Unit aiAvatar = BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, 200, Unit.class);
        aiAvatar.setPositionByTile(aiTile);
        BasicCommands.drawUnit(out, aiAvatar, aiTile);
        sleep(80);
        BasicCommands.setUnitAttack(out, aiAvatar, 2);
        sleep(80);
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, aiAvatar, 20);
        sleep(80);

        // ---- track on board ----
        gameState.boardUnits.put(gameState.key(hx, hy), humanAvatar);
        gameState.boardUnits.put(gameState.key(ax, ay), aiAvatar);
        gameState.uiUnitById.put(100, humanAvatar);
        gameState.uiUnitById.put(200, aiAvatar);

        // ---- track stats/positions server-side ----
        gameState.unitAttack.put(100, 2);
        gameState.unitAttack.put(200, 2);
        gameState.unitPositionKey.put(100, gameState.key(hx, hy));
        gameState.unitPositionKey.put(200, gameState.key(ax, ay));

        //  Story #17 needs maxHealth + owner
        // human avatar
        if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.put(100, 20);
        if (gameState.unitOwner != null) gameState.unitOwner.put(100, "HUMAN");

        // AI avatar
        if (gameState.unitMaxHealth != null) gameState.unitMaxHealth.put(200, 20);
        if (gameState.unitOwner != null) gameState.unitOwner.put(200, "AI");


        // ----------------------------------------------------
        // 3) Story #3: Set player UI health to 20
        // ----------------------------------------------------
        BasicCommands.setPlayer1Health(out, new Player(gameState.humanHealth, gameState.humanMana));
        BasicCommands.setPlayer2Health(out, new Player(gameState.aiHealth, gameState.aiMana));

        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

        // ----------------------------------------------------
        // 4) Story #1: Draw 3 cards for human
        // ----------------------------------------------------
       
        /**
        File dir = new File("conf/gameconfs/cards/");
        String[] p1 = dir.list((d, name) -> name.startsWith("1_") && name.endsWith(".json"));

        if (p1 != null) {
            Arrays.sort(p1);
            for (int i = 0; i < 3 && i < p1.length; i++) {
                String cfg = "conf/gameconfs/cards/" + p1[i];
                int handPos = i + 1;
                int cardId = 1000 + handPos;

                Card c = BasicObjectBuilders.loadCard(cfg, cardId, Card.class);
                if (c != null) {
                    BasicCommands.drawCard(out, c, handPos, 0);
                }
            }
        }
            */
       
        // debug
      
        File dir = new File("conf/gameconfs/cards/");
        String[] p1 = dir.list((d, name) -> name.startsWith("1_") && name.endsWith(".json"));

        if (p1 != null) {
            Arrays.sort(p1);

            // build runtime deck in order
            // build runtime deck in order, 2 copies of each card
        for (String fileName : p1) {
        String cfg = "conf/gameconfs/cards/" + fileName;
        gameState.humanDeck.add(cfg);
        }
        for (String fileName : p1) {
        String cfg = "conf/gameconfs/cards/" + fileName;
        gameState.humanDeck.add(cfg);
}
           gameState.humanDeckIndex = 0;

            // draw starting hand (up to 3 cards)
           int startingDraw = Math.min(3, gameState.humanDeck.size());
           for (int i = 0; i < startingDraw; i++) {
           String cfg = gameState.humanDeck.get(gameState.humanDeckIndex);
           gameState.humanDeckIndex = (gameState.humanDeckIndex + 1) % gameState.humanDeck.size();
                gameState.humanHand.add(cfg);

                int handPos = i + 1;
                int cardId = 1000 + handPos;

                Card c = BasicObjectBuilders.loadCard(cfg, cardId, Card.class);
                if (c != null) {
                    BasicCommands.drawCard(out, c, handPos, 0);
                    sleep(80);
                }
            }
        }

        gameState.aiDeck.clear();
        gameState.aiHand.clear();

        File dir2 = new File("conf/gameconfs/cards/");
        String[] p2 = dir2.list((d, name) -> name.startsWith("2_") && name.endsWith(".json"));

    if (p2 != null) {
    Arrays.sort(p2);

    // build AI runtime deck: 2 copies of each AI card
    for (String fileName : p2) {
    String cfg = "conf/gameconfs/cards/" + fileName;
    gameState.aiDeck.add(cfg);
}
    for (String fileName : p2) {
    String cfg = "conf/gameconfs/cards/" + fileName;
    gameState.aiDeck.add(cfg);
}
    gameState.aiDeckIndex = 0;

    // draw starting AI hand (up to 3)
    int startingDraw = Math.min(3, gameState.aiDeck.size());
    for (int i = 0; i < startingDraw; i++) {
    String cfg = gameState.aiDeck.get(gameState.aiDeckIndex);
    gameState.aiDeckIndex = (gameState.aiDeckIndex + 1) % gameState.aiDeck.size();
        gameState.aiHand.add(cfg);
    }
}

        

        /** 
        //test#26
        String[] starterHand = new String[] {
        "conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json",
        "conf/gameconfs/cards/1_1_c_u_bad_omen.json",
        "conf/gameconfs/cards/1_2_c_s_hornoftheforsaken.json"
        };

        for (int i = 0; i < starterHand.length; i++) {
            String cfg = starterHand[i];
            int handPos = i + 1;
            int cardId = 1000 + handPos;

        Card c = BasicObjectBuilders.loadCard(cfg, cardId, Card.class);
        if (c != null) {
            BasicCommands.drawCard(out, c, handPos, 0);
        }
        }
            */

    }

    // small UI sync delay
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}