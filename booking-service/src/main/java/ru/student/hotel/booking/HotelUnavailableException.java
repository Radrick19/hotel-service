package ru.student.hotel.booking;

public class HotelUnavailableException extends RuntimeException {
  public HotelUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
