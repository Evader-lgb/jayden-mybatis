package com.jayden.mapper;

import com.jayden.entity.User;

/**
 * @author linguobin
 */
public interface UserMapper {
    User selectById(Long id);
}
