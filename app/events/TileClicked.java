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
import utils.DestroySpellUtils;
import utils.StunRules;

//  new utils 
import utils.SummonUtils;
import utils.OpeningGambitResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import utils.SpellTargetRules;
import utils.DirectDamageSpellUtils;

import utils.HealSpellUtils;


public class TileClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        if (gameState.gameOver) return;
        if (!"HUMAN".equals(gameState.activePlayer)) return;

        int tilex = message.get("tilex").asInt();
        int tiley = message.get("tiley").asInt();


        //SC 6 addition start -MAggie
        // only triggerrs when no card is selected yet
        // or has not moved or attacked yet
        // 2 tiles cardinally or 1 diagonally,
        // tiles with units wont be highlighted
        boolean noCardSelected = (
                gameState.selectedHandPos == null || gameState.selectedCardConfig == null);
        if (noCardSelected) {
            clearMoveHighlights(out, gameState);

            // checks if the tile clicked has a unit
            String clickedKey = gameState.key(tilex, tiley);
            Unit clickedUnit = gameState.boardUnits.get(clickedKey);

            // if the tile is empty, nothing willbe highlighted
            if (clickedUnit == null) return;

            String owner = gameState.unitOwner.get(clickedUnit.getId());
            if (!"HUMAN".equals(owner)) return;

            int unitId = clickedUnit.getId();

            // constraints -- unit cannot have already attacked
            boolean alreadyAttacked = gameState.unitHadAttacked.getOrDefault(unitId, false);
            if (alreadyAttacked) {
                BasicCommands.addPlayer1Notification(
                        out,
                        // message showing to player and there will be mo highlighted tile
                        "This unit already attacked.", 2);
                return;
            }

            // cant have already moved
            boolean alreadyMoved = gameState.unitHasMoved.getOrDefault(unitId, false);
            if (alreadyMoved) {
                BasicCommands.addPlayer1Notification(out, "This unit already moved.", 2);
                return;
            }

            //storing which unit is selected
            gameState.selectUnitId = unitId;

            if (StunRules.rejectIfStunned(out, gameState, unitId)) {
            return;
            }

            List<int[]> reachable = getValidMoveTiles(gameState, tilex, tiley);

            highlightMoveTilesWhite(out, gameState, reachable);

            return;
        }
        //SC 6 done


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

            //  Add to SC18: Record unit name upon summoning
            gameState.unitName.put(unitId, card.getCardname());

            // --- story card 23: rush ability ---
            boolean hasRush = (unit instanceof BetterUnit) && ((BetterUnit) unit).getHasRush();
            // units without rush cannot move/attack on summoning turn
            if (!hasRush){
                gameState.unitHasMoved.put(unitId, true);
                gameState.unitHadAttacked.put(unitId, true);
            }

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

        if (!isValidSpellTarget(gameState, card, tilex, tiley)) {
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


        //#26
        // ============================================================
        // Spell: Truestrike (deal 2 damage to an enemy non-avatar unit)
        // ============================================================
        if (name.equals("truestrike")) {

        Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
        if (target == null) return;

        // spend mana
        gameState.humanMana -= cost;
        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

        boolean applied = DirectDamageSpellUtils.dealDamageToUnit(
            out,
            gameState,
            target,
            2,
            "AI",
            false,
            StaticConfFiles.f1_martyrdom
        );

        if (!applied) return;

        consumeSelectedCardAndClear(out, gameState, selectedPos);
        return;
        }


        // ============================================================
        // Spell: Sundrop Elixir (heal target unit by 5, capped at max health)
        // ============================================================
        if (name.equals("sundrop elixir")) {

            Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
            if (target == null) return;

            // spend mana
            gameState.humanMana -= cost;
            BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

            boolean applied = HealSpellUtils.healUnit(
                    out,
                    gameState,
                    target,
                    5,
                    null,   // no owner restriction: any unit tile is allowed
                    true,   // avatar can be healed too
                    StaticConfFiles.f1_buff
            );

            if (!applied) return;

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // ============================================================
        // Spell: Beam Shock (stun enemy non-avatar unit)
        // ============================================================
        if (name.equals("beamshock")) {

        Unit target = gameState.boardUnits.get(gameState.key(tilex, tiley));
        if (target == null) return;

        int targetId = target.getId();

        // must be enemy non-avatar
        if (targetId == gameState.humanAvatarId || targetId == gameState.aiAvatarId) return;

        String owner = gameState.unitOwner.get(targetId);
        if (!"AI".equals(owner)) return;

        // spend mana
        gameState.humanMana -= cost;
        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

        // apply stun
        if (!StunRules.applyStunToUnit(out, gameState, target)) return;

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
            if (!DestroySpellUtils.destroyNonAvatarUnit(out, gameState, target)) return;

            // summon wraithling on same tile
            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        //Spell: Horn of the Forsaken -- ARTIFACT 3 (give friendly unit +2/+2)
        // ------ story card 19: damage ability trigger -------
        if (name.equals("horn of the forsaken")) {
            // target must be human avatar tile (1,2)
            if (tilex !=1 || tiley!=2) return;

            //spend mana
            gameState.humanMana -= cost;
            BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

            // play effect on avatar tile
            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (fx != null) BasicCommands.playEffectAnimation(out, fx, tile);

            //equip artifact -- story card 19----
            gameState.hornOfForsaken = true;
            gameState.hornRobustness = 3;

            BasicCommands.addPlayer1Notification(out, "Horn of the Forsaken equipped for the next 3 turns!", 3);

            consumeSelectedCardAndClear(out, gameState, selectedPos);
            return;
        }

        // Unknown spell - do nothing but clear highlights (optional)
        BasicCommands.addPlayer1Notification(out, "Spell not implemented", 2);

    }


    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------


    //#21  only valid target tile can apply spell
    private boolean isValidSpellTarget(GameState gameState, Card card, int tilex, int tiley) {
    List<int[]> validTargets = SpellTargetRules.getValidTargetTiles(gameState, card);
    for (int[] xy : validTargets) {
        if (xy != null && xy.length >= 2 && xy[0] == tilex && xy[1] == tiley) {
            return true;
        }
    }
    return false;
    }



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
    
    /** 
    //test #26
    private List<String> getCurrentHumanHandConfigs() {
    List<String> res = new ArrayList<>();

    // temporary test hand
    res.add("conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json");

    File dir = new File("conf/gameconfs/cards/");
    String[] p1 = dir.list((d, name) -> name.startsWith("1_") && name.endsWith(".json"));
    if (p1 == null) return res;

    Arrays.sort(p1);

    for (int i = 0; i < p1.length && res.size() < 6; i++) {
        res.add("conf/gameconfs/cards/" + p1[i]);
    }
    return res;
    }
    */


    // helper for SC6 -Maggie
    private void clearMoveHighlights(ActorRef out, GameState gameState) {
        if (gameState.highlightedMovedTiles.isEmpty()) return;
        for (String key : gameState.highlightedMovedTiles) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);

            Tile tile = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, tile, 0); //won't highlight if =0
        }

        gameState.highlightedMovedTiles.clear();
    }

    // highlights tiles that are moveable in white
    // then will store in team state "highlightedMovedtiles"
    private void highlightMoveTilesWhite(
            ActorRef out,
            GameState gameState,
            List<int[]> tiles) {

        for (int[] xy : tiles) {
            int x = xy[0];
            int y = xy[1];
            Tile boardTile = BasicObjectBuilders.loadTile(x, y);

            //highligh white =1
            BasicCommands.drawTile(out, boardTile, 1);

            gameState.highlightedMovedTiles.add(gameState.key(x, y));
        }
    }

    private List<int[]> getValidMoveTiles(
            GameState gameState,
            int startX,
            int startY) {

        List<int[]> validTiles = new ArrayList<>();

        int[][] cardinalsDir = new int[][]{
                {1, 0},  // left of screen
                {-1, 0}, // right of screen
                {0, 1}, // down
                {0, -1} // up
        };

        for (int[] direction : cardinalsDir) {
            int dirX = direction[0];
            int dirY = direction[1];

            int step1X = startX + dirX;
            int step1Y = startY + dirY;

            if (!isOnBoard(step1X, step1Y))
                continue;

            String step1Key = gameState.key(step1X, step1Y);

            // cannot mvoe if step1 has unit and cant go around/thru
            if (gameState.boardUnits.containsKey(step1Key))
                continue;

            validTiles.add(new int[]{step1X, step1Y});

            // can only move bc first tile was empty
            int step2X = startX + 2 * dirX;
            int step2Y = startY + 2 * dirY;

            if (!isOnBoard(step2X, step2Y))
                continue;

            String step2Key = gameState.key(step2X, step2Y);

            // needs to be empty for the 2 square move
            if (gameState.boardUnits.containsKey(step2Key))
                continue;

            validTiles.add(new int[]{step2X, step2Y});
        }

        // diagional can only do 1 sqaure move
        int[][] diagonalDir = new int[][]{
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };

        for (int[] direction : diagonalDir) {
            int diagX = startX + direction[0];
            int diagY = startY + direction[1];

            if (!isOnBoard(diagX, diagY))
                continue;

            String key = gameState.key(diagX, diagY);

            //diagonal square needs to be empty
            if (gameState.boardUnits.containsKey(key))
                continue;

            validTiles.add(new int[]{diagX, diagY});
        }
        return validTiles;
    }
}