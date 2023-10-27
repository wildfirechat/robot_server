package cn.wildfirechat.app.jpa;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bbs_post")
public class Post {
    public int  tid;
    @Id
    public int pid;
    public int uid;
    public int isfirst;
    public long create_date;
    public long userip;
    public int images;
    public int files;
    public int doctype;
    public int quotepid;
    public String message;
    public String message_fmt;
    public String message_cache;
}
