/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package cn.mythicland.npclib.api.skin;

public class Skin {
    private final String value;
    private final String signature;

    public Skin(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    public String value() { return value; }
    public String signature() { return signature; }
    // The shaded API retains these getters for already compiled extensions.
    public String getValue() { return value; }
    public String getSignature() { return signature; }

    @Override public boolean equals(Object other) {
        return other instanceof Skin skin && java.util.Objects.equals(value, skin.value)
                && java.util.Objects.equals(signature, skin.signature);
    }
    @Override public int hashCode() { return java.util.Objects.hash(value, signature); }
}
