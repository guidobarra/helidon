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
                .namedQuery("find-all")
                .map(User::fromRow)
                .toList();
    }

    public Optional<User> findById(long id) {
        return dbClient.execute()
                .namedGet("find-by-id", id)
                .map(User::fromRow);
    }

    public User save(User user) {
        long count = dbClient.execute()
                .namedInsert("insert", user.getName(), user.getEmail());
        if (count == 0) {
            throw new IllegalStateException("Failed to insert user");
        }
        return dbClient.execute()
                .namedGet("find-by-email", user.getEmail())
                .map(User::fromRow)
                .orElseThrow();
    }

    public Optional<User> update(long id, User user) {
        long count = dbClient.execute()
                .namedUpdate("update", user.getName(), user.getEmail(), id);
        if (count == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean delete(long id) {
        long count = dbClient.execute()
                .namedDelete("delete", id);
        return count > 0;
    }
}
