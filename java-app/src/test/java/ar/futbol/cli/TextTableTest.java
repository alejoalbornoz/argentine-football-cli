package ar.futbol.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextTableTest {

    @Test
    void padsEachColumnToItsWidestCell() {
        String text = new TextTable().right("#").left("Team").right("Pts")
                .row(1, "River Plate", 80)
                .row(10, "Tigre", 5)
                .render();

        assertEquals("""
                 #  Team         Pts
                --  -----------  ---
                 1  River Plate   80
                10  Tigre          5
                """, text);
    }

    @Test
    void rowWithTheWrongNumberOfCellsIsRejected() {
        TextTable table = new TextTable().left("Team").right("Pts");

        assertThrows(IllegalArgumentException.class, () -> table.row("Tigre"));
    }
}
