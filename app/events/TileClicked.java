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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            String clickedKey = gameState.key(tilex, tiley);

            //Story 11 (Unit Action: Move)
            if (gameState.selectUnitId != null) {

                Unit selectedUnit = gameState.uiUnitById.get(gameState.selectUnitId);

                //safety
                if (selectedUnit == null) {
                    gameState.selectUnitId = null;
                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                }
                //move clicked
                if (gameState.highlightedMovedTiles.contains(clickedKey)) {
                    Tile target = BasicObjectBuilders.loadTile(tilex, tiley);

                    //sending to UI
                    BasicCommands.moveUnitToTile(out, selectedUnit, target, true);

                    //updating position backend
                    selectedUnit.setPositionByTile(target);

                    //remove unit from previous tile
                    gameState.boardUnits.values().removeIf(v -> v.equals(selectedUnit));

                    //add unit to new tile
                    gameState.boardUnits.put(gameState.key(tilex, tiley), selectedUnit);

                    //tracking where the units are
                    gameState.unitPositionKey.put(
                            selectedUnit.getId(),
                            gameState.key(tilex, tiley)
                    );

                    //noting that the unit alrady moved this turn
                    gameState.unitHasMoved.put(selectedUnit.getId(), true);

                    //this will clear the selection so the player can click unit again
                    //can still attack later during the same turn
                    gameState.selectUnitId = null;

                    //remove white/red highlights from board
                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                } else if (gameState.highlightedTargetTiles.contains(clickedKey)) {

                    //get enemy unit on that clicked tile
                    Unit enemy = gameState.boardUnits.get(clickedKey);

                    //safety to not attack null
                    if (enemy != null) {
                        selectedUnit.attack(gameState, out, enemy);
                    }

                    //clear the selected unit and highlights after the attack
                    gameState.selectUnitId = null;
                    clearMoveHighlights(out, gameState);
                    HighlightUtils.clearHighlightedTiles(out, gameState);
                    return;
                }
                //if click was not on a highlighted move or attack tile,
                // clear current selection/highlights and continue
                gameState.selectUnitId = null;
                clearMoveHighlights(out, gameState);
                HighlightUtils.clearHighlightedTiles(out, gameState);
            }

            // checks if the tile clicked has a unit
            else {
                clearMoveHighlights(out, gameState);
                HighlightUtils.clearHighlightedTiles(out, gameState);
                Unit clickedUnit = gameState.boardUnits.get(clickedKey);

                // if the tile is empty, nothing willbe highlighted
                if (clickedUnit == null) return;

                //only human units can be selected during human turn
                String owner = gameState.unitOwner.get(clickedUnit.getId());
                if (!"HUMAN".equals(owner)) return;

                //save the unit id
                int unitId = clickedUnit.getId();

                // if the unit already attacked this turn, it cannot do anything else
                boolean alreadyAttacked = gameState.unitHadAttacked.getOrDefault(unitId, false);
                if (alreadyAttacked) {
                    BasicCommands.addPlayer1Notification(out, "This unit already attacked.", 2);
                    return;
                }
                //if unit is stunned, stop
                if (StunRules.rejectIfStunned(out, gameState, unitId)) {
                    return;
                }

                //storing which unit is selected
                gameState.selectUnitId = unitId;

                //checking weather unit has alrady moved this turn
                boolean alreadyMoved = gameState.unitHasMoved.getOrDefault(unitId, false);

                //show white highlights if unit has not moved
                //show white highlights if unit has not moved
                if (!alreadyMoved) {
                List<int[]> reachable = getValidMoveTiles(gameState, tilex, tiley);
                highlightMoveTilesWhite(out, gameState, reachable);

                // show in red any enemy that could be attacked after moving to
                // one of the highlighted destination tiles
                List<int[]> attackableAfterMove = getAttackableAfterMoveTiles(gameState, reachable);
                HighlightUtils.highlightTilesRed(out, gameState, attackableAfterMove);
            }

// always show enemy tiles in red if they are already adjacent now
List<int[]> attackable = HighlightUtils.getEnemyTiles(tilex, tiley, true, gameState);
HighlightUtils.highlightTilesRed(out, gameState, attackable);
return;
            }
        }
        //SC 6 done


        // Must have selected a card
        if (gameState.selectedHandPos == null) return;
        if (gameState.selectedCardConfig == null) return;

        int selectedPos = gameState.selectedHandPos;

        // validate selected position against current runtime hand
        if (selectedPos < 1 || selectedPos > gameState.humanHand.size()) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }

        // the selected card must still match the actual current card in hand
        String currentCfg = gameState.humanHand.get(selectedPos - 1);
        if (currentCfg == null || !currentCfg.equals(gameState.selectedCardConfig)) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }

        // Load selected card from current runtime hand
        Card card = BasicObjectBuilders.loadCard(currentCfg, 1000 + selectedPos, Card.class);
        if (card == null) {
            HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
        }


        // ------------------------------------------------------------
        // Branch A: Unit card summon
        // ------------------------------------------------------------
        if (gameState.selectedCardIsUnit) {

            if (!card.isCreature()) return;

            // Tile must be empty
            if (gameState.boardUnits.containsKey(gameState.key(tilex, tiley))) return;

            // can summon only on currently highlighted valid summon tiles
            if (!gameState.highlightedMovedTiles.contains(gameState.key(tilex, tiley))) return;


            // Mana check
            int cost = card.getManacost();
            if (gameState.humanMana < cost) {
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return;
            }

            // Prepare summon data first, before spending mana
            if (card.getBigCard() == null || card.getUnitConfig() == null) {
                BasicCommands.addPlayer1Notification(out, "Unit data is invalid", 2);
                HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
            }

            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            int unitId = gameState.allocateUnitId();
            Unit unit = BasicObjectBuilders.loadUnit(card.getUnitConfig(), unitId, BetterUnit.class);

            if (unit == null) {
                BasicCommands.addPlayer1Notification(out, "Failed to summon unit", 2);
                HighlightUtils.clearSelectionAndHighlights(out, gameState);
            return;
            }

            int atk = card.getBigCard().getAttack();
            int hp = card.getBigCard().getHealth();

            // play summon animation
            EffectAnimation summonFx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (summonFx != null) BasicCommands.playEffectAnimation(out, summonFx, tile);

            if (!spendHumanMana(out, gameState, cost)) return;



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

        // generic safety check: clicked tile must be one of the currently highlighted valid spell targets
        if (!gameState.highlightedTargetTiles.contains(gameState.key(tilex, tiley))) {
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
            if (!gameState.highlightedTargetTiles.contains(gameState.key(tilex, tiley))) return;


            // spend mana
            if (!spendHumanMana(out, gameState, cost)) return;


            // play spell effect (optional)
            Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
            EffectAnimation fx = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
            if (fx != null) BasicCommands.playEffectAnimation(out, fx, tile);

            // summon 3 wraithlings:
            // 1) first at clicked tile
            SummonUtils.spawnWraithling(out, gameState, tilex, tiley, "HUMAN");

            // 2) fill remaining around current human avatar position, skipping occupied tiles
            int summoned = 1;
            int[] avatarPos = gameState.getAvatarPosition("HUMAN");
            int ax = avatarPos[0], ay = avatarPos[1];


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
        if (!spendHumanMana(out, gameState, cost)) return;


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
            if (!spendHumanMana(out, gameState, cost)) return;


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
        if (!spendHumanMana(out, gameState, cost)) return;


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
            // must be enemy creature (not AI avatar)
            if (target.getId() == gameState.aiAvatarId) return;


            String owner = gameState.unitOwner.get(target.getId());
            if (!"AI".equals(owner)) return;

            // spend mana
            if (!spendHumanMana(out, gameState, cost)) return;


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
            int[] avatarPos = gameState.getAvatarPosition("HUMAN");
            if (tilex != avatarPos[0] || tiley != avatarPos[1]) return;


            //spend mana
            if (!spendHumanMana(out, gameState, cost)) return;


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

    private boolean spendHumanMana(ActorRef out, GameState gameState, int cost) {
        if (gameState.humanMana < cost) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return false;
        }

        gameState.humanMana -= cost;
        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));
        return true;
    }


    /**
     * Remove the selected card from hand UI and clear selection/highlights.
     * (keeps your existing "redraw from conf folder" approach)
     */
    private void consumeSelectedCardAndClear(ActorRef out, GameState gameState, int selectedPos) {

        // 1) remove selected card from real runtime hand
        if (selectedPos >= 1 && selectedPos <= gameState.humanHand.size()) {
            gameState.humanHand.remove(selectedPos - 1);
        }

        // 2) clear all hand slots first
        for (int pos = 1; pos <= 6; pos++) {
            BasicCommands.deleteCard(out, pos);
        }

        // 3) redraw current runtime hand from left to right
        for (int i = 0; i < gameState.humanHand.size() && i < 6; i++) {
            String cfg = gameState.humanHand.get(i);
            int handPos = i + 1;
            Card c = BasicObjectBuilders.loadCard(cfg, 1000 + handPos, Card.class);
            if (c != null) {
                BasicCommands.drawCard(out, c, handPos, 0);
            }
        }

        // 4) clear summon/move highlights and spell target highlights
        clearMoveHighlights(out, gameState);
        HighlightUtils.clearHighlightedTiles(out, gameState);

        // 5) clear selection state
        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;
    }


    /**
     * NOTE: Your current hand logic is "read from conf folder" (static), not real runtime deck.
     * I keep it as-is to avoid breaking your current tests/template.
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

    private List<int[]> getAttackableAfterMoveTiles(GameState gameState, List<int[]> reachableTiles) {
    List<int[]> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (int[] xy : reachableTiles) {
        if (xy == null || xy.length < 2) continue;

        List<int[]> adjacentEnemies = HighlightUtils.getEnemyTiles(xy[0], xy[1], true, gameState);
        for (int[] enemyTile : adjacentEnemies) {
            String key = gameState.key(enemyTile[0], enemyTile[1]);
            if (seen.add(key)) {
                result.add(enemyTile);
            }
        }
    }

    return result;
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