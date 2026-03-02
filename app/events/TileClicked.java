package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;
import utils.HighlightUtils;
import utils.UnitDeathUtils;

// ✅ new utils (you must create these two files)
import utils.SummonUtils;
import utils.OpeningGambitResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TileClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        if (gameState.gameOver) return;
        if (!"HUMAN".equals(gameState.activePlayer)) return;

        int tilex = message.get("tilex").asInt();
        int tiley = message.get("tiley").asInt();

        // Must have selected a card
        if (gameState.selectedHandPos == null) return;
        if (gameState.selectedCardConfig == null) return;

        int selectedPos = gameState.selectedHandPos;

        // Load selected card
        Card card = BasicObjectBuilders.loadCard(gameState.selectedCardConfig, 1000 + selectedPos, Card.class);
        if (card == null) return;

        // ------------------------------------------------------------
        // Branch A: Unit card summon
        // ------------------------------------------------------------
        if (gameState.selectedCardIsUnit) {

            if (!card.isCreature()) return;

            // Tile must be empty
            if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return;

            // can summon only within 1 tile of human avatar at (1,2)
            if (!isWithinOneTile(tilex, tiley, 1, 2)) return;

            // Mana check
            int cost = card.getManacost();
            if (gameState.humanMana < cost) {
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
                return;
            }

            // 1) decrement mana + update UI
            gameState.humanMana -= cost;
            BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

            // 2) play summon animation
            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation summonFx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (summonFx != null) BasicCommands.playEffectAnimation(out, summonFx, tile);

            // 3) create unit id + stats
            int unitId = gameState.allocateUnitId();
            int atk = card.getBigCard().getAttack();
            int hp = card.getBigCard().getHealth();

            // 4) draw unit in frontend + set stats
            Unit unit = BasicObjectBuilders.loadUnit(card.getUnitConfig(), unitId, BetterUnit.class);
            if (unit == null) return;

            unit.setPositionByTile(tile);
            BasicCommands.drawUnit(out, unit, tile);
            BasicCommands.setUnitAttack(out, unit, atk);
            UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, unit, hp);

            // Track on board (UI)
            gameState.boardUnits.put(gameState.key(tilex, tiley), unit);
            gameState.uiUnitById.put(unitId, unit);

            // Track stats/positions server-side
            gameState.unitAttack.put(unitId, atk);
            gameState.unitPositionKey.put(unitId, gameState.key(tilex, tiley));

            // Story #17 needs these
            gameState.unitMaxHealth.put(unitId, hp);
            gameState.unitOwner.put(unitId, "HUMAN");

            // Story #17: opening gambit triggers right after summon
            OpeningGambitResolver.onSummoned(out, gameState, unit, card.getCardname());

            // consume card + clear selection/highlights
            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // ------------------------------------------------------------
        // Branch B: Spell card logic (Story #30)
        // ------------------------------------------------------------
        // For safety: spell cards are not creatures
        if (card.isCreature()) return;

        String name = (card.getCardname() == null) ? "" : card.getCardname().trim().toLowerCase();

        // Mana check
        int cost = card.getManacost();
        if (gameState.humanMana < cost) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return;
        }

        // ============================================================
        // Spell: Wraithling Swarm (summon 3 wraithlings around avatar)
        // ============================================================
        if (name.equals("wraithling swarm")) {

            // clicked tile must be empty AND within 1 tile of human avatar (same as highlight rules)
            if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return;
            if (!isWithinOneTile(tilex, tiley, 1, 2)) return;

            // spend mana
            gameState.humanMana -= cost;
            BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

            // play spell effect (optional)
            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (fx != null) BasicCommands.playEffectAnimation(out, fx, tile);

            // summon 3 wraithlings:
            // 1) first at clicked tile
            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            // 2) fill remaining around avatar (1,2), skipping occupied
            int summoned = 1;
            int ax = 1, ay = 2;

            for (int dx = -1; dx <= 1 && summoned < 3; dx++) {
                for (int dy = -1; dy <= 1 && summoned < 3; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int sx = ax + dx;
                    int sy = ay + dy;
                    if (!isOnBoard(sx, sy)) continue;

                    if (gameState.boardUnits.containsKey(gameState.key(sx, sy))) continue;
                    // skip the clicked tile (already summoned)
                    if (sx == tilex && sy == tiley) continue;

                    SummonUtils.spawnWraithling(out, gameState, sx, sy, "HUMAN");
                    summoned++;
                }
            }

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // ============================================================
        // Spell: Dark Terminus (destroy enemy creature; summon wraithling)
        // ============================================================
        if (name.equals("dark terminus")) {

            Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
            if (target == null) return;

            // must be enemy creature (not AI avatar id=200)
            if (target.getId() == 200) return;

            String owner = gameState.unitOwner.get(target.getId());
            if (!"AI".equals(owner)) return;

            // spend mana
            gameState.humanMana -= cost;
            BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

            // kill target
            UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, 0);

            // summon wraithling on same tile
            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Unknown spell - do nothing but clear highlights (optional)
        BasicCommands.addPlayer1Notification(out, "Spell not implemented", 2);
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    private boolean isWithinOneTile(int x, int y, int ax, int ay) {
        int dx = Math.abs(x - ax);
        int dy = Math.abs(y - ay);
        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    /**
     * Remove the selected card from hand UI and clear selection/highlights.
     * (keeps your existing "redraw from conf folder" approach)
     */
    private void consumeSelectedCardAndClear(ActorRef out, GameState gameState, int selectedPos) {

        // 1) remove selected card from hand UI
        BasicCommands.deleteCard(out, selectedPos);

        List<String> hand = getCurrentHumanHandConfigs();
        if (hand.size() >= selectedPos) {
            hand.remove(selectedPos - 1);
        }

        // redraw up to 6
        for (int i = 0; i < hand.size() && i < 6; i++) {
            String cfg = hand.get(i);
            int handPos = i + 1;
            Card c = BasicObjectBuilders.loadCard(cfg, 1000 + handPos, Card.class);
            if (c != null) BasicCommands.drawCard(out, c, handPos, 0);
        }
        // clear any leftover slots if hand shrank
        for (int pos = hand.size() + 1; pos <= 6; pos++) {
            BasicCommands.deleteCard(out, pos);
        }

        // 2) clear selection/highlights
        HighlightUtils.clearHighlightedTiles(out, gameState);
        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;
    }

    /**
     * NOTE: Your current hand logic is "read from conf folder" (static), not real runtime deck.
     * I keep it as-is to avoid breaking your current tests/template.
     */
    private List<String> getCurrentHumanHandConfigs() {
        File dir = new File("conf/gameconfs/cards/");
        String[] p1 = dir.list((d, name) -> name.startsWith("1_") && name.endsWith(".json"));
        if (p1 == null) return new ArrayList<>();

        Arrays.sort(p1);

        List<String> res = new ArrayList<>();
        for (int i = 0; i < p1.length && i < 6; i++) {
            res.add("conf/gameconfs/cards/" + p1[i]);
        }
        return res;
    }
}