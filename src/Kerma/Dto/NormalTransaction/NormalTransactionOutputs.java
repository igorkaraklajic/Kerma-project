package Kerma.Dto.NormalTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalTransactionOutputs (@JsonProperty(value = "pubkey", required = true) String pubkey,
                                        @JsonProperty(value = "value", required = true) long value){}
