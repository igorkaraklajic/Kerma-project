package Dto.CoinbaseTransaction;

import Dto.TransactionDto;

public record CoinbaseTransactionFields (int height, CoinbaseTransactionOutputs[] outputs, String type) implements TransactionDto {}
