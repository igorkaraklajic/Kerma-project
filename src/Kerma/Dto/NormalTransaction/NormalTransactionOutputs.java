package Kerma.Dto.NormalTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

public record NormalTransactionOutputs (@JsonProperty(value = "pubkey", required = true) String pubkey,
                                        @JsonProperty(value = "value", required = true) BigInteger value){}
