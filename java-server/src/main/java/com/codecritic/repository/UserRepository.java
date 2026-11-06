package com.codecritic.repository;

import com.codecritic.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User} documents.
 *
 * <p>In addition to the standard CRUD operations inherited from
 * {@link MongoRepository}, exposes lookups and deletions keyed by the unique
 * username.</p>
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Finds the user with the given username.
     *
     * @param username username to look up
     * @return the matching user, or an empty optional when none exists
     */
    Optional<User> findByUsername(String username);

    /**
     * Deletes the user with the given username, if present.
     *
     * @param username username whose document should be removed
     */
    void deleteByUsername(String username);
}