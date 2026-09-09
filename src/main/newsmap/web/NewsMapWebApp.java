package main.newsmap.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "main.newsmap.web")
public class NewsMapWebApp {

    public static void main(String[] args) {
        SpringApplication.run(NewsMapWebApp.class, args);
    }
}
