package ru.stas.pupkov.preparations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PreparationsApplication {

  public static void main(String[] args) {
    SpringApplication.run(PreparationsApplication.class, args);
    System.out.println("Launched");
  }

}
