package com.mbio.custom.processors.model;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement(name = "history")
@XmlAccessorType(XmlAccessType.FIELD)
public class BitcoinHistoryModel implements Serializable {

  private static final long serialVersionUID = 2753462393384639415L;

  @XmlElement(name = "timestamp")
  @XmlJavaTypeAdapter(ZonedDateTimeAdapter.class)
  @JsonProperty("timestamp")
  private ZonedDateTime timestamp;

  @XmlElement(name = "open")
  @JsonProperty("open")
  private double open;

  @XmlElement(name = "high")
  @JsonProperty("high")
  private double high;

  @XmlElement(name = "low")
  @JsonProperty("low")
  private double low;

  @XmlElement(name = "close")
  @JsonProperty("close")
  private double close;

  @XmlElement(name = "btc-volume")
  @JsonProperty("btcVolume")
  private double btcVolume;

  @XmlElement(name = "usd-volume")
  @JsonProperty("usdVolume")
  private double usdVolume;

  @XmlElement(name = "weighted-price")
  @JsonProperty("weightedPrice")
  private double weightedPrice;

  public ZonedDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(ZonedDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public double getOpen() {
    return open;
  }

  public void setOpen(double open) {
    this.open = open;
  }

  public double getHigh() {
    return high;
  }

  public void setHigh(double high) {
    this.high = high;
  }

  public double getLow() {
    return low;
  }

  public void setLow(double low) {
    this.low = low;
  }

  public double getClose() {
    return close;
  }

  public void setClose(double close) {
    this.close = close;
  }

  public double getBtcVolume() {
    return btcVolume;
  }

  public void setBtcVolume(double btcVolume) {
    this.btcVolume = btcVolume;
  }

  public double getUsdVolume() {
    return usdVolume;
  }

  public void setUsdVolume(double usdVolume) {
    this.usdVolume = usdVolume;
  }

  public double getWeightedPrice() {
    return weightedPrice;
  }

  public void setWeightedPrice(double weightedPrice) {
    this.weightedPrice = weightedPrice;
  }

  @Override
  public int hashCode() {
    return Objects.hash(btcVolume, close, high, low, open, timestamp, usdVolume, weightedPrice);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
      BitcoinHistoryModel other = (BitcoinHistoryModel) obj;
    return Double.doubleToLongBits(btcVolume) == Double.doubleToLongBits(other.btcVolume)
        && Double.doubleToLongBits(close) == Double.doubleToLongBits(other.close)
        && Double.doubleToLongBits(high) == Double.doubleToLongBits(other.high)
        && Double.doubleToLongBits(low) == Double.doubleToLongBits(other.low)
        && Double.doubleToLongBits(open) == Double.doubleToLongBits(other.open)
        && Objects.equals(timestamp, other.timestamp)
        && Double.doubleToLongBits(usdVolume) == Double.doubleToLongBits(other.usdVolume)
        && Double.doubleToLongBits(weightedPrice) == Double.doubleToLongBits(other.weightedPrice);
  }

  @Override
  public String toString() {
    return "BitcoinHistoryModel [timestamp=" + timestamp + ", open=" + open + ", high=" + high + ", low="
        + low + ", close=" + close + ", btcVolume=" + btcVolume + ", usdVolume=" + usdVolume
        + ", weightedPrice=" + weightedPrice + "]";
  }



}
