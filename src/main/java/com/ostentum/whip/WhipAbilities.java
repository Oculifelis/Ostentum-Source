package com.ostentum.whip;

import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.google.gson.JsonObject;
import com.ostentum.Ostentum;

/**
 * The numbers behind each whip's own ability (pull strength, effect lengths, bolt counts, chain hops), read from
 * {@code data/<namespace>/ostentum/whip_abilities/<name>.json} and keyed by {@code "whip"}. The jar ships every
 * whip's values there; a pack file merges over them field by field. Each call passes the shipped value as its
 * fallback, so a field a pack removes keeps working. Length, damage, cooldown and the rest of a whip's tuning stay
 * in Fundamentum's {@code fundamentum/whips} table.
 */
public final class WhipAbilities {
    public static final DataTable<JsonObject> TABLE = DataTables.registerJson(Ostentum.id("whip_abilities"),
        "ostentum/whip_abilities").keyField("whip").synced();

    /** Loads the class, so the table is registered before data loads. */
    public static void init() { }

    public static double get(String whip, String field, double fallback) {
        return TABLE.number(Ostentum.id(whip), field, fallback);
    }

    public static float get(String whip, String field, float fallback) {
        return (float) TABLE.number(Ostentum.id(whip), field, fallback);
    }

    public static int get(String whip, String field, int fallback) {
        return (int) Math.round(TABLE.number(Ostentum.id(whip), field, fallback));
    }

    private WhipAbilities() { }
}
