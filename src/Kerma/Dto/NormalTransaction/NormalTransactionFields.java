package Kerma.Dto.NormalTransaction;

import Kerma.Dto.TransactionDto;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalTransactionFields (@JsonProperty(value = "inputs", required = true) NormalTransactionInputs[] inputs,
                                       @JsonProperty(value = "outputs", required = true) NormalTransactionOutputs[] outputs,
                                       @JsonProperty(value = "type", required = true) String type) implements TransactionDto {}
