package cn.wildfirechat.app.jpa;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource()
public interface ThreadRepository extends CrudRepository<Thread, Integer> {
    @Query(value = "select * from bbs_thread order by last_date desc limit ?1", nativeQuery = true)
    List<Thread> getLatestThreads(int count);
}
