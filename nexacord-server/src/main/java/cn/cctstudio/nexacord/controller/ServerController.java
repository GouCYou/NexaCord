package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.service.ServerService;
import cn.cctstudio.nexacord.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {
    private final ServerService serverService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<Server> createServer(@Valid @RequestBody Server server, @AuthenticationPrincipal User currentUser) {
        Server createdServer = serverService.createServer(server, currentUser);
        return new ResponseEntity<>(createdServer, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Server> getServerById(@PathVariable Long id) {
        Server server = serverService.getServerById(id);
        return new ResponseEntity<>(server, HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Server>> searchServersByName(@RequestParam String name) {
        List<Server> servers = serverService.searchServersByName(name);
        return new ResponseEntity<>(servers, HttpStatus.OK);
    }

    @GetMapping("/user")
    public ResponseEntity<List<Server>> getServersByUser(@AuthenticationPrincipal User currentUser) {
        List<Server> servers = serverService.getServersByUser(currentUser);
        return new ResponseEntity<>(servers, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Server> updateServer(@PathVariable Long id, @Valid @RequestBody Server server, @AuthenticationPrincipal User currentUser) {
        // 检查用户是否是服务器所有者
        if (!serverService.isServerOwner(id, currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        server.setId(id);
        Server updatedServer = serverService.updateServer(server);
        return new ResponseEntity<>(updatedServer, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        // 检查用户是否是服务器所有者
        if (!serverService.isServerOwner(id, currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        serverService.deleteServer(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}