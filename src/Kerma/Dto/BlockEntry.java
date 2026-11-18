package Kerma.Dto;

import java.util.Set;

public record BlockEntry(int height, BlockDto blockDto, Set<UTXO> utxoSet) {
}
