package cn.wildfirechat.app.jpa;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bbs_user")
public class User {
    @Id
    public int  uid;
    public String username;
}
