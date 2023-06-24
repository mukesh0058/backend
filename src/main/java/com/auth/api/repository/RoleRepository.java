package com.auth.api.repository;

import javax.transaction.Transactional;

import com.auth.api.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.auth.api.model.User;

@Repository
@Transactional
public interface RoleRepository extends JpaRepository<Role, String> {

    Role findByName(String name);

}

