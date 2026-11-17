package Kerma.Dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties()
public class BlockDto {
    public final String T;
    public final long created;
    public final String miner;
    public final String nonce;
    public final String previd;
    public final String[] txids;
    public final String type;

    @JsonCreator
    public BlockDto(
            @JsonProperty(value = "T", required = true) String T,
            @JsonProperty(value = "created", required = true) long created,
            @JsonProperty(value = "miner") String miner,
            @JsonProperty(value = "nonce", required = true) String nonce,
            @JsonProperty(value = "previd") String previd,
            @JsonProperty(value = "txids", required = true) String[] txids,
            @JsonProperty(value = "type", required = true) String type) {

        this.T = T;
        this.created = created;
        this.miner = miner;
        this.nonce = nonce;
        this.previd = previd;
        this.txids = txids;
        this.type = type;
    }
}