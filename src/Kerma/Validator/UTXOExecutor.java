package Kerma.Validator;

import Kerma.Dto.BlockDto;
import Kerma.Dto.BlockEntry;
import Kerma.Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionInputs;
import Kerma.Dto.NormalTransaction.NormalTransactionOutputs;
import Kerma.Dto.TransactionDto;
import Kerma.Dto.UTXO;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final  class  UTXOExecutor {
    public record UtxoExecutionResult(int statusCode, Set<UTXO> newUTXOSet) {}

    public static UtxoExecutionResult executeBlock(BlockDto blockDto, BlockEntry prevBlockEntry, Map<String, TransactionDto> knownObjectsMap) {

        String[] txids = blockDto.txids();

        String cbTxid = null;
        for (int i = 0; i < txids.length; i++) {
            TransactionDto tdto = knownObjectsMap.get(txids[i]);
            if(tdto instanceof CoinbaseTransactionFields) {
                if(i != 0) {
                    return new UtxoExecutionResult(-6, null);
                }
                cbTxid = txids[0];
            }
        }

        Set<UTXO> workingUtxoSet = new HashSet<>(prevBlockEntry.utxoSet());

        BigInteger fee = BigInteger.ZERO;
        int startIndex = (cbTxid != null) ? 1 : 0;

        for (int i = startIndex; i < blockDto.txids().length; i++) {
            String txid = blockDto.txids()[i];
            TransactionDto tdto = knownObjectsMap.get(txid);

            if(!(tdto instanceof NormalTransactionFields normalTx)) {
                return new UtxoExecutionResult(-2, null);
            }

            BigInteger inputSum = BigInteger.ZERO;

            for (NormalTransactionInputs input : normalTx.inputs()) {
                String inputTxid = input.outpoint().txid();
                int index = input.outpoint().index();

                if (inputTxid.equals(cbTxid)) {
                    return new UtxoExecutionResult(-6, null);
                }
                if (!workingUtxoSet.remove(new UTXO(inputTxid, index))) {
                    return new UtxoExecutionResult(-7, null);
                }

                TransactionDto inputTdto = knownObjectsMap.get(inputTxid);

                if (inputTdto instanceof NormalTransactionFields refNormal) {
                    if(index < 0 || index >= refNormal.outputs().length) {
                        return new UtxoExecutionResult(-7, null);
                    }
                    inputSum = inputSum.add(refNormal.outputs()[index].value());
                } else if (inputTdto instanceof CoinbaseTransactionFields refCb) {
                    if(index < 0 || index >= refCb.outputs().length) {
                        return new UtxoExecutionResult(-7, null);
                    }
                    inputSum = inputSum.add(refCb.outputs()[index].value());
                } else {
                    return new UtxoExecutionResult(-7, null);
                }
            }

            BigInteger outputSum = BigInteger.ZERO;
            NormalTransactionOutputs[] outputs = normalTx.outputs();
            for (int outIndex = 0; outIndex < outputs.length; outIndex++) {
                outputSum = outputSum.add(outputs[outIndex].value());
                workingUtxoSet.add(new UTXO(txid, outIndex));
            }

            if(inputSum.compareTo(outputSum) < 0) {
                return new UtxoExecutionResult(-7, null);
            }

            fee = fee.add(inputSum.subtract(outputSum));
        }

        int prevHeight = prevBlockEntry.height();

        // validate Coinbase transaction
        if (cbTxid != null) {
            CoinbaseTransactionFields cb = (CoinbaseTransactionFields) knownObjectsMap.get(cbTxid);
            if (cb == null || cb.outputs() == null || cb.outputs().length != 1) {
                return new UtxoExecutionResult(-6, null);
            }
            if (prevHeight + 1 != cb.height()) {
                return new UtxoExecutionResult(-6, null);
            }

            BigInteger feeValuePlusPicaker = fee.add(BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(12)));
            if (cb.outputs()[0].value().compareTo(feeValuePlusPicaker) > 0) {
                return new UtxoExecutionResult(-6, null);
            }

            workingUtxoSet.add(new UTXO(cbTxid, 0));
        }
        return new UtxoExecutionResult(0, workingUtxoSet);
    }

}