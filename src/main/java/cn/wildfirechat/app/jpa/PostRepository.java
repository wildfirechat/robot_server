package cn.wildfirechat.app.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource()
public interface PostRepository extends CrudRepository<Post, Integer> {
    @Query(value = "select * from bbs_post order by pid desc limit ?1", nativeQuery = true)
    List<Post> getLatestPosts(int count);
}
