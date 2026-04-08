package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.model.User;

public interface UserService {
    User findByUsername(String username);
    User findByEmail(String email);
    User findById(Long id);
    User save(User user);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}