package Dto.NormalTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalTransactionInputsOutpoint (@JsonProperty(value = "index", required = true) int index,
                                               @JsonProperty(value = "txid", required = true) String txid){}
