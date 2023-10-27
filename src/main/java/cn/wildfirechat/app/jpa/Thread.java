package cn.wildfirechat.app.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "bbs_thread")
public class Thread {
    public int fid;
    @Id
    public int  tid;
    public int top;
    public int uid;
    public long userip;
    @Column(length = 128)
    public String subject;
    public long create_date;
    public long last_date;
    public int views;
    public int posts;
    public int images;
    public int files;
    public int mods;
    public int closed;
    public int firstpid;
    public int lastuid;
    public int lastpid;
    public int digest;
    @Column(length = 32)
    public String tagids;
    public int tagids_time;
    public int content_buy;
    public int content_buy_type;
}
