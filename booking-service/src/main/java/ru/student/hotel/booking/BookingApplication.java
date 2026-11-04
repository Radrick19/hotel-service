package ru.student.hotel.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ru.student.hotel")
public class BookingApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookingApplication.class, args);
  }
}
