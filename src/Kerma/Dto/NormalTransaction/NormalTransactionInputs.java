package Kerma.Dto.NormalTransaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalTransactionInputs (@JsonProperty(value = "outpoint", required = true) NormalTransactionInputsOutpoint outpoint,
                                       @JsonProperty(value = "sig", required = true) String sig){}
