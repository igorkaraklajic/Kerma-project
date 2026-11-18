package Kerma.Dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties()
public record BlockDto(String T, long created, String miner, String nonce,
                       String note, String previd, String[] txids, String type) {
    @JsonCreator
    public BlockDto(
        @JsonProperty(value = "T", required = true) String T,
        @JsonProperty(value = "created", required = true) long created,
        @JsonProperty(value = "miner") String miner,
        @JsonProperty(value = "nonce", required = true) String nonce,
        @JsonProperty(value = "note") String note,
        @JsonProperty(value = "previd") String previd, //this one may be null for the genesis block
        @JsonProperty(value = "txids", required = true) String[] txids,
        @JsonProperty(value = "type", required = true) String type) {

        this.T = T;
        this.created = created;
        this.miner = miner;
        this.nonce = nonce;
        this.note = note;
        this.previd = previd;
        this.txids = txids;
        this.type = type;
    }
}