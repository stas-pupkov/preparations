package ru.stas.pupkov.preparations.spring.code.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data на старте создаёт JDK-прокси этого интерфейса. */
public interface DemoUserRepository extends JpaRepository<DemoUser, Long> {

    Optional<DemoUser> findByEmailAndActiveTrue(String email);

    @Query("select u from DemoUser u where lower(u.email) = lower(:email)")
    Optional<DemoUser> byEmailIgnoreCase(@Param("email") String email);

    List<DemoUser> findByActiveTrue();
}
