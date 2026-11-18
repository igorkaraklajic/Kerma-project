package Kerma.Dto.CoinbaseTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;

public record CoinbaseTransactionOutputs (@JsonProperty(value = "pubkey", required = true) String pubkey,
                                          @JsonProperty(value = "value", required = true) BigInteger value){}
