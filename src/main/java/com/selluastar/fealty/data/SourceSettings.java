package com.selluastar.fealty.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.Severity;

/**
 * Tuning for a rep source, from {@code fealty/rep_actions/<id>.json} or {@code fealty/crimes/<id>.json}.
 * The file id must match a registered rep source (for example {@code fealty:trade}).
 *
 * <pre>{@code
 * // rep_actions/trade.json: +1 for every 5 trades, at most +3 a day
 * { "amount": 1, "every": 5, "daily_cap": 3 }
 * // crimes/steal.json
 * { "amount": -15, "severity": "moderate", "heat": 3 }
 * }</pre>
 */
public record SourceSettings(Optional<Integer> amount, int every, int dailyCap, Optional<Severity> severity,
                             Optional<Boolean> requiresWitness, boolean alertsGuards, Optional<Boolean> canRaiseNegative,
                             int heat) {

    public static final Codec<SourceSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("amount").forGetter(SourceSettings::amount),
            Codec.intRange(1, 10000).optionalFieldOf("every", 1).forGetter(SourceSettings::every),
            Codec.intRange(0, 100000).optionalFieldOf("daily_cap", 0).forGetter(SourceSettings::dailyCap),
            Severity.CODEC.optionalFieldOf("severity").forGetter(SourceSettings::severity),
            Codec.BOOL.optionalFieldOf("requires_witness").forGetter(SourceSettings::requiresWitness),
            Codec.BOOL.optionalFieldOf("alerts_guards", true).forGetter(SourceSettings::alertsGuards),
            Codec.BOOL.optionalFieldOf("can_raise_negative").forGetter(SourceSettings::canRaiseNegative),
            Codec.intRange(0, 10000).optionalFieldOf("heat", 0).forGetter(SourceSettings::heat)
    ).apply(i, SourceSettings::new));

    public static final SourceSettings DEFAULT = new SourceSettings(Optional.empty(), 1, 0, Optional.empty(),
            Optional.empty(), true, Optional.empty(), 0);
}
