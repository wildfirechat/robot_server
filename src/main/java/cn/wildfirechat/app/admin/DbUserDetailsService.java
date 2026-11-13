package cn.wildfirechat.app.admin;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从数据库加载管理员账号。
 */
@Service
public class DbUserDetailsService implements UserDetailsService {
    private final AdminUserRepository repository;

    public DbUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("ADMIN")
                .build();
    }
}
