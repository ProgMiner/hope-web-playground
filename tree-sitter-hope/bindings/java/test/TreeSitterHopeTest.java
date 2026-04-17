import io.github.treesitter.jtreesitter.Language;
import io.github.treesitter.jtreesitter.hope.TreeSitterHope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TreeSitterHopeTest {
    @Test
    public void testCanLoadLanguage() {
        assertDoesNotThrow(() -> new Language(TreeSitterHope.language()));
    }
}
