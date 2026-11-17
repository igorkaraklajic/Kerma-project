package Kerma.Dto;

import Kerma.Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionFields;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({ @JsonSubTypes.Type(CoinbaseTransactionFields.class), @JsonSubTypes.Type(NormalTransactionFields.class) })
public interface TransactionDto {}
