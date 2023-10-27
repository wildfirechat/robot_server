package cn.wildfirechat.app.jpa;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource()
public interface UserRepository extends CrudRepository<User, Integer> {
}
