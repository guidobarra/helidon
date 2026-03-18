package com.guba.helidonnima.service;

import com.guba.helidonnima.model.User;
import com.guba.helidonnima.repository.UserRepository;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public Optional<User> update(long id, User user) {
        return userRepository.update(id, user);
    }

    public boolean delete(long id) {
        return userRepository.delete(id);
    }
}
