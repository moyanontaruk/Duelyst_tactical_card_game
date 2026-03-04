import structures.GameState;
import structures.basic.Unit;
import utils.UnitDeathUtils;

public class UnitDeathRulesTest {
    public static void main(String[]args){
        GameState stateOfGame = new GameState();
        int deadId = 123;
        int x = 4;
        int y = 4;

        //fake/test UI unit.
        Unit uiUnit = new Unit();
        uiUnit.setId(deadId);

        //adding unit to board
        stateOfGame.boardUnits.put(stateOfGame.key(x, y), uiUnit);

        stateOfGame.uiUnitById.put(deadId, uiUnit);

        stateOfGame.unitHealth.put(deadId, 3);
        stateOfGame.unitAttack.put(deadId, 2);
        stateOfGame.unitPositionKey.put(deadId, stateOfGame.key(x, y));

        boolean unitIsThere = stateOfGame.boardUnits.get(stateOfGame.key(x,y)) !=null;
        System.out.println("Unit is on board, before damage:" + unitIsThere);

        UnitDeathUtils.setUnitHealthAndCheckDeath(
                null,
                stateOfGame,
                uiUnit,
                0 //setting health to 0
        );

        boolean stillOnBoard = (stateOfGame.boardUnits.get(stateOfGame.key(x,y)) !=null);

        boolean stillInMap = stateOfGame.uiUnitById.containsKey(deadId);

        System.out.println("Unit still at (x,y) after damage??" + stillOnBoard);
        System.out.println("was the backend removed from board?" + (!stillOnBoard));
        System.out.println("was UI mapping removed?" + (!stillInMap));

        if (!stillOnBoard && !stillInMap) {
            System.out.println("UnitDeathRules PASSSSEDDD!!");
        }
            else{
                System.out.println("we cooked. UnitDeathRules failed :((");
        }
    }
}