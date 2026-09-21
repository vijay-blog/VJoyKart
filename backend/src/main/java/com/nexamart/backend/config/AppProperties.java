package com.nexamart.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexamart")
public class AppProperties {
  private String jwtSecret;
  private long accessTokenMinutes = 60;
  private long refreshTokenDays = 30;
  private String adminUsername = "admin";
  private String adminPassword = "admin@223";
  private String adminEmail = "admin@nexamart.local";
  private String adminName = "NexaMart Admin";
  private String otpApiKey = "";
  private String otpTemplateName = "";
  private int otpTtlSeconds = 300;
  private boolean otpDevMode = false;
  public String getJwtSecret(){return jwtSecret;} public void setJwtSecret(String v){jwtSecret=v;}
  public long getAccessTokenMinutes(){return accessTokenMinutes;} public void setAccessTokenMinutes(long v){accessTokenMinutes=v;}
  public long getRefreshTokenDays(){return refreshTokenDays;} public void setRefreshTokenDays(long v){refreshTokenDays=v;}
  public String getAdminUsername(){return adminUsername;} public void setAdminUsername(String v){adminUsername=v;}
  public String getAdminPassword(){return adminPassword;} public void setAdminPassword(String v){adminPassword=v;}
  public String getAdminEmail(){return adminEmail;} public void setAdminEmail(String v){adminEmail=v;}
  public String getAdminName(){return adminName;} public void setAdminName(String v){adminName=v;}
  public String getOtpApiKey(){return otpApiKey;} public void setOtpApiKey(String v){otpApiKey=v;}
  public String getOtpTemplateName(){return otpTemplateName;} public void setOtpTemplateName(String v){otpTemplateName=v;}
  public int getOtpTtlSeconds(){return otpTtlSeconds;} public void setOtpTtlSeconds(int v){otpTtlSeconds=v;}
  public boolean isOtpDevMode(){return otpDevMode;} public void setOtpDevMode(boolean v){otpDevMode=v;}
}
