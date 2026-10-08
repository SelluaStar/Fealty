package com.selluastar.fealty.guard;

/** What a blast of the Lord's Horn tells the village's guards. */
public enum HornOrder {
    /** Guards nearby fall in behind the lord, and more are sent from the village to join them. */
    CALL("call", "horn"),
    /** The lord's guards stay where they stand. */
    HOLD("hold", "shield"),
    /** The lord's guards keep watch over the area around the lord. */
    GUARD("guard", "guard"),
    /** The lord's guards go back to the village. */
    RETURN("return", "house");

    private final String id;
    private final String icon;

    HornOrder(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    /** The UI icon for this order. */
    public String icon() {
        return icon;
    }

    public static HornOrder byIndex(int index) {
        HornOrder[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static HornOrder byId(String id) {
        for (HornOrder order : values()) {
            if (order.id.equals(id)) {
                return order;
            }
        }
        return CALL;
    }
}
