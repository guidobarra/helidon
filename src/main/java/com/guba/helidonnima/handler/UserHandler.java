package com.guba.helidonnima.handler;

import com.guba.helidonnima.model.User;
import com.guba.helidonnima.service.UserService;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.List;

public class UserHandler implements HttpService {

    private final UserService userService;

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::findAll)
                .get("/{id}", this::findById)
                .post("/", this::create)
                .put("/{id}", this::update)
                .delete("/{id}", this::delete);
    }

    private void findAll(ServerRequest request, ServerResponse response) {
        List<User> users = userService.findAll();
        response.send(users);
    }

    private void findById(ServerRequest request, ServerResponse response) {
        long id = Long.parseLong(request.path().pathParameters().get("id"));
        userService.findById(id)
                .ifPresentOrElse(
                        response::send,
                        () -> response.status(Status.NOT_FOUND_404).send()
                );
    }

    private void create(ServerRequest request, ServerResponse response) {
        User user = request.content().as(User.class);
        User saved = userService.save(user);
        response.status(Status.CREATED_201).send(saved);
    }

    private void update(ServerRequest request, ServerResponse response) {
        long id = Long.parseLong(request.path().pathParameters().get("id"));
        User user = request.content().as(User.class);
        userService.update(id, user)
                .ifPresentOrElse(
                        response::send,
                        () -> response.status(Status.NOT_FOUND_404).send()
                );
    }

    private void delete(ServerRequest request, ServerResponse response) {
        long id = Long.parseLong(request.path().pathParameters().get("id"));
        boolean deleted = userService.delete(id);
        if (deleted) {
            response.status(Status.NO_CONTENT_204).send();
        } else {
            response.status(Status.NOT_FOUND_404).send();
        }
    }
}
