package Dto;

import Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Dto.NormalTransaction.NormalTransactionFields;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({ @JsonSubTypes.Type(CoinbaseTransactionFields.class), @JsonSubTypes.Type(NormalTransactionFields.class) })
public interface TransactionDto {}
