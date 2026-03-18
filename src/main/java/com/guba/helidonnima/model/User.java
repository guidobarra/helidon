package com.guba.helidonnima.model;

import io.helidon.dbclient.DbRow;

import java.time.LocalDateTime;

public class User {

    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public static User fromRow(DbRow row) {
        User user = new User();
        user.setId(row.column("id").asLong().get());
        user.setName(row.column("name").asString().get());
        user.setEmail(row.column("email").asString().get());
        row.column("created_at").as(LocalDateTime.class).ifPresent(user::setCreatedAt);
        row.column("updated_at").as(LocalDateTime.class).ifPresent(user::setUpdatedAt);
        return user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
