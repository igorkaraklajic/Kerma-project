package Dto.CoinbaseTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoinbaseTransactionOutputs (@JsonProperty(value = "pubkey", required = true) String pubkey,
                                          @JsonProperty(value = "value", required = true) long value){}
