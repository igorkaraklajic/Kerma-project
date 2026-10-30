package Dto.NormalTransaction;

import Dto.TransactionDto;

public record NormalTransactionFields (NormalTransactionInputs[] inputs, NormalTransactionOutputs[] outputs, String type) implements TransactionDto {}
