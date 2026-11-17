package Kerma.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties()
public class HelloDto {
  public String type;
  public String version;
  public String agent;
}
