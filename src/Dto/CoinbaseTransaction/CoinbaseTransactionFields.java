package Dto.CoinbaseTransaction;

import Dto.TransactionDto;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CoinbaseTransactionFields (@JsonProperty(value = "height", required = true) int height,
                                         @JsonProperty(value = "outputs", required = true) CoinbaseTransactionOutputs[] outputs,
                                         @JsonProperty(value = "type", required = true) String type) implements TransactionDto {}
