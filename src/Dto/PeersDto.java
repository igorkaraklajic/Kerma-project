package Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties()
public class PeersDto {
  public String type = "peers";
  public List<String> peers;
}
