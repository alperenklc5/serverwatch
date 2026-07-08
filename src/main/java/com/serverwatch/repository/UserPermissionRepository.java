package com.serverwatch.repository;

import com.serverwatch.model.entity.Permission;
import com.serverwatch.model.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUserId(Long userId);
    boolean existsByUserIdAndPermission(Long userId, Permission permission);

    @Modifying
    @Query("DELETE FROM UserPermission p WHERE p.userId = :userId AND p.permission = :permission")
    void deleteByUserIdAndPermission(@Param("userId") Long userId, @Param("permission") Permission permission);

    @Query("SELECT p.permission FROM UserPermission p WHERE p.userId = :userId")
    Set<Permission> findPermissionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM UserPermission p WHERE p.permission = :permission")
    long countByPermission(@Param("permission") Permission permission);
}
