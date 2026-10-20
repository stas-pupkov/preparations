package ru.stas.pupkov.preparations.spring.code.springdata;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "DemoUser")
@Table(name = "users")
public class DemoUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private boolean active;

    protected DemoUser() {
    }

    public DemoUser(String email, boolean active) {
        this.email = email;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "DemoUser{id=" + id + ", email='" + email + "', active=" + active + '}';
    }
}
