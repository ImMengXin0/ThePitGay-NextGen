package net.mizukilab.pit.util.sign;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignGuiTextTest {
    @Test public void amountInputSupportsBothMinecraftComponentEncodings() {
        assertEquals("125", SignGui.plainText("\"125\""));
        assertEquals("125", SignGui.plainText("{\"text\":\"125\"}"));
        assertEquals("125", SignGui.plainText("{\"text\":\"12\",\"extra\":[{\"text\":\"5\"}]}"));
        assertEquals("", SignGui.plainText("{\"text\":\"\"}"));
    }
}
