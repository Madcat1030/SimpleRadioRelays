package dev.paul.simpleradiorepeaters.network;

import net.minecraft.core.BlockPos;
import java.util.UUID;

/** Physical controller identity, independent of the Relay's persistent RF identity. */
public record CardBankRef(BlockPos position, UUID id) {
    public CardBankRef { position = position.immutable(); }
}
