package ru.stas.pupkov.preparations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class
})
@ComponentScan(
    basePackageClasses = PreparationsApplication.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "ru\\.stas\\.pupkov\\.preparations\\.spring\\.code\\..*"
        )
    }
)
public class PreparationsApplication {

  public static void main(String[] args) {
    SpringApplication.run(PreparationsApplication.class, args);
    System.out.println("Launched");
  }

}
