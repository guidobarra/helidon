package com.guba.helidonnima.repository;

import com.guba.helidonnima.model.User;
import io.helidon.dbclient.DbClient;

import java.util.List;
import java.util.Optional;

public class UserRepository {

    private final DbClient dbClient;

    public UserRepository(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public List<User> findAll() {
        return dbClient.execute()
                .query("SELECT * FROM users ORDER BY id")
                .map(User::fromRow)
                .toList();
    }

    public Optional<User> findById(long id) {
        return dbClient.execute()
                .get("SELECT * FROM users WHERE id = ?", id)
                .map(User::fromRow);
    }

    public User save(User user) {
        long count = dbClient.execute()
                .insert("INSERT INTO users (name, email) VALUES (?, ?)",
                        user.getName(), user.getEmail());
        if (count == 0) {
            throw new IllegalStateException("Failed to insert user");
        }
        return dbClient.execute()
                .get("SELECT * FROM users WHERE email = ?", user.getEmail())
                .map(User::fromRow)
                .orElseThrow();
    }

    public Optional<User> update(long id, User user) {
        long count = dbClient.execute()
                .update("UPDATE users SET name = ?, email = ? WHERE id = ?",
                        user.getName(), user.getEmail(), id);
        if (count == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean delete(long id) {
        long count = dbClient.execute()
                .delete("DELETE FROM users WHERE id = ?", id);
        return count > 0;
    }
}
